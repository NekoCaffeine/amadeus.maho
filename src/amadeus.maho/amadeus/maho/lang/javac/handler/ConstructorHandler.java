package amadeus.maho.lang.javac.handler;

import java.lang.annotation.Annotation;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.Flow;
import com.sun.tools.javac.jvm.Gen;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Position;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.javac.handler.base.BaseHandler;
import amadeus.maho.lang.javac.handler.base.Handler;
import amadeus.maho.lang.javac.handler.base.HandlerMarker;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.function.FunctionHelper;
import amadeus.maho.util.runtime.StreamHelper;

import static com.sun.tools.javac.code.Flags.*;

@NoArgsConstructor
@TransformProvider
public abstract class ConstructorHandler<A extends Annotation> extends BaseHandler<A> {
    
    public static final int PRIORITY = SetterHandler.PRIORITY << 2;
    
    @NoArgsConstructor
    @Handler(value = NoArgsConstructor.class, priority = PRIORITY + 1)
    public static class NoArgsConstructorHandler extends ConstructorHandler<NoArgsConstructor> {
        
        @Override
        protected AccessLevel accessLevel(final NoArgsConstructor annotation) = annotation.value();
        
        @Override
        protected boolean varargs(final NoArgsConstructor annotation) = annotation.varargs();
        
        @Override
        protected List<JCTree.JCVariableDecl> fields(final Env<AttrContext> env, final JCTree.JCClassDecl decl) = List.nil();
        
        @Override
        public boolean initializedField(final Symbol.VarSymbol symbol) = false;
        
    }
    
    @NoArgsConstructor
    @Handler(value = AllArgsConstructor.class, priority = PRIORITY - 1)
    public static class AllArgsConstructorHandler extends ConstructorHandler<AllArgsConstructor> {
        
        @Override
        protected AccessLevel accessLevel(final AllArgsConstructor annotation) = annotation.value();
        
        @Override
        protected boolean varargs(final AllArgsConstructor annotation) = annotation.varargs();
        
        @Override
        protected List<JCTree.JCVariableDecl> fields(final Env<AttrContext> env, final JCTree.JCClassDecl decl) = decl.defs.stream()
                .filter(JCTree.JCVariableDecl.class::isInstance)
                .map(JCTree.JCVariableDecl.class::cast)
                .filter(variable -> noneMatch(variable.mods.flags, STATIC))
                .filter(variable -> noneMatch(variable.mods.flags, FINAL) || variable.init == null || hasAnnotation(variable.mods, env, Default.class))
                .filter(this::nonGenerating)
                .filter(variable -> !(marker.lookupAnnotation(variable.mods, env, variable, Getter.class)?.lazy() ?? false))
                .collect(List.collector());
        
    }
    
    @NoArgsConstructor
    @Handler(value = RequiredArgsConstructor.class, priority = PRIORITY)
    public static class RequiredArgsConstructorHandler extends ConstructorHandler<RequiredArgsConstructor> {
        
        @Override
        protected AccessLevel accessLevel(final RequiredArgsConstructor annotation) = annotation.value();
        
        @Override
        protected boolean varargs(final RequiredArgsConstructor annotation) = annotation.varargs();
        
        @Override
        protected List<JCTree.JCVariableDecl> fields(final Env<AttrContext> env, final JCTree.JCClassDecl decl) = decl.defs.stream()
                .filter(JCTree.JCVariableDecl.class::isInstance)
                .map(JCTree.JCVariableDecl.class::cast)
                .filter(variable -> noneMatch(variable.mods.flags, STATIC))
                .filter(variable -> anyMatch(variable.mods.flags, FINAL) && variable.init == null || hasAnnotation(variable.mods, env, Default.class))
                .filter(this::nonGenerating)
                .filter(variable -> !(marker.lookupAnnotation(variable.mods, env, variable, Getter.class)?.lazy() ?? false))
                .collect(List.collector());
        
    }
    
    @NoArgsConstructor
    public static class SyntheticConstructor extends JCTree.JCMethodDecl { }
    
    @Hook
    private static void visitAssign(final Gen $this, final JCTree.JCAssign assign) {
        if (symbol(TreeInfo.skipParens(assign.lhs)) instanceof Symbol.VarSymbol varSymbol && varSymbol.getConstValue() != null)
            varSymbol.setData(null);
    }
    
    private static final ThreadLocal<List<JCTree.JCExpression>> defaultInitExpr = { };
    
