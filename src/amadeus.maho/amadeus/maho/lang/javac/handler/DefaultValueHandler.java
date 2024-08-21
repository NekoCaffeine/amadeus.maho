package amadeus.maho.lang.javac.handler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Check;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.Flow;
import com.sun.tools.javac.comp.TypeEnter;
import com.sun.tools.javac.parser.JavacParser;
import com.sun.tools.javac.parser.Tokens;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeCopier;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.javac.handler.base.BaseSyntaxHandler;
import amadeus.maho.lang.javac.handler.base.Syntax;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;

import static amadeus.maho.lang.javac.handler.DefaultValueHandler.PRIORITY;
import static amadeus.maho.util.runtime.ObjectHelper.requireNonNull;
import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.parser.Tokens.TokenKind.EQ;

@NoArgsConstructor
@TransformProvider
@Syntax(priority = PRIORITY)
public class DefaultValueHandler extends BaseSyntaxHandler {
    
    public static final int PRIORITY = 1 << 1;
    
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PUBLIC)
    public static class DerivedMethod extends JCTree.JCMethodDecl {
        
        JCMethodDecl source;
        
    }
    
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PUBLIC)
    public static class DefaultVariable extends JCTree.JCVariableDecl {
        
        boolean checked;
        
    }
    
    @Hook
    private static Hook.Result checkOverride(final Check $this, final Env<AttrContext> env, final JCTree.JCMethodDecl tree, final Symbol.MethodSymbol m) = Hook.Result.falseToVoid(tree instanceof DerivedMethod);
    
    @Hook
    private static Hook.Result checkVarargsMethodDecl(final Check $this, final Env<AttrContext> env, final JCTree.JCMethodDecl tree) = Hook.Result.falseToVoid(tree instanceof DerivedMethod);
    
    // Makes lexical parsing possible to parse the assignment statements after method parameters.
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static void formalParameter(final JCTree.JCVariableDecl capture, final JavacParser $this, final boolean lambdaParameters, final boolean recordComponents) {
        if (!lambdaParameters && $this.token().kind == EQ) {
            $this.accept(EQ);
            capture.init = $this.variableInitializer();
        }
    }
    
    @Hook(at = @At(field = @At.FieldInsn(name = "head")), capture = true)
    private static List<JCTree.JCStatement> checkFirstConstructorStat(final List<JCTree.JCStatement> capture, final Attr $this, final JCTree.JCMethodInvocation tree, final JCTree.JCMethodDecl enclMethod, final boolean error) {
        List<JCTree.JCStatement> result = capture;
        while (result.head instanceof DefaultVariable)
            result = result.tail;
        return result;
    }
    
    @Hook(value = TreeInfo.class, isStatic = true)
    private static Hook.Result isSyntheticInit(final JCTree stat) = Hook.Result.falseToVoid(stat instanceof DefaultVariable);
    
    @Hook
    private static Hook.Result setLazyConstValue(final Symbol.VarSymbol $this, final Env<AttrContext> env, final Attr attr, final JCTree.JCVariableDecl variable)
        = Hook.Result.falseToVoid(anyMatch(variable.mods.flags, PARAMETER) || instance(DefaultValueHandler.class).hasAnnotation(variable.mods, env, Default.class), null);
    
    @Hook(at = @At(method = @At.MethodInsn(name = "needsLazyConstValue")), before = false, capture = true, branchReversal = true)
    private static boolean visitVarDef(final boolean capture, final Attr $this, final JCTree.JCVariableDecl tree) = capture && !instance(DefaultValueHandler.class).hasAnnotation(tree.mods, env($this), Default.class);
    
    // There is no need to check assignment statements on method parameters.
    @Hook
    private static Hook.Result checkInit(final Flow.AssignAnalyzer $this, final JCDiagnostic.DiagnosticPosition pos, final Symbol.VarSymbol sym, final JCDiagnostic.Error errorKey)
        = Hook.Result.falseToVoid((sym.flags_field & PARAMETER) != 0 && !sym.name.equals(sym.name.table.names._this));
    
    @Hook
    private static void visitVarDef(final Attr $this, final JCTree.JCVariableDecl variable) {
        if (variable instanceof DefaultVariable defaultVariable && !defaultVariable.checked && variable.type instanceof Type.ArrayType arrayType) {
            final TreeMaker maker = instance(DefaultValueHandler.class).maker.at(variable.init.pos);
            final TreeCopier copier = { maker };
            final Type type = $this.attribExpr(copier.copy(variable.init), env($this), Type.recoveryType);
            if (!(type instanceof Type.ArrayType || type.getTag() == TypeTag.BOT)) {
                variable.init = maker.NewArray(maker.Type(arrayType.elemtype), List.nil(), List.of(variable.init instanceof JCTree.JCLambda || variable.init instanceof JCTree.JCMemberReference ?
                        maker.TypeCast(arrayType.elemtype, variable.init) : variable.init));
                defaultVariable.checked = true;
                throw new ReAttrException(variable, variable);
            }
        }
    }
    
    public static void eraseInitialization(final JCTree.JCVariableDecl decl) {
        decl.init = null;
        final @Nullable Symbol.VarSymbol sym = decl.sym;
        if (sym != null) {
            sym.setData(null);
            sym.flags_field &= ~HASINIT;
        }
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static JCTree.JCMethodDecl finalAdjustment(final JCTree.JCMethodDecl capture, final TypeEnter.RecordConstructorHelper $this, final JCTree.JCMethodDecl constructor) {
        final List<JCTree.JCVariableDecl> recordFieldDecls = (Privilege) $this.recordFieldDecls;
        capture.params.forEach(parameter -> {
            final @Nullable JCTree.JCVariableDecl component = ~recordFieldDecls.stream().filter(decl -> decl.name == parameter.name);
            if (component != null && component.init != null && parameter.init == null) {
                parameter.init = component.init;
                eraseInitialization(component);
            }
        });
        return capture;
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static JCTree.JCClassDecl recordDeclaration(final JCTree.JCClassDecl capture, final JavacParser $this, final JCTree.JCModifiers modifiers, final Tokens.Comment comment) {
        final Map<Name, JCTree.JCVariableDecl> mapping = capture.defs.stream()
                .cast(JCTree.JCVariableDecl.class)
                .collect(Collectors.toMap(decl -> decl.name, Function.identity()));
        capture.defs.stream()
                .cast(JCTree.JCMethodDecl.class)
                .filter(decl -> anyMatch(decl.mods.flags, COMPACT_RECORD_CONSTRUCTOR))
                .forEach(decl -> decl.params.forEach(parameter -> {
                    final @Nullable JCTree.JCVariableDecl component = mapping[parameter.name];
                    if (component != null && component.init != null && parameter.init == null) {
                        parameter.init = component.init;
                        eraseInitialization(component);
                    }
                }));
        return capture;
    }
    
    public static final long removeFlags = ~(ABSTRACT | NATIVE | SYNCHRONIZED | DEFAULT | RECORD | COMPACT_RECORD_CONSTRUCTOR);
    
    @Override
    public void process(final Env<AttrContext> env, final JCTree tree, final JCTree owner, final boolean advance) {
        if (!advance) {
            if (tree instanceof JCTree.JCMethodDecl methodDecl && owner instanceof JCTree.JCClassDecl) { // Expand methods with default parameters.
                final LinkedList<JCTree.JCVariableDecl> defaultValues = { };
                methodDecl.params.stream()
                        .filter(decl -> decl.init != null)
                        .forEach(defaultValues::addFirst);
                if (!defaultValues.isEmpty()) {
                    final ArrayList<ArrayList<JCTree.JCVariableDecl>> list = Stream.concat(Stream.of(new ArrayList<>(defaultValues)), Stream.generate(() -> defaultValues)
                                    .limit(defaultValues.size() - 1)
                                    .peek(LinkedList::removeLast)
                                    .map(ArrayList::new))
                            .collect(Collectors.toCollection(ArrayList::new));
                    if (!list.isEmpty()) { // Exhaustive different possible defaults.
                        final TreeMaker maker = this.maker.forToplevel(env.toplevel).at(tree.pos);
                        final List<JCTree.JCVariableDecl> params = methodDecl.params;
                        final Env<AttrContext> methodEnv = (Privilege) memberEnter.methodEnv(methodDecl, env);
                        (Privilege) (methodEnv.info.lint = lint);
                        final Map<JCTree.JCVariableDecl, JCTree.JCExpression> initMapping = params.stream()
                                .filter(decl -> decl.init != null)
                                .peek(decl -> decl.type = attr.attribType(decl.vartype, methodEnv))
                                .peek(decl -> { // Allowing default parameters to be declared as an array can be done using only curly braces without full declaration.
                                    if (decl.init instanceof JCTree.JCNewArray newArray && newArray.elemtype == null)
                                        if (decl.type instanceof Type.ArrayType arrayType) {
                                            newArray.elemtype = maker.at(methodDecl.pos).Type(arrayType.elemtype);
                                            decl.init = AssignHandler.transform(maker.at(newArray.pos), newArray, arrayType);
                                        } else
                                            AssignHandler.lower(decl, newArray, decl.type);
                                })
                                .collect(Collectors.toMap(Function.identity(), decl -> decl.init));
                        params.forEach(DefaultValueHandler::eraseInitialization);
                        final boolean def = noneMatch(methodDecl.mods.flags, PRIVATE | STATIC) && anyMatch(modifiers(owner).flags, INTERFACE);
                        if (def)
                            requireNonNull(modifiers(owner)).flags |= DEFAULT;
                        list.forEach(defaultParameters -> injectMember(env, derivedMethod(maker.at(methodDecl.pos), maker.at(methodDecl.pos).Modifiers(methodDecl.mods.flags & removeFlags | (def ? DEFAULT : 0), methodDecl.mods.annotations),
                                methodDecl.name, methodDecl.restype, methodDecl.typarams, params.stream()
                                        .filter(parameter -> defaultParameters.stream().noneMatch(defaultParameter -> defaultParameter == parameter))
                                        .map(parameter -> maker.at(parameter.pos).VarDef(parameter.mods, parameter.name, parameter.vartype, null))
                                        .collect(List.collector()), methodDecl.thrown, body(maker.at(methodDecl.body?.pos ?? methodDecl.pos), methodDecl, params, initMapping, defaultParameters, env), null, methodDecl)));
                        
                    }
                }
            }
        }
    }
    
    protected DerivedMethod derivedMethod(final TreeMaker maker, final JCTree.JCModifiers mods, final Name name, final JCTree.JCExpression returnTypeExpr, final List<JCTree.JCTypeParameter> typarams,
            final List<JCTree.JCVariableDecl> params, final List<JCTree.JCExpression> thrown, final JCTree.JCBlock body, final @Nullable JCTree.JCExpression defaultValue, final JCTree.JCMethodDecl source)
        = new DerivedMethod(mods, name, returnTypeExpr, typarams, null, params, thrown, body, defaultValue, null, source).let(result -> result.pos = maker.pos);
    
    protected JCTree.JCBlock body(final TreeMaker maker, final JCTree.JCMethodDecl methodDecl, final List<JCTree.JCVariableDecl> params, final Map<JCTree.JCVariableDecl, JCTree.JCExpression> initMapping,
            final Collection<JCTree.JCVariableDecl> defaultParameters, final Env<AttrContext> env) {
        final TreeCopier copier = { maker };
        final JCTree.JCMethodInvocation apply = maker.Apply(null, maker.Ident(methodDecl.name.equals(names.init) ? names._this : methodDecl.name), params.map(parameter -> maker.at(parameter.pos).Ident(parameter.name)));
        return maker.Block(0L, new LinkedList<>(defaultParameters).descendingStream()
                .map(parameter -> new DefaultVariable(maker.at(parameter.mods.pos).Modifiers(FINAL), parameter.name, (JCTree.JCExpression) copier.copy(parameter.vartype), (JCTree.JCExpression) copier.copy(initMapping[parameter]), null)
                        .let(it -> it.type = parameter.sym.type))
                .collect(List.<JCTree.JCStatement>collector())
                .append(methodDecl.sym.getReturnType() instanceof Type.JCVoidType ? maker.at(methodDecl.body?.pos ?? methodDecl.pos).Exec(apply) : maker.at(methodDecl.body?.pos ?? methodDecl.pos).Return(apply)));
    }
    
}
