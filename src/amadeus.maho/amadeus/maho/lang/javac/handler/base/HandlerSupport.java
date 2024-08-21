package amadeus.maho.lang.javac.handler.base;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

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
import com.sun.tools.javac.comp.TypeEnter;
import com.sun.tools.javac.jvm.Gen;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeCopier;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Name;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.Special;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.javac.JavacContext;
import amadeus.maho.lang.javac.handler.AccessibleHandler;
import amadeus.maho.lang.javac.multithreaded.MultiThreadedContext;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.InvisibleType;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.Bytecodes;
import amadeus.maho.util.control.OverrideMap;
import amadeus.maho.util.tuple.Tuple2;

import static com.sun.tools.javac.code.Flags.*;

@TransformProvider
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class HandlerSupport extends JavacContext {
    
    private static final OverrideMap overrideMap = { };
    
    @Getter
    List<BaseHandler<Annotation>> baseHandlers = Handler.Marker.handlerTypes().stream()
            .map(Supplier::get)
            .map(JavacContext::instance)
            .collect(Collectors.toCollection(ArrayList::new));
    
    @Getter
    List<BaseSyntaxHandler> syntaxHandlers = Syntax.SyntaxMarker.syntaxTypes().values().stream()
            .map(Supplier::get)
            .map(JavacContext::instance)
            .collect(Collectors.toCollection(ArrayList::new));
    
    List<DynamicAnnotationHandler> handlers = Stream.concat(baseHandlers().stream(), syntaxHandlers().stream()).cast(DynamicAnnotationHandler.class).toList();
    
    @Hook
    private static void enterDone(final JavaCompiler $this) = instance(HandlerSupport.class).beforeEnterDone();
    
    public void beforeEnterDone() = modules.allModules().forEach(this::loadDynamicAnnotationProvider);
    
    public void loadDynamicAnnotationProvider(final Symbol.ModuleSymbol moduleSymbol) {
        if (moduleSymbol.module_info != null && moduleSymbol.module_info.classfile != null)
            try (final var input = moduleSymbol.module_info.classfile.openInputStream()) {
                final ClassNode node = ASMHelper.newClassNode(new ClassReader(input));
                handlers.forEach(handler -> {
                    final @Nullable AnnotationNode annotationNode = ASMHelper.findAnnotationNode(node.visibleAnnotations, handler.providerType());
                    if (annotationNode != null) {
                        final int index = annotationNode.values.indexOf("value");
                        if (index != -1 && annotationNode.values[index + 1] instanceof List<?> array)
                            array.stream()
                                    .cast(String.class)
                                    .map(name -> symtab.enterClass(moduleSymbol, names.fromString(name)))
                                    .forEach(handler::addSymbol);
                    }
                });
            } catch (final Exception ignored) { }
    }
    
    public void beforeAttrModuleInfo(final JCTree.JCModuleDecl moduleDecl) {
        final Symbol.ModuleSymbol module = moduleDecl.sym;
        handlers.forEach(handler -> {
            final Collection<Symbol.ClassSymbol> symbols = handler.allSymbols(module);
            if (!symbols.isEmpty()) {
                final JCTree.JCAnnotation annotation = maker.at(moduleDecl.mods.pos).Annotation(IdentQualifiedName(handler.providerType()),
                        com.sun.tools.javac.util.List.of(maker.NewArray(null, null, symbols.stream()
                                .map(Symbol.ClassSymbol::flatName)
                                .map(Name::toString)
                                .map(maker::Literal)
                                .collect(com.sun.tools.javac.util.List.collector()))));
                moduleDecl.mods.annotations = Stream.concat(moduleDecl.mods.annotations.stream()
                                .filter(it -> !it.type.tsym.flatName().toString().equals(handler.providerType().getName())), Stream.of(annotation))
                        .collect(com.sun.tools.javac.util.List.collector());
                (Privilege) (moduleDecl.sym.getMetadata().attributes = ((Privilege) moduleDecl.sym.getMetadata().attributes).stream()
                        .filter(it -> !it.type.tsym.flatName().toString().equals(handler.providerType().getName()))
                        .collect(com.sun.tools.javac.util.List.collector()));
                moduleDecl.sym.appendAttributes(com.sun.tools.javac.util.List.of(annotate.attributeAnnotation(annotation, symtab.annotationType, (Privilege) attr.env)));
            }
        });
    }
    
    @Hook
    private static void visitModuleDef(final Attr $this, final JCTree.JCModuleDecl tree) = instance(HandlerSupport.class).beforeAttrModuleInfo(tree);
    
    LinkedList<JCTree> attrContext = { };
    
    public static LinkedList<JCTree> attrContext() = instance(HandlerSupport.class).attrContext;
    
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
        final HandlerSupport instance = instance(HandlerSupport.class);
        instance.syntaxHandlers().forEach(handler -> handler.attribTree(tree, env));
        if (env.enclMethod != null && tree instanceof JCTree.JCVariableDecl decl && noneMatch(decl.mods.flags, PARAMETER)) // local var
            instance.process(env, tree, env.enclMethod, false);
    }
    
    @SuppressWarnings("Hook")
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
    
    @Hook
    private static void visitMethodDef(final Attr $this, final JCTree.JCMethodDecl tree) {
        if (tree.body == null && noneMatch(tree.mods.flags, NATIVE | ABSTRACT))
            instance(HandlerSupport.class).generateMethodBody((Privilege) $this.env, tree);
    }
    
    private void generateMethodBody(final Env<AttrContext> env, final JCTree.JCMethodDecl tree) = overrideMap[baseHandlers()][BaseHandler.Methods.generateMethodBody].stream()
            .map(baseHandler -> getAnnotationsByTypeWithOuter(tree.mods, env, tree, baseHandler))
            .nonnull()
            .collect(Collectors.toCollection(ArrayList::new))
            .let(result -> result.sort((a, b) -> (int) (a.getKey().handler().priority() - b.getKey().handler().priority())))
            .stream()
            .takeWhile(_ -> tree.body == null)
            .forEach(entry -> entry.getValue().forEach(tuple -> generateMethodBody(entry.getKey(), env, tree, tuple.v1, tuple.v2)));
    
    private void generateMethodBody(final BaseHandler<Annotation> baseHandler, final Env<AttrContext> env, final JCTree.JCMethodDecl tree, final Annotation annotation, final JCTree.JCAnnotation annotationTree) {
        final JCTree.JCCompilationUnit topLevel = maker.toplevel;
        final int pos = maker.pos;
        try {
            maker.toplevel = env.toplevel;
            maker.pos = annotationTree.pos;
            baseHandler.generateMethodBody(env, tree, annotation, annotationTree);
        } finally {
            maker.toplevel = topLevel;
            maker.pos = pos;
        }
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)))
    private static void setSyntheticVariableType(final Attr $this, final JCTree.JCVariableDecl tree, final Type type) {
        if (!type.isErroneous() && noneMatch(tree.mods.flags, PARAMETER))
            $this.attribType(tree.vartype, (Privilege) $this.env);
    }
    
    IdentityHashMap<JCTree, JCTree> speculativeContext = { };
    
    public static IdentityHashMap<JCTree, JCTree> speculativeContext() = instance(HandlerSupport.class).speculativeContext;
    
    @Hook(at = @At(method = @At.MethodInsn(name = "copy")), before = false, capture = true)
    private static <Z> void attribSpeculative_$Copy(final JCTree capture, final DeferredAttr $this, final JCTree tree, final Env<AttrContext> env, final Attr.ResultInfo resultInfo, final TreeCopier<Z> deferredCopier,
            final Supplier<Log.DiagnosticHandler> handlerSupplier, final DeferredAttr.AttributionMode attributionMode, final ArgumentAttr.LocalCacheContext localCache) = speculativeContext()[tree] = capture;
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.FINALLY)))
    private static <Z> void attribSpeculative_$Finally(final DeferredAttr $this, final JCTree tree, final Env<AttrContext> env, final Attr.ResultInfo resultInfo, final TreeCopier<Z> deferredCopier,
            final Supplier<Log.DiagnosticHandler> handlerSupplier, final DeferredAttr.AttributionMode attributionMode, final ArgumentAttr.LocalCacheContext localCache) = speculativeContext() -= tree;
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static JCTree attribSpeculative(final JCTree capture, final DeferredAttr $this, final JCTree tree, final Env<AttrContext> env, final Attr.ResultInfo resultInfo, final Supplier<Log.DiagnosticHandler> handlerSupplier,
            final DeferredAttr.AttributionMode attributionMode, final ArgumentAttr.LocalCacheContext localCache, final @Hook.LocalVar(index = 7) Env<AttrContext> localEnv) = localEnv.tree;
    
    @Hook(at = @At(field = @At.FieldInsn(name = "flags_field")))
    private static void finishClass_$Pre(final TypeEnter.MembersPhase $this, final JCTree.JCClassDecl tree, final JCTree defaultConstructor, final Env<AttrContext> env) = instance(HandlerSupport.class).preprocessing(tree, env);
    
    public void preprocessing(final JCTree.JCClassDecl tree, final Env<AttrContext> env) {
        baseHandlers().forEach(handler -> handler.preprocessing(env));
        processClassDecl(tree, env, true);
    }
    
    @Hook(at = @At(method = @At.MethodInsn(name = "memberEnter")), before = false)
    private static void finishClass_$Post(final TypeEnter.MembersPhase $this, final JCTree.JCClassDecl tree, final JCTree defaultConstructor, final Env<AttrContext> env) = instance(HandlerSupport.class).postprocessing($this, tree, env);
    
    private void postprocessing(final TypeEnter.MembersPhase $this, final JCTree.JCClassDecl tree, final Env<AttrContext> env) {
        if (!(context instanceof MultiThreadedContext)) {
            final com.sun.tools.javac.util.List<Env<AttrContext>> todo = (Privilege) $this.todo;
            final @Nullable Symbol.ClassSymbol superSymbol = (Symbol.ClassSymbol) types.supertype(env.enclClass.sym.type).tsym;
            if (superSymbol != null) {
                final @Nullable Env<AttrContext> superEnv = (Privilege) typeEnvs.get(superSymbol);
                if (superEnv != null && todo.contains(superEnv)) {
                    (Privilege) ($this.todo = todo.stream().filter(it -> it != superEnv).collect(com.sun.tools.javac.util.List.collector()));
                    (@Special Privilege) ((TypeEnter.Phase) $this).doCompleteEnvs(com.sun.tools.javac.util.List.of(superEnv));
                }
            }
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
            overrideMap[baseHandlers()][BaseHandler.Methods.specific(tree)].stream()
                    .filter(baseHandler -> baseHandler.shouldProcess(advance))
                    .map(baseHandler -> getAnnotationsByTypeWithOuter(modifiers, env, tree, baseHandler))
                    .nonnull()
                    .collect(Collectors.toCollection(ArrayList::new))
                    .let(result -> result.sort((a, b) -> (int) (a.getKey().handler().priority() - b.getKey().handler().priority())))
                    .forEach(entry -> entry.getValue().forEach(tuple -> process(env, tree, owner, entry.getKey(), tuple.v1, tuple.v2, advance)));
        overrideMap[syntaxHandlers()][BaseSyntaxHandler.Methods.process].forEach(handler -> process(env, tree, owner, handler, advance));
        if (modifiers != null)
            AccessibleHandler.transformPackageLocalToProtected(tree, owner, modifiers);
    }
    
    public @Nullable <A extends Annotation> Map.Entry<BaseHandler<A>, List<Tuple2<A, JCTree.JCAnnotation>>> getAnnotationsByTypeWithOuter(final JCTree.JCModifiers modifiers, final Env<AttrContext> env, final JCTree tree,
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
    
    private void process(final Env<AttrContext> env, final JCTree tree, final JCTree owner, final BaseSyntaxHandler baseHandler, final boolean advance) {
        final JCTree.JCCompilationUnit topLevel = maker.toplevel;
        final int pos = maker.pos;
        try {
            maker.toplevel = env.toplevel;
            maker.pos = tree.pos;
            baseHandler.process(env, tree, owner, advance);
        } finally {
            maker.toplevel = topLevel;
            maker.pos = pos;
        }
    }
    
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
        return switch (tree) {
            case JCTree.JCVariableDecl _ -> range == Handler.Range.FIELD;
            case JCTree.JCMethodDecl _   -> range == Handler.Range.METHOD;
            case JCTree.JCClassDecl _    -> range == Handler.Range.CLASS;
            default                      -> false;
        };
    }
    
    public void injectMember(final Env<AttrContext> env, final JCTree tree, final boolean advance = false) {
        final JCTree.JCClassDecl decl = env.enclClass;
        decl.defs = decl.defs.append(tree);
        if (!advance) {
            final Env<AttrContext> dup = env.dup(env.enclClass);
            process(env, tree, env.enclClass, true);
            (Privilege) enter.classEnter(tree, dup);
            (Privilege) memberEnter.memberEnter(tree, dup);
            process(env, tree, env.enclClass, false);
        }
    }
    
    LinkedList<JCTree> flowContext = { };
    
    public static LinkedList<JCTree> flowContext() = instance(HandlerSupport.class).flowContext;
    
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
    
    LinkedList<JCTree> lowerContext = { };
    
    public static LinkedList<JCTree> lowerContext() = instance(HandlerSupport.class).lowerContext;
    
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
    
    LinkedList<JCTree> genContext = { };
    
    public static LinkedList<JCTree> genContext() = instance(HandlerSupport.class).genContext;
    
    @Hook
    private static void genDef_$Enter(final Gen $this, final JCTree tree, final Env<?> env) = genContext().addLast(tree);
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.FINALLY)))
    private static void genDef_$Exit(final Gen $this, final JCTree tree, final Env<?> env) = genContext().removeLast();
    
    @Hook
    private static void genExpr_$Enter(final Gen $this, final JCTree tree, final Type pt) = genContext().addLast(tree);
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.FINALLY)))
    private static void genExpr_$Exit(final Gen $this, final JCTree tree, final Type pt) = genContext().removeLast();
    
}