    @Hook
    private static void normalizeDefs_$Enter(final Gen $this, final List<JCTree> def, final Symbol.ClassSymbol symbol)
            = defaultInitExpr.set(def.stream().cast(JCTree.JCVariableDecl.class).filter(decl -> decl.init != null && hasAnnotation(decl.sym, Default.class)).map(decl -> decl.init).collect(List.collector()));
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.FINALLY)))
    private static void normalizeDefs_$Exit(final Gen $this, final List<JCTree> def, final Symbol.ClassSymbol symbol) = defaultInitExpr.set(null);
    
    private static List<JCTree.JCStatement> dropDefaultInitAssignment(final List<JCTree.JCStatement> initCode) = initCode.stream()
            .filter(statement -> !(statement instanceof JCTree.JCExpressionStatement expr) || !(expr.expr instanceof JCTree.JCAssign assign) || defaultInitExpr.get().stream().noneMatch(init -> init == assign.rhs))
            .collect(List.collector());
    
    // Place the code of the code block after the automatically generated constructor.
    @Hook
    private static Hook.Result normalizeMethod(final Gen $this, final JCTree.JCMethodDecl methodDecl, final List<JCTree.JCStatement> initCode, final List<Attribute.TypeCompound> initTAs) {
        if (methodDecl instanceof SyntheticConstructor) {
            methodDecl.body.stats = new ListBuffer<JCTree.JCStatement>().let(it -> {
                it.appendList(methodDecl.body.stats);
                it.appendList(dropDefaultInitAssignment(initCode));
            }).toList();
            if (methodDecl.body.endpos == Position.NOPOS)
                methodDecl.body.endpos = TreeInfo.endPos(methodDecl.body.stats.last());
            methodDecl.sym.appendUniqueTypeAttributes(initTAs);
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    // Solve the misjudgment that may occur after the last hook.
    @Hook
    private static Hook.Result checkInit(final Flow.AssignAnalyzer $this, final JCDiagnostic.DiagnosticPosition pos, final Symbol.VarSymbol symbol)
    = Hook.Result.falseToVoid(symbol.owner instanceof Symbol.ClassSymbol && noneMatch(symbol.flags_field, STATIC) && instance(HandlerMarker.class).baseHandlers().stream()
            .filter(ConstructorHandler.class::isInstance)
            .map(ConstructorHandler.class::cast)
            .filter(handler -> handler.include(symbol))
            .allMatch(handler -> handler.initializedField(symbol)));
    
    @Hook
    private static Hook.Result checkAssignable(final Attr $this, final JCDiagnostic.DiagnosticPosition pos, final Symbol.VarSymbol symbol, final JCTree base, final Env<AttrContext> env)
            = Hook.Result.falseToVoid(hasAnnotation(symbol, Default.class));
    
    @Hook(at = @At(method = @At.MethodInsn(name = "VarMightAlreadyBeAssigned")))
    private static Hook.Result letInit(final Flow.AssignAnalyzer $this, final JCDiagnostic.DiagnosticPosition pos, final Symbol.VarSymbol symbol) = Hook.Result.falseToVoid(hasAnnotation(symbol, Default.class));
    
    protected abstract AccessLevel accessLevel(A annotation);
    
    protected abstract boolean varargs(A annotation);
    
    protected abstract List<JCTree.JCVariableDecl> fields(final Env<AttrContext> env, JCTree.JCClassDecl decl);
    
    public boolean initializedField(final Symbol.VarSymbol symbol) = true;
    
    public boolean include(final Symbol.VarSymbol symbol) {
        final Env<AttrContext> env = typeEnvs().get(symbol.owner);
        return env != null && marker.hasAnnotation(env.enclClass.mods, env, handler().value());
    }
    
    // Check if this class needs to add a default constructor.
    @Override
    public void preprocessing(final Env<AttrContext> env) {
        final JCTree.JCClassDecl decl = env.enclClass;
        decl.defs.stream()
                .filter(JCTree.JCMethodDecl.class::isInstance)
                .map(JCTree.JCMethodDecl.class::cast)
                .filter(def -> def.name.equals(names.init))
                .filter(def -> def.pos == decl.pos)
                .findFirst()
                .filter(def -> hasAnnotation(decl.mods, env, handler().value()))
                .ifPresent(def -> decl.defs = decl.defs.stream().filter(it -> it != def).collect(List.collector()));
    }
    
    @Override
    public void processClass(final Env<AttrContext> env, final JCTree.JCClassDecl tree, final JCTree owner, final A annotation, final JCTree.JCAnnotation annotationTree, final boolean advance) {
        final List<JCTree.JCVariableDecl> fields = fields(env, tree);
        final long access = accessLevel(accessLevel(annotation));
        superConstructors(tree)
                .forEach(symbol -> {
                    final Function<String, String> simplify = simplify(tree.name.toString());
                    final List<JCTree.JCVariableDecl> params = params(tree, symbol, simplify);
                    final long varargs = varargs(annotation) ? VARARGS : fields.isEmpty() ? symbol.flags() & VARARGS : 0;
                    final List<JCTree.JCVariableDecl> decls = params.appendList(params(env, fields, simplify));
                    if (shouldInjectMethod(env, names.init, names(decls, env)))
                        injectMember(env, SyntheticConstructor(maker.Modifiers(access | varargs), names.init, maker.TypeIdent(TypeTag.VOID), List.nil(), decls, List.nil(),
                                maker.Block(0L, generateBody(env, params, fields)), null).let(it -> followAnnotation(annotationTree, "on", it.mods)));
                });
    }
    
    public SyntheticConstructor SyntheticConstructor(final JCTree.JCModifiers mods, final Name name, final JCTree.JCExpression returnType, final List<JCTree.JCTypeParameter> typarams, final List<JCTree.JCVariableDecl> params,
            final List<JCTree.JCExpression> thrown, final JCTree.JCBlock body, final @Nullable JCTree.JCExpression defaultValue)
            = new SyntheticConstructor(mods, name, returnType, typarams, null, params, thrown, body, defaultValue, null).let(result -> result.pos = maker.pos);
    
    protected List<JCTree.JCVariableDecl> params(final Env<AttrContext> env, final List<JCTree.JCVariableDecl> fields, final Function<String, String> simplify)
            = fields.map(it -> maker.VarDef(maker.Modifiers(FINAL | PARAMETER).let(modifiers -> followAnnotation(env, it.mods, modifiers)), name(simplify.apply(it.name.toString())), it.vartype,
            hasAnnotation(it.mods, env, Default.class) ? it.init : null));
    
    protected Stream<Symbol.MethodSymbol> superConstructors(final JCTree.JCClassDecl decl) {
        final Symbol.TypeSymbol superSymbol = decl.sym.getSuperclass().tsym;
        return StreamHelper.fromIterable(superSymbol.members().getSymbols(symbol -> symbol.owner == superSymbol && symbol.isConstructor() && anyMatch(symbol.flags(), PROTECTED | PUBLIC), Scope.LookupKind.NON_RECURSIVE))
                .cast(Symbol.MethodSymbol.class);
    }
    
    protected List<JCTree.JCVariableDecl> params(final JCTree.JCClassDecl decl, final Symbol.MethodSymbol symbol, final Function<String, String> simplify) {
        final @Nullable Type extendingType = decl.extending != null ? decl.extending.type : null;
        final @Nullable TypeMapping mapping;
        if (extendingType != null && !extendingType.tsym.type.getTypeArguments().isEmpty())
            if (extendingType.getTypeArguments().size() == extendingType.tsym.type.getTypeArguments().size())
                mapping = { extendingType.tsym.type.getTypeArguments().stream().collect(Collectors.toMap(Function.identity(), FunctionHelper.abandon(extendingType.getTypeArguments().iterator()::next))) };
            else
                mapping = { extendingType.tsym.type.getTypeArguments().stream().collect(Collectors.toMap(Function.identity(), it -> symtab.objectType)) };
        else
            mapping = null;
        return symbol.owner == symtab.enumSym ? List.nil() : symbol.params().map(param -> maker.VarDef(maker.Modifiers(FINAL | PARAMETER).let(modifiers -> followAnnotation(param, modifiers)),
                name(simplify.apply(param.name.toString())), maker.Type(mapping == null ? param.type : param.type.map(mapping)), null));
    }
    
    protected JCTree.JCStatement callSuper(final List<JCTree.JCVariableDecl> params) = maker.Exec(maker.Apply(List.nil(), maker.Ident(names._super), params.map(param -> maker.Ident(param.name))));
    
    protected List<JCTree.JCStatement> generateBody(final Env<AttrContext> env, final List<JCTree.JCVariableDecl> superParams, final List<JCTree.JCVariableDecl> params) {
        final Function<String, String> simplify = simplify(env.enclClass.name.toString());
        final List<JCTree.JCStatement> result = (env.enclClass.sym.getSuperclass().tsym.getQualifiedName().equals(names.java_lang_Enum) ? List.<JCTree.JCStatement>nil() : List.of(callSuper(superParams)))
                .appendList(params.map(param -> maker.Exec(maker.Assign(maker.Select(maker.Ident(names._this), param.name), maker.Ident(name(simplify.apply(param.name.toString())))))));
        final @Nullable JCTree.JCMethodDecl postInitMethod = lookupMethod(env, name("postInit"));
        return postInitMethod != null && noneMatch(postInitMethod.mods.flags, STATIC) ? result.append(maker.Exec(maker.Apply(List.nil(), maker.Select(maker.Ident(names._this), postInitMethod.name), List.nil()))) : result;
    }
    
}
