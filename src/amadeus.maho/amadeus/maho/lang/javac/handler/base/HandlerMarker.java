package amadeus.maho.lang.javac.handler.base;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.ClassReader;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.ArgumentAttr;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.DeferredAttr;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.Flow;
import com.sun.tools.javac.comp.Lower;
import com.sun.tools.javac.comp.Modules;
import com.sun.tools.javac.comp.Operators;
import com.sun.tools.javac.comp.TypeEnter;
import com.sun.tools.javac.jvm.Gen;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.Log;

import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.Special;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.javac.JavacContext;
import amadeus.maho.lang.javac.handler.AccessibleHandler;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.InvisibleType;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.bytecode.Bytecodes;
import amadeus.maho.util.runtime.StreamHelper;
import amadeus.maho.util.tuple.Tuple2;

import static com.sun.tools.javac.code.Flags.PARAMETER;

@TransformProvider
@NoArgsConstructor
public class HandlerMarker extends JavacContext {
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)))
    private static void initModules(final Modules $this, final com.sun.tools.javac.util.List<JCTree.JCCompilationUnit> trees) {
        final HandlerMarker marker = instance(HandlerMarker.class);
        final List<DynamicAnnotationHandler> handlers = Stream.concat(marker.baseHandlers().stream(), marker.syntaxHandlers().stream()).cast(DynamicAnnotationHandler.class).toList();
        final Set<Symbol.ModuleSymbol> allModules = $this.allModules();
        handlers.forEach(handler -> handler.initModules(allModules));
        final Class providerTypes[] = handlers.stream().map(DynamicAnnotationHandler::providerType).toArray(Class[]::new);
        final Class annotationTypes[] = handlers.stream().map(DynamicAnnotationHandler::annotationType).toArray(Class[]::new);
        final Map<Class<? extends Annotation>, DynamicAnnotationHandler> map = handlers.stream().collect(Collectors.toMap(DynamicAnnotationHandler::annotationType, Function.identity()));
        allModules.stream()
                .filter(module -> {
                    if (module.module_info != null && module.module_info.classfile != null)
                        try (final var input = module.module_info.classfile.openInputStream()) {
                            return ClassAnnotationFinder.hasAnnotations(new ClassReader(input), providerTypes);
                        } catch (final Exception ignored) { }
                    return false;
                })
                .flatMap(module -> module.enclosedPackages.stream())
                .map(pkg -> pkg.members().getSymbols(Symbol.ClassSymbol.class::isInstance))
                .flatMap(StreamHelper::fromIterable)
                .cast(Symbol.ClassSymbol.class)
                .forEach(symbol -> {
                    if (symbol.classfile != null)
                        try (final var input = symbol.classfile.openInputStream()) {
                            ClassAnnotationFinder.processVoid(new ClassReader(input), clazz -> map[clazz].addSymbol(symbol), annotationTypes);
                        } catch (final Exception ignored) { }
                });
    }
    
    @Getter
    private final List<BaseHandler<Annotation>> baseHandlers = Handler.Marker.handlerTypes().stream().map(Supplier::get)
            .map(JavacContext::instance)
            .collect(Collectors.toCollection(ArrayList::new));
    
    @Getter
    private final List<BaseSyntaxHandler> syntaxHandlers = Syntax.SyntaxMarker.syntaxTypes().values().stream().map(Supplier::get)
            .map(JavacContext::instance)
            .collect(Collectors.toCollection(ArrayList::new));
    
    public static final ThreadLocal<LinkedList<JCTree>> attrContextLocal = ThreadLocal.withInitial(LinkedList::new);
    
    public static LinkedList<JCTree> attrContext() = attrContextLocal.get();
    
    @Hook
    private static void attribTree_$Enter(
            final Attr $this,
            final JCTree tree,
            final Env<AttrContext> env,
            final Attr.ResultInfo resultInfo) = attrContext().addLast(tree);
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN), ordinal = 0))
    private static void attribTree_$Return(
            final Attr $this,
            final JCTree tree,
            final Env<AttrContext> env,
            final Attr.ResultInfo resultInfo) {
        final HandlerMarker instance = instance(HandlerMarker.class);
        instance.syntaxHandlers().forEach(handler -> handler.attribTree(tree, env));
        if (env.enclMethod != null && tree instanceof JCTree.JCVariableDecl decl && noneMatch(decl.mods.flags, PARAMETER)) // local var
            instance.process(env, tree, env.enclMethod, false);
    }
    
    @Hook(at = @At(type = @At.TypeInsn(opcode = Bytecodes.NEW, type = AssertionError.class))) // throw new AssertionError("isSubtype " + t.getTag());
    private static Hook.Result visitType(final Types.@InvisibleType("com.sun.tools.javac.code.Types$4") TypeRelation $this, final Type t, final Type s) = Hook.Result.TRUE;
    
    @SneakyThrows
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.EXCEPTION)), capture = true, forceReturn = true)
    private static Type attribTree_$Catch(
            final Throwable throwable,
            final Attr $this,
            final JCTree tree,
            final Env<AttrContext> env,
            final Attr.ResultInfo resultInfo) {
        if (throwable instanceof ReAttrException exception && tree == exception.breakTree)
            try {
                TreeTranslator.translate(Map.of(exception.breakTree, exception.tree), true, TreeTranslator.upper(env, tree), env.tree);
                if (env.tree == exception.breakTree)
                    env.tree = exception.tree;
                if (exception.needAttr) {
                    exception.tree.accept(new TreeScanner() {
                        
                        @Override
                        public void scan(final @Nullable JCTree tree) {
                            exception.consumer.accept(tree);
                            if (tree instanceof JCTree.JCLambda lambda && lambda.paramKind == JCTree.JCLambda.ParameterKind.IMPLICIT)
                                lambda.params.forEach(parameter -> parameter.vartype = null);
                            else if (tree instanceof JCTree.JCVariableDecl decl && decl.sym != null && decl.sym.owner instanceof Symbol.MethodSymbol)
                                decl.sym = null;
                            super.scan(tree);
                        }
                        
                    });
                    return (Privilege) $this.attribTree(exception.tree, env, resultInfo);
                }
                return exception.tree.type;
            } finally { exception.runnable.run(); }
        else
            throw throwable;
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.FINALLY)))
    private static void attribTree_$Exit(
            final Attr $this,
            final JCTree tree,
            final Env<AttrContext> env,
            final Attr.ResultInfo resultInfo) = attrContext().removeLast();
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)))
    private static void setSyntheticVariableType(final Attr $this, final JCTree.JCVariableDecl tree, final Type type) {
        if (!type.isErroneous() && noneMatch(tree.mods.flags, PARAMETER))
            $this.attribType(tree.vartype, (Privilege) $this.env);
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static JCTree attribSpeculative(final JCTree capture, final DeferredAttr $this, final JCTree tree, final Env<AttrContext> env, final Attr.ResultInfo resultInfo, final Supplier<Log.DiagnosticHandler> handlerSupplier,
            final DeferredAttr.AttributionMode attributionMode, final ArgumentAttr.LocalCacheContext localCache, final @Hook.LocalVar(index = 7) Env<AttrContext> localEnv) = localEnv.tree;
    
    @Hook(at = @At(field = @At.FieldInsn(name = "flags_field")))
    private static void finishClass_$Pre(final TypeEnter.MembersPhase $this, final JCTree.JCClassDecl tree, final JCTree defaultConstructor, final Env<AttrContext> env) = instance(HandlerMarker.class).preprocessing(tree, env);
    
    public void preprocessing(final JCTree.JCClassDecl tree, final Env<AttrContext> env) {
        baseHandlers().forEach(handler -> handler.preprocessing(env));
        syntaxHandlers().forEach(handler -> handler.preprocessing(env));
        processClassDecl(tree, env, true);
    }
    
    @Hook(at = @At(method = @At.MethodInsn(name = "memberEnter")), before = false)
    private static void finishClass_$Post(final TypeEnter.MembersPhase $this, final JCTree.JCClassDecl tree, final JCTree defaultConstructor, final Env<AttrContext> env) = instance(HandlerMarker.class).postprocessing($this, tree, env);
    
    
    private void postprocessing(final TypeEnter.MembersPhase $this, final JCTree.JCClassDecl tree, final Env<AttrContext> env) {
        final com.sun.tools.javac.util.List<Env<AttrContext>> todo = (Privilege) $this.todo;
        final Symbol.ClassSymbol superSymbol = (Symbol.ClassSymbol) env.enclClass.sym.getSuperclass().tsym;
        final Env<AttrContext> superEnv = typeEnvs()[superSymbol];
        if (todo.contains(superEnv)) {
            (Privilege) ($this.todo = todo.stream().filter(it -> it != superEnv).collect(com.sun.tools.javac.util.List.collector()));
            (@Special Privilege) ((TypeEnter.Phase) $this).doCompleteEnvs(com.sun.tools.javac.util.List.of(superEnv));
        }
        processClassDecl(tree, env, false);
    }
    
    public void processClassDecl(final JCTree.JCClassDecl tree, final Env<AttrContext> env, final boolean advance) {
        tree.defs.stream()
                .filter(def -> !(def instanceof JCTree.JCClassDecl))
                .filter(def -> modifiers(def) != null)
                .forEach(def -> process(env, def, tree, advance));
        process(env, tree, env.outer.tree, advance);
    }
    
    public void process(final Env<AttrContext> env, final JCTree tree, final JCTree owner, final boolean advance) {
        if (tree instanceof JCTree.JCMethodDecl methodDecl)
            methodDecl.params.forEach(param -> process(env, param, tree, advance));
        final @Nullable JCTree.JCModifiers modifiers = modifiers(tree);
        if (modifiers != null)
            baseHandlers().stream()
                    .filter(baseHandler -> baseHandler.shouldProcess(advance))
                    .map(baseHandler -> getAnnotationsByTypeWithOuter(modifiers, env, tree, baseHandler))
                    .nonnull()
                    .collect(Collectors.toCollection(ArrayList::new))
                    .let(result -> result.sort((a, b) -> (int) (a.getKey().handler().priority() - b.getKey().handler().priority())))
                    .forEach(entry -> entry.getValue().forEach(tuple -> process(env, tree, owner, entry.getKey(), tuple.v1, tuple.v2, advance)));
        syntaxHandlers().forEach(handler -> handler.process(env, tree, owner, advance));
        AccessibleHandler.transformPackageLocalToProtected(tree, owner, modifiers);
    }
    
    public @Nullable <A extends Annotation> Map.Entry<BaseHandler<A>, List<Tuple2<A, JCTree.JCAnnotation>>> getAnnotationsByTypeWithOuter(final @Nullable JCTree.JCModifiers modifiers, final Env<AttrContext> env, final JCTree tree,
            final BaseHandler<A> baseHandler) {
        final Handler handler = baseHandler.handler();
        final Class<A> annotationType = (Class<A>) handler.value();
        return Optional.of(getAnnotationsByType(modifiers, env, annotationType))
                .filter(annotations -> !annotations.isEmpty())
                .or(() -> env.enclClass != tree && env.enclClass.mods != null && Stream.of(handler.ranges()).anyMatch(range -> checkRange(range, tree)) && baseHandler.derivedFilter(env, tree) ?
                        Optional.ofNullable(getAnnotationsByType(env.enclClass.mods, env, annotationType)).filter(annotations -> !annotations.isEmpty()) : Optional.empty())
                .map(annotations -> Map.entry(baseHandler, annotations)).orElse(null);
    }
    
    public <A extends Annotation> List<Tuple2<A, JCTree.JCAnnotation>> getAnnotationsByTypeWithOuter(final JCTree.JCModifiers modifiers, final Env<AttrContext> env, final JCTree tree, final Class<A> annotationType)
            = baseHandlers().stream()
            .filter(baseHandler -> baseHandler.handler().value() == annotationType)
            .findFirst()
            .map(baseHandler -> getAnnotationsByTypeWithOuter(modifiers, env, tree, (BaseHandler<A>) baseHandler))
            .map(Map.Entry::getValue)
            .orElseGet(() -> getAnnotationsByType(modifiers, env, annotationType));
    
    public <A extends Annotation> @Nullable A lookupAnnotation(final JCTree.JCModifiers modifiers, final Env<AttrContext> env, final JCTree tree, final Class<A> annotationType)
            = getAnnotationsByTypeWithOuter(modifiers, env, tree, annotationType).stream().findFirst().map(Tuple2::v1).orElse(null);
    
    private void process(final Env<AttrContext> env, final JCTree tree, final JCTree owner, final BaseHandler<Annotation> baseHandler, final Annotation annotation, final JCTree.JCAnnotation annotationTree, final boolean advance) {
        final JCTree.JCCompilationUnit topLevel = maker.toplevel;
        final int pos = maker.pos;
        try {
            maker.toplevel = env.toplevel;
            maker.pos = annotationTree.pos;
            baseHandler.process(env, tree, owner, annotation, annotationTree, advance);
        } finally {
            maker.toplevel = topLevel;
            maker.pos = pos;
        }
    }
    
    private static boolean checkRange(final Handler.Range range, final JCTree tree) {
        if (tree instanceof JCTree.JCVariableDecl)
            return range == Handler.Range.FIELD;
        if (tree instanceof JCTree.JCMethodDecl)
            return range == Handler.Range.METHOD;
        if (tree instanceof JCTree.JCClassDecl)
            return range == Handler.Range.CLASS;
        return false;
    }
    
    public void injectMember(final Env<AttrContext> env, final JCTree tree, final boolean advance = false) {
        final JCTree.JCClassDecl decl = env.enclClass;
        decl.defs = decl.defs.append(tree);
        if (!advance) {
            final Env<AttrContext> dup = env.dup(env.enclClass);
            process(env, tree, env.enclClass, true);
            classEnter(enter, tree, dup);
            memberEnter(memberEnter, tree, dup);
            process(env, tree, env.enclClass, false);
        }
    }
    
    @Hook
    private static Hook.Result resolveUnary(final Operators $this, final JCDiagnostic.DiagnosticPosition pos, final JCTree.Tag tag, final Type op)
            = Hook.Result.nullToVoid(instance(HandlerMarker.class).syntaxHandlers().stream()
            .map(handler -> handler.resolveUnary(pos, tag, op))
            .nonnull()
            .findAny()
            .orElse(null));
    
    @Hook
    private static Hook.Result resolveBinary(final Operators $this, final JCDiagnostic.DiagnosticPosition pos, final JCTree.Tag tag, final Type op1, final Type op2)
            = Hook.Result.nullToVoid(instance(HandlerMarker.class).syntaxHandlers().stream()
            .map(handler -> handler.resolveBinary(pos, tag, op1, op2))
            .nonnull()
            .findAny()
            .orElse(null));
    
    public static final ThreadLocal<LinkedList<JCTree>> flowContextLocal = ThreadLocal.withInitial(LinkedList::new);
    
    public static LinkedList<JCTree> flowContext() = flowContextLocal.get();
    
    @Hook
    private static void visitClassDef_$Enter(final Flow.FlowAnalyzer $this, final JCTree.JCClassDecl decl) = flowContext().addLast(decl);
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.FINALLY)))
    private static void visitClassDef_$Exit(final Flow.FlowAnalyzer $this, final JCTree.JCClassDecl decl) = flowContext().removeLast();
    
    @Hook
    private static void visitVarDef_$Enter(final Flow.FlowAnalyzer $this, final JCTree.JCVariableDecl decl) = flowContext().addLast(decl);
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.FINALLY)))
    private static void visitVarDef_$Exit(final Flow.FlowAnalyzer $this, final JCTree.JCVariableDecl decl) = flowContext().removeLast();
    
    @Hook
    private static void visitMethodDef_$Enter(final Flow.FlowAnalyzer $this, final JCTree.JCMethodDecl decl) = flowContext().addLast(decl);
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.FINALLY)))
    private static void visitMethodDef_$Exit(final Flow.FlowAnalyzer $this, final JCTree.JCMethodDecl decl) = flowContext().removeLast();
    
    public static final ThreadLocal<LinkedList<JCTree>> lowerContextLocal = ThreadLocal.withInitial(LinkedList::new);
    
    public static LinkedList<JCTree> lowerContext() = lowerContextLocal.get();
    
    @Hook
    private static void translate_$Enter(final Lower $this, final JCTree tree) = lowerContext().addLast(tree);
    
    @SneakyThrows
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.EXCEPTION)), capture = true, forceReturn = true)
    private static JCTree translate_$Catch(final Throwable throwable, final Lower $this, final JCTree tree) {
        if (throwable instanceof ReLowException e && lowerContext().getLast() == e.breakTree)
            return e.tree;
        else
            throw throwable;
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.FINALLY)))
    private static void translate_$Exit(final Lower $this, final JCTree tree) = lowerContext().removeLast();
    
    public static final ThreadLocal<LinkedList<JCTree>> genContextLocal = ThreadLocal.withInitial(LinkedList::new);
    
    public static LinkedList<JCTree> genContext() = genContextLocal.get();
    
    @Hook
    private static void genDef_$Enter(final Gen $this, final JCTree tree, final Env<?> env) = genContext().addLast(tree);
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.FINALLY)))
    private static void genDef_$Exit(final Gen $this, final JCTree tree, final Env<?> env) = genContext().removeLast();
    
    @Hook
    private static void genExpr_$Enter(final Gen $this, final JCTree tree, final Type pt) = genContext().addLast(tree);
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.FINALLY)))
    private static void genExpr_$Exit(final Gen $this, final JCTree tree, final Type pt) = genContext().removeLast();
    
}
