package amadeus.maho.lang.javac.handler;

import java.lang.invoke.MethodHandles;
import java.util.LinkedList;
import javax.lang.model.type.NullType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeVisitor;

import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Check;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.MatchBindingsComputer;
import com.sun.tools.javac.jvm.Code;
import com.sun.tools.javac.jvm.Gen;
import com.sun.tools.javac.jvm.Items;
import com.sun.tools.javac.parser.JavacParser;
import com.sun.tools.javac.parser.Tokens;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeCopier;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.DefinedBy;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.javac.JavacContext;
import amadeus.maho.lang.javac.MahoJavac;
import amadeus.maho.lang.javac.handler.base.BaseSyntaxHandler;
import amadeus.maho.lang.javac.handler.base.HandlerSupport;
import amadeus.maho.lang.javac.handler.base.Syntax;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.Proxy;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.dynamic.ClassLocal;
import amadeus.maho.util.dynamic.Cloner;
import amadeus.maho.util.dynamic.LookupHelper;
import amadeus.maho.util.dynamic.Wrapper;
import amadeus.maho.util.runtime.ObjectHelper;

import static amadeus.maho.lang.javac.handler.BranchHandler.PRIORITY;
import static amadeus.maho.util.bytecode.Bytecodes.INVOKEVIRTUAL;
import static amadeus.maho.util.runtime.ObjectHelper.requireNonNull;
import static com.sun.tools.javac.code.Flags.STATIC;
import static com.sun.tools.javac.code.Kinds.Kind.TYP;
import static com.sun.tools.javac.code.TypeTag.BOT;
import static com.sun.tools.javac.jvm.ByteCodes.*;
import static com.sun.tools.javac.jvm.CRTFlags.*;
import static com.sun.tools.javac.tree.JCTree.Tag.*;

@TransformProvider
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PUBLIC)
@Syntax(priority = PRIORITY)
public class BranchHandler extends BaseSyntaxHandler {
    
    public static final int PRIORITY = 1 << 3;
    
    public interface VoidMark { }
    
    private static final ClassLocal<Class<?>> voidMarkLocal = { BranchHandler::voidMark };
    
    private static Class<?> voidMark(final Class<?> clazz) {
        final Wrapper<?> wrapper = { clazz, "VoidMark", VoidMark.class };
        return wrapper.defineHiddenWrapperClass(MethodHandles.Lookup.ClassOption.NESTMATE, MethodHandles.Lookup.ClassOption.STRONG);
    }
    
    public static @Nullable Type voidMark(final @Nullable Type type) = type == null || type instanceof Type.JCVoidType || type instanceof VoidMark ? type : (Type) Cloner.copyFields(voidMarkLocal[type.getClass()], type);
    
    public interface SafeExpression {
        
        boolean allowed();
        
