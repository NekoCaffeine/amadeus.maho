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
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.MatchBindingsComputer;
import com.sun.tools.javac.jvm.Code;
import com.sun.tools.javac.jvm.Gen;
import com.sun.tools.javac.jvm.Items;
import com.sun.tools.javac.parser.JavacParser;
import com.sun.tools.javac.parser.Tokens;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeCopier;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.DefinedBy;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.javac.handler.base.BaseSyntaxHandler;
import amadeus.maho.lang.javac.handler.base.HandlerMarker;
import amadeus.maho.lang.javac.handler.base.Syntax;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.Proxy;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.dynamic.ClassLocal;
import amadeus.maho.util.dynamic.Wrapper;
import amadeus.maho.vm.JVM;

import static amadeus.maho.lang.javac.handler.BranchHandler.PRIORITY;
import static amadeus.maho.util.bytecode.Bytecodes.INVOKEVIRTUAL;
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
    
    public static Type voidMark(final Type type) = type == null || type instanceof Type.JCVoidType || type instanceof VoidMark ? type : (Type) JVM.local().copyObjectWithoutHead(voidMarkLocal[type.getClass()], type);
    
    public interface SafeExpression { }
    
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PUBLIC)
    public static class SafeMethodInvocation extends JCTree.JCMethodInvocation implements SafeExpression {
        
        public SafeMethodInvocation(final JCMethodInvocation invocation) = this(invocation.typeargs, invocation.meth, invocation.args);
        
    }
    
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PUBLIC)
    public static class SafeFieldAccess extends JCTree.JCFieldAccess implements SafeExpression {
        
        public SafeFieldAccess(final JCFieldAccess access) = this(access.selected, access.name, access.sym);
        
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
    
    Name nullOrName = name("??");
    
    @Hook
    public static <P> Hook.Result visitMethodInvocation(final TreeCopier<P> $this, final MethodInvocationTree node, final P p) {
        if (node instanceof SafeMethodInvocation invocation) {
            final SafeMethodInvocation result = { $this.copy(invocation.typeargs, p), $this.copy(invocation.meth, p), $this.copy(invocation.args, p) };
            result.pos = invocation.pos;
            return { result };
        }
        return Hook.Result.VOID;
    }
    
    @Hook
    public static <P> Hook.Result visitMemberSelect(final TreeCopier<P> $this, final MemberSelectTree node, final P p) {
        if (node instanceof SafeFieldAccess access) {
            final SafeFieldAccess result = { $this.copy(access.selected, p), access.name, null };
            result.pos = access.pos;
            return { result };
        }
        return Hook.Result.VOID;
    }
    
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
    
    @Privilege
    private static void attrNullOrExpr(final Attr attr, final JCTree.JCBinary binary) {
        final Type left = attr.chk.checkNonVoid(binary.lhs.pos(), attr.attribExpr(binary.lhs, attr.env));
        final Type right = attr.chk.checkNonVoid(binary.rhs.pos(), attr.attribExpr(binary.rhs, attr.env, binary.rhs instanceof JCTree.JCLambda || binary.rhs instanceof JCTree.JCMemberReference ? left : Type.noType));
        final List<Type> types = List.of(left, right);
        final Type owner = attr.condType(List.of(binary.lhs.pos(), binary.rhs.pos()), types);
        final NullOrBinary tree = { binary };
        tree.pos = binary.pos;
        final List<Type> argTypes = left.isPrimitive() == right.isPrimitive() ? types : types.map(attr.types::boxedTypeOrType);
        tree.operator = { instance(BranchHandler.class).nullOrName, new Type.MethodType(argTypes, owner, List.nil(), attr.syms.noSymbol), -1, attr.syms.noSymbol };
        attr.result = attr.check(tree, owner, Kinds.KindSelector.VAL, attr.resultInfo);
        attr.matchBindings = MatchBindingsComputer.EMPTY;
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
    @Privilege
    private static Hook.Result visitExec(final Gen $this, final JCTree.JCExpressionStatement statement) {
        final Env<Gen.GenContext> localEnv = $this.env.dup(statement, new Gen.GenContext());
        try {
            $this.env = localEnv;
            final JCTree.JCExpression expr = statement.expr;
            if (expr instanceof JCTree.JCUnary unary)
                switch (expr.getTag()) {
                    case POSTINC -> unary.setTag(PREINC);
                    case POSTDEC -> unary.setTag(PREDEC);
                }
            final Code code = $this.code;
            Assert.check(code.isStatementStart());
            if (statement.expr instanceof JCTree.JCMethodInvocation invocation)
                statement.expr.type = ((Symbol.MethodSymbol) symbol(invocation.meth)).getReturnType();
            final Items.Item result = $this.genExpr(statement.expr, statement.expr.type);
            @Nullable Code.Chain thenExit = null;
            @Nullable final Code.Chain exit = localEnv.info.exit;
            if (exit != null) {
                switch (result.typecode) {
                    case VOIDcode      -> thenExit = code.branch(goto_);
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
                code.state.stack[code.state.stacksize - 1] = $this.syms.objectType;
                code.emitop0(pop);
                if (thenExit != null)
                    code.resolve(thenExit);
            } else
                result.drop();
            Assert.check(code.isStatementStart());
        } finally { $this.env = localEnv.next; }
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
    
    @Privilege
    private static void genNullOrExpr(final Gen gen, final NullOrBinary nullOr) {
        final Code code = gen.code;
        code.statBegin(nullOr.pos);
        if (nullOr.lhs.type == gen.syms.botType)
            if (nullOr.rhs.type == gen.syms.botType) { // null ?? null => null
                gen.code.emit1(aconst_null);
                gen.result = gen.items.makeStackItem(nullOr.type);
            } else // null ?? b => b
                gen.result = gen.genExpr(nullOr.rhs, nullOr.type).load();
        else {
            final Env<Gen.GenContext> localEnv = gen.env.dup(nullOr.lhs, new Gen.GenContext());
            gen.env = localEnv;
            final boolean genCrt = gen.genCrt;
            final int startPc = genCrt ? code.curCP() : 0;
            final Items.Item lhs = gen.genExpr(nullOr.lhs, nullOr.type).load();
            if (genCrt)
                code.crt.put(lhs, CRT_FLOW_CONTROLLER, startPc, code.curCP());
            @Nullable Code.Chain thenExit = null;
            @Nullable final Code.Chain whenNull = localEnv.info.exit;
            if (nullOr.lhs.type instanceof Type.JCPrimitiveType) {
                if (whenNull != null)
                    thenExit = code.branch(goto_);
            } else {
                code.emitop0(dup); // ref
                thenExit = code.branch(if_acmp_nonnull);
            }
            code.resolve(whenNull);
            gen.env = localEnv.next;
            if (thenExit != null) {
                final int rhsStartPc = genCrt ? code.curCP() : 0;
                final JCTree.JCExpression rhs = nullOr.rhs;
                code.statBegin(rhs.pos);
                code.emitop0(pop); // ref
                gen.genExpr(rhs, nullOr.type).load();
                code.state.forceStackTop(nullOr.type);
                if (genCrt)
                    code.crt.put(rhs, CRT_FLOW_TARGET, rhsStartPc, code.curCP());
                code.resolve(thenExit);
            }
            gen.result = gen.items.makeStackItem(nullOr.type);
        }
    }
    
    /*
        a?.b();
        var result = a?.b() ?? c;
     */
    @Hook(at = @At(method = @At.MethodInsn(name = "isEmpty"), offset = -1, ordinal = 0), jump = @At(method = @At.MethodInsn(name = "illegal"), offset = 2, ordinal = 0))
    private static Hook.Result term3Rest(final JavacParser $this, @Hook.Reference JCTree.JCExpression t, @Hook.Reference List<JCTree.JCExpression> typeArgs) {
        final Tokens.Token token = token($this);
        if (token.kind == AdditionalOperators.KIND_SAFE_ACCESS) {
            final int pos = token.pos;
            $this.nextToken();
            typeArgs = typeArgumentsOpt($this, EXPR());
            final var F = F($this);
            t = toP($this, F.at(pos).Select(t, ident($this, true)));
            t = argumentsOpt($this, typeArgs, typeArgumentsOpt($this, t));
            if (t instanceof JCTree.JCMethodInvocation invocation)
                (t = new SafeMethodInvocation(invocation)).pos = invocation.pos;
            else if (t instanceof JCTree.JCFieldAccess access)
                (t = new SafeFieldAccess(access)).pos = access.pos;
            else
                throw new AssertionError(t.getClass() + ": " + t);
            typeArgs = null;
            return new Hook.Result().jump();
        }
        return Hook.Result.VOID;
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)))
    private static void visitApply(final Attr $this, final JCTree.JCMethodInvocation invocation) = markVoid($this, invocation);
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)))
    private static void visitSelect(final Attr $this, final JCTree.JCFieldAccess access) = markVoid($this, access);
    
    @Privilege
    private static void markVoid(final Attr attr, final JCTree.JCExpression expression) {
        if (expression instanceof SafeExpression)
            expression.type = voidMark(expression.type);
        else {
            @Nullable final JCTree.JCFieldAccess access = expression instanceof JCTree.JCFieldAccess it ? it : expression instanceof JCTree.JCMethodInvocation invocation && invocation.meth instanceof JCTree.JCFieldAccess it ? it : null;
            if (access != null && access.selected.type instanceof VoidMark)
                expression.type = voidMark(expression.type);
        }
        if (expression.type instanceof VoidMark) {
            final LinkedList<JCTree> context = HandlerMarker.attrContext();
            final @Nullable JCTree prev = context[-2];
            if (prev != null)
                if (!(prev.getTag() == AdditionalOperators.TAG_NULL_OR ||
                        prev instanceof JCTree.JCFieldAccess access && access.selected == expression ||
                        prev instanceof JCTree.JCMethodInvocation invocation && invocation.meth == expression))
                    expression.type = attr.syms.voidType;
        }
    }
    
    @Hook
    private static Hook.Result visitApply(final Gen $this, final JCTree.JCMethodInvocation invocation) {
        if (invocation instanceof SafeMethodInvocation safeMethodInvocation && noneMatch(symbol(safeMethodInvocation.meth).flags_field, STATIC)) {
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
    
    @Privilege
    private static <T extends JCTree.JCExpression> void dropResult(final Gen gen, final T expression) {
        if (gen.result.typecode != VOIDcode && expression.type instanceof VoidMark) {
            final JCTree prev = HandlerMarker.genContext()[-2];
            if (prev != null)
                if (!(prev.getTag() == AdditionalOperators.TAG_NULL_OR || prev instanceof JCTree.JCFieldAccess || prev instanceof JCTree.JCMethodInvocation invocation && invocation.meth == expression)) {
                    gen.result.drop();
                    gen.result = gen.items.makeVoidItem();
                }
        }
    }
    
    @Privilege
    private static <T extends JCTree.JCExpression> void genSafeExpression(final Gen gen, final T expression) {
        gen.setTypeAnnotationPositions(expression.pos);
        final Code code = gen.code;
        code.statBegin(expression.pos);
        final boolean genCrt = gen.genCrt;
        final int startPc = genCrt ? code.curCP() : 0;
        final JCTree.JCFieldAccess meth = (JCTree.JCFieldAccess) (expression instanceof JCTree.JCMethodInvocation invocation ? invocation.meth : expression);
        final Items.Item item = expression instanceof JCTree.JCMethodInvocation ? gen.genExpr(meth, gen.methodType) : gen.genExpr(meth.selected, meth.selected.type).load();
        code.emitop0(dup);
        final Code.Chain whenNull = code.branch(if_acmp_null);
        if (genCrt)
            code.crt.put(meth.selected, CRT_FLOW_CONTROLLER, startPc, code.curCP());
        final int invokeStartPc = genCrt ? code.curCP() : 0;
        final Items.Item result;
        if (expression instanceof JCTree.JCMethodInvocation invocation) {
            final Symbol.MethodSymbol method = (Symbol.MethodSymbol) symbol(invocation.meth);
            gen.genArgs(invocation.args, method.externalType(gen.types).getParameterTypes());
            code.statBegin(expression.pos);
            result = item.invoke();
        } else {
            final Symbol baseSymbol = symbol(meth.selected);
            final Symbol.VarSymbol varSymbol = (Symbol.VarSymbol) symbol(meth);
            final boolean selectSuper = baseSymbol != null && (baseSymbol.kind == TYP || baseSymbol.name == gen.names._super), accessSuper = gen.isAccessSuper(gen.env.enclMethod);
            if (varSymbol == gen.syms.lengthVar) {
                code.emitop0(arraylength);
                result = gen.items.makeStackItem(gen.syms.intType).load();
            } else
                result = gen.items.makeMemberItem(varSymbol, gen.nonVirtualForPrivateAccess(varSymbol) || selectSuper || accessSuper).load();
        }
        if (genCrt)
            code.crt.put(expression, CRT_FLOW_TARGET, invokeStartPc, code.curCP());
        gen.env.info.addExit(whenNull);
        gen.result = result;
    }
    
}