        static void allow(final @Nullable JCTree tree) = switch (tree) {
            case JCTree.JCBinary binary
                    when binary.hasTag(AdditionalOperators.TAG_NULL_OR) -> {
                allow(binary.lhs);
                allow(binary.rhs);
            }
            case JCTree.JCFieldAccess access                            -> {
                if (access instanceof SafeFieldAccess safeFieldAccess)
                    safeFieldAccess.allowed = true;
                allow(access.selected);
            }
            case JCTree.JCMethodInvocation invocation                   -> {
                if (invocation instanceof SafeMethodInvocation safeMethodInvocation)
                    safeMethodInvocation.allowed = true;
                allow(invocation.meth);
            }
            case JCTree.JCTypeCast typeCast                             -> allow(typeCast.expr);
            case JCTree.JCExpressionStatement statement                 -> allow(statement.expr);
            case null,
                 default                                                -> { }
        };
        
    }
    
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PUBLIC)
    public static class SafeMethodInvocation extends JCTree.JCMethodInvocation implements SafeExpression {
        
        @Getter
        boolean allowed = false;
        
        public SafeMethodInvocation(final JCMethodInvocation invocation) = this(invocation.typeargs, invocation.meth, invocation.args);
        
    }
    
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PUBLIC)
    public static class SafeFieldAccess extends JCTree.JCFieldAccess implements SafeExpression {
        
        @Getter
        boolean allowed = false;
        
        public SafeFieldAccess(final JCFieldAccess access) = this(access.selected, access.name, access.sym);
        
    }
    
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PUBLIC)
    public static class AssertFieldAccess extends JCTree.JCFieldAccess {
        
        public AssertFieldAccess(final JCFieldAccess access) = this(access.selected, access.name, access.sym);
        
    }
    
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PUBLIC)
    public static class NullOrBinary extends JCTree.JCBinary {
        
        public NullOrBinary(final JCBinary binary) = this(binary.getTag(), binary.lhs, binary.rhs, binary.operator);
        
    }
    
    public static class TopMarkType extends Type implements NullType {
        
        public TopMarkType() = super(null, List.nil());
        
        @Override
        public TypeTag getTag() = BOT;
        
        @Override
        @DefinedBy(DefinedBy.Api.LANGUAGE_MODEL)
        public TypeKind getKind() = TypeKind.NULL;
        
        @Override
        @DefinedBy(DefinedBy.Api.LANGUAGE_MODEL)
        public <R, P> R accept(final TypeVisitor<R, P> v, final P p) = v.visitNull(this, p);
        
        @Override
        public Type constType(final Object value) = this;
        
        @Override
        public String stringValue() = "null";
        
        @Override
        public boolean isNullOrReference() = true;
        
    }
    
    public static final String
            requireNonNull = LookupHelper.method1(ObjectHelper::requireNonNull).getName(),
            assertNonNull  = LookupHelper.methodV1(ObjectHelper::assertNonNull).getName();
    
    Name
            ObjectHelperName   = name(ObjectHelper.class),
            nullOrName         = name("??"),
            requireNonNullName = name(requireNonNull),
            assertNonNullName  = name(assertNonNull);
    
    @Hook(at = @At(field = @At.FieldInsn(name = "SUBSUB"), ordinal = 0, offset = 1), capture = true)
    private static boolean term3Rest(final boolean capture, final JavacParser $this, final JCTree.JCExpression t, final List<JCTree.JCExports> typeArgs) = capture || $this.token().kind == Tokens.TokenKind.BANG;
    
    @Hook(at = @At(field = @At.FieldInsn(name = "POSTDEC"), ordinal = 0), before = false, capture = true)
    private static JCTree.Tag term3Rest(final JCTree.Tag capture, final JavacParser $this, final JCTree.JCExpression t, final List<JCTree.JCExports> typeArgs)
        = $this.token().kind == Tokens.TokenKind.BANG ? JavacContext.AdditionalOperators.TAG_POST_ASSERT_ACCESS : capture;
    
    @Hook(value = TreeInfo.class, isStatic = true)
    private static Hook.Result getStartPos(final JCTree tree) {
        if (tree.getTag() == JavacContext.AdditionalOperators.TAG_POST_ASSERT_ACCESS)
            return new Hook.Result(TreeInfo.getStartPos(((JCTree.JCUnary) tree).arg));
        return Hook.Result.VOID;
    }
    
    @Hook(value = TreeInfo.class, isStatic = true, at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static boolean isExpressionStatement(final boolean capture, final JCTree.JCExpression tree) = capture || tree instanceof JCTree.JCUnary unary && unary.getTag() == JavacContext.AdditionalOperators.TAG_POST_ASSERT_ACCESS;
    
    @Hook
    private static void visitUnary(final Attr $this, final JCTree.JCUnary unary) {
        if (unary.getTag() == JavacContext.AdditionalOperators.TAG_POST_ASSERT_ACCESS)
            throw new ReAttrException(instance(BranchHandler.class).requireNonNullInvocation(unary.arg, unary.pos), unary);
    }
    
    public JCTree.JCMethodInvocation requireNonNullInvocation(final JCTree.JCExpression expression, final int at = expression.pos)
        = maker.at(at).Apply(List.nil(), maker.Select(IdentQualifiedName(ObjectHelper.class), requireNonNullName), List.of(expression));
    
    @Hook
    public static <P> Hook.Result visitMethodInvocation(final TreeCopier<P> $this, final MethodInvocationTree node, final P p) {
        if (node instanceof SafeMethodInvocation invocation) {
            final SafeMethodInvocation result = { $this.copy(invocation.typeargs, p), $this.copy(invocation.meth, p), $this.copy(invocation.args, p) };
            result.allowed = invocation.allowed;
            result.pos = invocation.pos;
            return { result };
        }
        return Hook.Result.VOID;
    }
    
    @Hook
    public static <P> Hook.Result visitMemberSelect(final TreeCopier<P> $this, final MemberSelectTree node, final P p) {
        if (node instanceof SafeFieldAccess access) {
            final SafeFieldAccess result = { $this.copy(access.selected, p), access.name, null };
            result.allowed = access.allowed;
            result.pos = access.pos;
            return { result };
        }
        if (node instanceof AssertFieldAccess access) {
            final AssertFieldAccess result = { $this.copy(access.selected, p), access.name, null };
            result.pos = access.pos;
            return { result };
        }
        return Hook.Result.VOID;
    }
    
    @Hook
    private static void visitExec(final Attr $this, final JCTree.JCExpressionStatement statement) = SafeExpression.allow(statement.expr);
    
    @Hook
    private static void visitLambda(final Attr $this, final JCTree.JCLambda lambda) = SafeExpression.allow(lambda.body);
    
    /*
        a ?? b
     */
    @Hook
    private static Hook.Result visitBinary(final Attr $this, final JCTree.JCBinary binary) {
        if (binary.hasTag(AdditionalOperators.TAG_NULL_OR)) {
            attrNullOrExpr($this, binary);
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    private static void attrNullOrExpr(final Attr attr, final JCTree.JCBinary binary) {
        final Env<AttrContext> env = env(attr);
        final Check check = (Privilege) attr.chk;
        final Symtab symtab = (Privilege) attr.syms;
        SafeExpression.allow(binary.lhs);
        final Type left = (Privilege) check.checkNonVoid(binary.lhs.pos(), attr.attribExpr(binary.lhs, env));
        if (binary.rhs instanceof JCTree.JCLambda || binary.rhs instanceof JCTree.JCMemberReference || binary.rhs instanceof JCTree.JCParens)
            binary.rhs = instance(BranchHandler.class).maker.TypeCast(left, binary.rhs);
        final Type right = (Privilege) check.checkNonVoid(binary.rhs.pos(), attr.attribExpr(binary.rhs, env));
        final List<Type> types = List.of(left, right);
        final Type owner = (Privilege) attr.condType(List.of(binary.lhs.pos(), binary.rhs.pos()), types);
        final NullOrBinary tree = { binary };
        tree.pos = binary.pos;
        final List<Type> argTypes = left.isPrimitive() == right.isPrimitive() ? types : types.map(((Privilege) attr.types)::boxedTypeOrType);
        tree.operator = { instance(BranchHandler.class).nullOrName, new Type.MethodType(argTypes, owner, List.nil(), symtab.noSymbol), -1, symtab.noSymbol };
        (Privilege) (attr.result = (Privilege) attr.check(tree, owner, Kinds.KindSelector.VAL, (Privilege) attr.resultInfo));
        (Privilege) (attr.matchBindings = MatchBindingsComputer.EMPTY);
        throw new ReAttrException(() -> binary.type = tree.type, false, tree, binary);
    }
    
    @Hook
    private static Hook.Result isBooleanOrNumeric_$Hook(final Attr $this, final Env<AttrContext> env, final JCTree.JCExpression tree)
        = tree instanceof NullOrBinary binary ? new Hook.Result(isBooleanOrNumeric($this, env, binary.lhs) && isBooleanOrNumeric($this, env, binary.rhs)) : Hook.Result.VOID;
    
    @Proxy(INVOKEVIRTUAL)
    private static native boolean isBooleanOrNumeric(Attr $this, Env<AttrContext> env, JCTree.JCExpression tree);
    
    private final Hook.Result errorTypeResult = { symtab.objectType };
    
    @Hook
    private static Hook.Result error(final Code.State $this) = instance(BranchHandler.class).errorTypeResult;
    
    @Hook
    private static Hook.Result visitExec(final Gen $this, final JCTree.JCExpressionStatement statement) {
        final Env<Gen.GenContext> localEnv = ((Privilege) $this.env).dup(statement, (Privilege) new Gen.GenContext());
        try {
            (Privilege) ($this.env = localEnv);
            final JCTree.JCExpression expr = statement.expr;
            if (expr instanceof JCTree.JCUnary unary)
                switch (expr.getTag()) {
                    case POSTINC -> unary.setTag(PREINC);
                    case POSTDEC -> unary.setTag(PREDEC);
                }
            final Code code = (Privilege) $this.code;
            Assert.check(code.isStatementStart());
            if (statement.expr instanceof JCTree.JCMethodInvocation invocation)
                statement.expr.type = ((Symbol.MethodSymbol) requireNonNull(symbol(invocation.meth))).getReturnType();
            final Items.Item result = $this.genExpr(statement.expr, statement.expr.type);
            @Nullable Code.Chain thenExit = null;
            @Nullable final Code.Chain exit = (Privilege) localEnv.info.exit;
            if (exit != null) {
                switch ((Privilege) result.typecode) {
                    case VOIDcode   -> thenExit = code.branch(goto_);
                    case INTcode,
                         FLOATcode,
                         BYTEcode,
                         SHORTcode,
                         CHARcode   -> {
                        code.emitop0(pop);
                        thenExit = code.branch(goto_);
                    }
                    case LONGcode,
                         DOUBLEcode -> {
                        code.emitop0(pop2);
                        thenExit = code.branch(goto_);
                    }
                }
                code.resolve(exit);
                code.resolvePending();
                final Code.State state = (Privilege) code.state;
                ((Privilege) state.stack)[(Privilege) state.stacksize - 1] = ((Privilege) $this.syms).objectType;
                code.emitop0(pop);
                if (thenExit != null)
                    code.resolve(thenExit);
            } else
                (Privilege) result.drop();
            Assert.check(code.isStatementStart());
        } finally { (Privilege) ($this.env = localEnv.next); }
        return Hook.Result.NULL;
    }
    
    @Hook
    private static Hook.Result load(final Items.Item $this) = { $this };
    
    @Hook
    private static Hook.Result visitBinary(final Gen $this, final JCTree.JCBinary binary) {
        if (binary instanceof NullOrBinary nullOr) {
            genNullOrExpr($this, nullOr);
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    private static void genNullOrExpr(final Gen gen, final NullOrBinary nullOr) {
        final Code code = (Privilege) gen.code;
        final Symtab symtab = (Privilege) gen.syms;
        final Items items = (Privilege) gen.items;
        code.statBegin(nullOr.pos);
        if (nullOr.lhs.type == symtab.botType)
            if (nullOr.rhs.type == symtab.botType) { // null ?? null => null
                (Privilege) code.emit1(aconst_null);
                (Privilege) ((Privilege) code.state).push(symtab.objectType);
                (Privilege) (gen.result = (Privilege) items.makeStackItem(nullOr.type));
            } else // null ?? b => b
                (Privilege) (gen.result = (Privilege) gen.genExpr(nullOr.rhs, nullOr.type).load());
        else {
            final Env<Gen.GenContext> localEnv = ((Privilege) gen.env).dup(nullOr.lhs, (Privilege) new Gen.GenContext());
            (Privilege) (gen.env = localEnv);
            final boolean genCrt = (Privilege) gen.genCrt;
            final int startPc = genCrt ? code.curCP() : 0;
            final Items.Item lhs = (Privilege) gen.genExpr(nullOr.lhs, nullOr.type).load();
            if (genCrt)
                code.crt.put(lhs, CRT_FLOW_CONTROLLER, startPc, code.curCP());
            @Nullable Code.Chain thenExit = null;
            @Nullable final Code.Chain whenNull = (Privilege) localEnv.info.exit;
            if (nullOr.lhs.type instanceof Type.JCPrimitiveType) {
                if (whenNull != null)
                    thenExit = code.branch(goto_);
            } else {
                code.emitop0(dup); // ref
                thenExit = code.branch(if_acmp_nonnull);
            }
            code.resolve(whenNull);
            (Privilege) (gen.env = localEnv.next);
            if (thenExit != null) {
                final int rhsStartPc = genCrt ? code.curCP() : 0;
                final JCTree.JCExpression rhs = nullOr.rhs;
                code.statBegin(rhs.pos);
                code.emitop0(pop); // ref
                (Privilege) gen.genExpr(rhs, nullOr.type).load();
                (Privilege) ((Privilege) code.state).forceStackTop(nullOr.type);
                if (genCrt)
                    code.crt.put(rhs, CRT_FLOW_TARGET, rhsStartPc, code.curCP());
                code.resolve(thenExit);
            }
            (Privilege) (gen.result = (Privilege) items.makeStackItem(nullOr.type));
        }
    }
    
    /*
        a?.b();
        var result = a?.b() ?? c;
     */
    @Hook(at = @At(method = @At.MethodInsn(name = "isEmpty"), offset = -1, ordinal = 0), jump = @At(method = @At.MethodInsn(name = "illegal"), offset = 2, ordinal = 0))
    private static Hook.Result term3Rest(final JavacParser $this, @Hook.Reference JCTree.JCExpression t, @Hook.Reference @Nullable List<JCTree.JCExpression> typeArgs) {
        final Tokens.Token token = $this.token();
        if (token.kind == AdditionalOperators.KIND_SAFE_ACCESS) {
            final int pos = token.pos;
            $this.nextToken();
            typeArgs = (Privilege) $this.typeArgumentsOpt((Privilege) JavacParser.EXPR);
            final var F = F($this).at(pos);
            final Name ident = (Privilege) $this.ident(true);
            t = toP($this, F.Select(t, ident));
            final JCTree.JCExpression typeArgumentsOpt = (Privilege) $this.typeArgumentsOpt(t);
            t = (Privilege) $this.argumentsOpt(typeArgs, typeArgumentsOpt);
            if (t instanceof JCTree.JCMethodInvocation invocation)
                (t = new SafeMethodInvocation(invocation)).pos = invocation.pos;
            else if (t instanceof JCTree.JCFieldAccess access)
                (t = new SafeFieldAccess(access)).pos = access.pos;
            else
                throw new AssertionError(STR."\{t.getClass()}: \{t}");
            typeArgs = null;
            return new Hook.Result().jump();
        } else if (token.kind == AdditionalOperators.KIND_ASSERT_ACCESS) {
            final int pos = token.pos;
            $this.nextToken();
            typeArgs = (Privilege) $this.typeArgumentsOpt((Privilege) JavacParser.EXPR);
            final var F = F($this).at(pos);
            final Name ident = (Privilege) $this.ident(true);
            t = toP($this, F.Select(t, ident));
            final JCTree.JCExpression typeArgumentsOpt = (Privilege) $this.typeArgumentsOpt(t);
            t = (Privilege) $this.argumentsOpt(typeArgs, typeArgumentsOpt);
            if (t instanceof JCTree.JCMethodInvocation invocation && invocation.meth instanceof JCTree.JCFieldAccess access)
                (invocation.meth = new AssertFieldAccess(access)).pos = invocation.pos;
            else if (t instanceof JCTree.JCFieldAccess access)
                (t = new AssertFieldAccess(access)).pos = access.pos;
            else
                throw new AssertionError(STR."\{t.getClass()}: \{t}");
            typeArgs = null;
            return new Hook.Result().jump();
        }
        return Hook.Result.VOID;
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)))
    private static void visitApply(final Attr $this, final JCTree.JCMethodInvocation invocation) = markVoid($this, invocation);
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)))
    private static void visitSelect(final Attr $this, final JCTree.JCFieldAccess access) = markVoid($this, access);
    
    private static void markVoid(final Attr attr, final JCTree.JCExpression expression) {
        if (expression instanceof SafeExpression safeExpression) {
            expression.type = voidMark(expression.type);
            if (!safeExpression.allowed())
                instance().log.error(JCDiagnostic.DiagnosticFlag.RESOLVE_ERROR, expression, new JCDiagnostic.Error(MahoJavac.KEY, "safe.access.not.allowed"));
        } else {
            @Nullable final JCTree.JCFieldAccess access = expression instanceof JCTree.JCFieldAccess it ? it : expression instanceof JCTree.JCMethodInvocation invocation && invocation.meth instanceof JCTree.JCFieldAccess it ? it : null;
            if (access != null && access.selected.type instanceof VoidMark)
                expression.type = voidMark(expression.type);
        }
        if (expression.type instanceof VoidMark) {
            final LinkedList<JCTree> context = HandlerSupport.attrContext();
            final @Nullable JCTree prev = context[-2];
            if (prev != null)
                if (!(prev.getTag() == AdditionalOperators.TAG_NULL_OR ||
                      prev instanceof JCTree.JCFieldAccess access && access.selected == expression ||
                      prev instanceof JCTree.JCMethodInvocation invocation && invocation.meth == expression))
                    expression.type = ((Privilege) attr.syms).voidType;
        }
    }
    
    @Hook
    private static Hook.Result visitApply(final Gen $this, final JCTree.JCMethodInvocation invocation) {
        if (invocation instanceof SafeMethodInvocation safeMethodInvocation && noneMatch(requireNonNull(symbol(safeMethodInvocation.meth)).flags_field, STATIC)) {
            genSafeExpression($this, safeMethodInvocation);
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)))
    private static void visitApply_$Return(final Gen $this, final JCTree.JCMethodInvocation invocation) = dropResult($this, invocation);
    
    @Hook
    private static Hook.Result visitSelect(final Gen $this, final JCTree.JCFieldAccess access) {
        if (access instanceof SafeFieldAccess safeFieldAccess && safeFieldAccess.sym instanceof Symbol.VarSymbol symbol && noneMatch(symbol.flags_field, STATIC)) {
            genSafeExpression($this, safeFieldAccess);
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)))
    private static void visitSelect_$Return(final Gen $this, final JCTree.JCFieldAccess access) = dropResult($this, access);
    
    private static <T extends JCTree.JCExpression> void dropResult(final Gen gen, final T expression) {
        if ((Privilege) ((Privilege) gen.result).typecode != VOIDcode && expression.type instanceof VoidMark) {
            final JCTree prev = HandlerSupport.genContext()[-2];
            if (prev != null)
                if (!(prev.getTag() == AdditionalOperators.TAG_NULL_OR || prev instanceof JCTree.JCFieldAccess || prev instanceof JCTree.JCMethodInvocation invocation && invocation.meth == expression)) {
                    (Privilege) ((Privilege) gen.result).drop();
                    (Privilege) (gen.result = (Privilege) ((Privilege) gen.items).makeVoidItem());
                }
        }
    }
    
    private static <T extends JCTree.JCExpression> void genSafeExpression(final Gen gen, final T expression) {
        (Privilege) gen.setTypeAnnotationPositions(expression.pos);
        final Code code = (Privilege) gen.code;
        final Env<Gen.GenContext> env = (Privilege) gen.env;
        final Symtab symtab = (Privilege) gen.syms;
        code.statBegin(expression.pos);
        final boolean genCrt = (Privilege) gen.genCrt;
        final int startPc = genCrt ? code.curCP() : 0;
        final JCTree.JCFieldAccess meth = (JCTree.JCFieldAccess) (expression instanceof JCTree.JCMethodInvocation invocation ? invocation.meth : expression);
        final Items.Item item = expression instanceof JCTree.JCMethodInvocation ? gen.genExpr(meth, (Privilege) gen.methodType) : (Privilege) gen.genExpr(meth.selected, meth.selected.type).load();
        code.emitop0(dup);
        final Code.Chain whenNull = code.branch(if_acmp_null);
        if (genCrt)
            code.crt.put(meth.selected, CRT_FLOW_CONTROLLER, startPc, code.curCP());
        final int invokeStartPc = genCrt ? code.curCP() : 0;
        final Items.Item result;
        if (expression instanceof JCTree.JCMethodInvocation invocation) {
            final Symbol.MethodSymbol method = (Symbol.MethodSymbol) requireNonNull(symbol(invocation.meth));
            gen.genArgs(invocation.args, method.externalType((Privilege) gen.types).getParameterTypes());
            code.statBegin(expression.pos);
            result = (Privilege) item.invoke();
        } else {
            final Symbol baseSymbol = symbol(meth.selected);
            final Symbol.VarSymbol varSymbol = (Symbol.VarSymbol) symbol(meth);
            final boolean selectSuper = baseSymbol != null && (baseSymbol.kind == TYP || baseSymbol.name == ((Privilege) gen.names)._super), accessSuper = (Privilege) gen.isAccessSuper(env.enclMethod);
            // extract items
            final Items items = (Privilege) gen.items;
            if (varSymbol == symtab.lengthVar) {
                code.emitop0(arraylength);
                result = (Privilege) ((Privilege) items.makeStackItem(symtab.intType)).load();
            } else
                result = (Privilege) ((Privilege) items.makeMemberItem(varSymbol, (Privilege) gen.nonVirtualForPrivateAccess(varSymbol) || selectSuper || accessSuper)).load();
        }
        if (genCrt)
            code.crt.put(expression, CRT_FLOW_TARGET, invokeStartPc, code.curCP());
        (Privilege) env.info.addExit(whenNull);
        (Privilege) (gen.result = result);
    }
    
    @Hook(at = @At(method = @At.MethodInsn(name = "genExpr")), before = false, capture = true)
    private static void visitSelect(final Items.Item capture, final Gen $this, final JCTree.JCFieldAccess access) {
        if (access instanceof AssertFieldAccess assertFieldAccess)
            instance(BranchHandler.class).genNullCheck((Privilege) $this.code, assertFieldAccess);
    }
    
    private void genNullCheck(final Code code, final JCTree tree) {
        code.statBegin(tree.pos);
        code.emitop0(dup);
        (Privilege) gen.callMethod(tree.pos(), symtab.enterClass(mahoModule, ObjectHelperName).type, assertNonNullName, List.of(symtab.objectType), true);
    }
    
}
