package amadeus.maho.lang.javac.handler;

import java.util.EnumSet;
import java.util.LinkedList;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.DeferredAttr;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeCopier;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.javac.handler.base.BaseSyntaxHandler;
import amadeus.maho.lang.javac.handler.base.HandlerSupport;
import amadeus.maho.lang.javac.handler.base.Syntax;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.runtime.DebugHelper;

import static amadeus.maho.lang.javac.handler.OperatorOverloadingHandler.PRIORITY;
import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.tree.JCTree.Tag.*;

@TransformProvider
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
@Syntax(priority = PRIORITY)
public class OperatorOverloadingHandler extends BaseSyntaxHandler {
    
    public static final int PRIORITY = 1 << 5;
    
    @NoArgsConstructor
    public static class OperatorInvocation extends JCTree.JCMethodInvocation {
        
        public JCTree source;
        
        public OperatorInvocation(final JCExpression meth, final List<JCExpression> args, final JCTree source) {
            super(List.nil(), meth, args);
            this.source = source;
            pos = source.pos;
        }
        
    }
    
    @NoArgsConstructor
    public static class OperatorFieldAccess extends JCTree.JCFieldAccess {
        
        public static class Failure extends FlowControlException {
            
            @Getter
            private static final Failure instance = { };
            
        }
        
    }
    
    private static final EnumSet<JCTree.Tag> cannotOverload = EnumSet.of(AND, OR, EQ, NE);
    
    public static boolean canOverload(final JCTree.Tag tag) = !cannotOverload.contains(tag);
    
    Name PLUS = name("PLUS"), PUT = name("PUT"), GET = name("GET");
    
    @Hook
    private static Hook.Result check(final Attr $this, final JCTree tree, final Type found, @Hook.Reference Kinds.KindSelector kind, final Attr.ResultInfo resultInfo) {
        if (tree instanceof JCTree.JCMethodInvocation invocation && invocation.args.isEmpty() && invocation.typeargs.isEmpty() && name(invocation.meth) instanceof Name name) {
            final Names names = name.table.names;
            if (name != names._this && name != names._super) {
                kind = Kinds.KindSelector.VAR;
                return { };
            }
        }
        return Hook.Result.VOID;
    }
    
    @Hook
    private static Hook.Result visitAssign(final Attr $this, final JCTree.JCAssign tree) {
        if (skip(tree.lhs.type))
            return Hook.Result.VOID;
        @Nullable JCTree.JCExpression lowerExpr = null;
        final OperatorOverloadingHandler handler = instance(OperatorOverloadingHandler.class);
        final Env<AttrContext> env = env($this);
        if (tree.lhs instanceof OperatorInvocation invocation && invocation.source instanceof JCTree.JCArrayAccess access)
            lowerExpr = handler.methodInvocation(handler.PUT, env, tree, () -> access.indexed, () -> access.index, () -> tree.rhs);
        else if (tree.lhs instanceof JCTree.JCArrayAccess access)
            lowerExpr = handler.methodInvocation(handler.PUT, env, tree, () -> access.indexed, () -> access.index, () -> tree.rhs);
        if (lowerExpr == null && tree.lhs instanceof JCTree.JCMethodInvocation invocation && invocation.args.isEmpty() && invocation.typeargs.isEmpty())
            lowerExpr = handler.lowerSetter(env, invocation, getter -> tree.rhs);
        return Hook.Result.nullToVoid(handler.lower(tree, env, lowerExpr));
    }
    
    @Hook
    private static Hook.Result visitAssignop(final Attr $this, final JCTree.JCAssignOp tree) {
        if (skip(tree.lhs.type))
            return Hook.Result.VOID;
        @Nullable JCTree.JCExpression lowerExpr;
        final OperatorOverloadingHandler handler = instance(OperatorOverloadingHandler.class);
        final TreeMaker maker = handler.maker.at(tree.pos);
        final Env<AttrContext> env = env($this);
        final JCTree.Tag tag = tree.getTag();
        if ((lowerExpr = handler.methodInvocation(handler.name(tag), env, tree, () -> tree.lhs, () -> tree.rhs)) == null)
            if (tree.lhs instanceof OperatorInvocation invocation && invocation.source instanceof JCTree.JCArrayAccess getter) {
                final LetHandler let = instance(LetHandler.class);
                final Name resultName = let.nextName(env), indexedName = let.nextName(env), indexName = let.nextName(env);
                lowerExpr = maker.LetExpr(List.of(
                                maker.VarDef(maker.Modifiers(FINAL), indexedName, null, getter.indexed),
                                maker.VarDef(maker.Modifiers(FINAL), indexName, null, getter.index),
                                maker.VarDef(maker.Modifiers(FINAL), resultName, null, maker.Binary(tag.noAssignOp(),
                                        maker.Apply(List.nil(), maker.Select(maker.Ident(indexedName), handler.GET), List.of(maker.Ident(indexName))), tree.rhs)),
                                maker.Exec(maker.Apply(List.nil(), maker.Select(maker.Ident(indexedName), handler.PUT), List.of(maker.Ident(indexName), maker.Ident(resultName))))),
                        maker.Ident(resultName));
            } else if (tree.lhs instanceof JCTree.JCArrayAccess getter && canOverload(tag.noAssignOp()) &&
                       (lowerExpr = handler.methodInvocation(handler.name(tag.noAssignOp()), env, tree, () -> tree.lhs, () -> tree.rhs)) != null) {
                final LetHandler let = instance(LetHandler.class);
                final Name resultName = let.nextName(env), indexedName = let.nextName(env), indexName = let.nextName(env);
                lowerExpr = maker.LetExpr(List.of(
                                maker.VarDef(maker.Modifiers(FINAL), indexedName, null, getter.indexed),
                                maker.VarDef(maker.Modifiers(FINAL), indexName, null, getter.index),
                                maker.VarDef(maker.Modifiers(FINAL), resultName, null, maker.Binary(tag.noAssignOp(), maker.Indexed(maker.Ident(indexedName), maker.Ident(indexName)), tree.rhs)),
                                maker.Exec(maker.Assign(maker.Indexed(maker.Ident(indexedName), maker.Ident(indexName)), maker.Ident(resultName)))),
                        maker.Ident(resultName));
            } else if ((tree.lhs instanceof JCTree.JCFieldAccess || tree.lhs instanceof JCTree.JCIdent) && canOverload(tag.noAssignOp()) &&
                       (lowerExpr = handler.methodInvocation(handler.name(tag.noAssignOp()), env, tree, () -> tree.lhs, () -> tree.rhs)) != null) {
                final LetHandler let = instance(LetHandler.class);
                final Name resultName = let.nextName(env);
                if (tree.lhs instanceof JCTree.JCFieldAccess access && !(access.selected instanceof JCTree.JCIdent)) {
                    final Name varName = let.nextName(env);
                    lowerExpr = maker.LetExpr(List.of(
                                    maker.VarDef(maker.Modifiers(FINAL), varName, null, access.selected),
                                    maker.VarDef(maker.Modifiers(FINAL), resultName, null, maker.Binary(tag.noAssignOp(), maker.Select(maker.Ident(varName), access.name), tree.rhs)),
                                    maker.Exec(maker.Assign(maker.Select(maker.Ident(varName), access.name), maker.Ident(resultName)))),
                            maker.Ident(resultName));
                } else
                    lowerExpr = maker.LetExpr(List.of(
                                    maker.VarDef(maker.Modifiers(FINAL), resultName, null, lowerExpr),
                                    maker.Exec(maker.Assign(new TreeCopier<>(maker).copy(tree.lhs), maker.Ident(resultName)))),
                            maker.Ident(resultName));
            }
        if (lowerExpr == null && tree.lhs instanceof JCTree.JCMethodInvocation invocation && invocation.args.isEmpty() && invocation.typeargs.isEmpty())
            lowerExpr = handler.lowerSetter(env, invocation, getter -> maker.Binary(tag.noAssignOp(), getter, tree.rhs));
        return Hook.Result.nullToVoid(handler.lower(tree, env, lowerExpr));
    }
    
    @Hook
    private static Hook.Result visitIndexed(final Attr $this, final JCTree.JCArrayAccess tree) {
        if (skip(tree.indexed.type))
            return Hook.Result.VOID;
        final OperatorOverloadingHandler handler = instance(OperatorOverloadingHandler.class);
        final Env<AttrContext> env = env($this);
        return Hook.Result.nullToVoid(handler.lower(tree, env, handler.methodInvocation(handler.GET, env, tree, () -> tree.indexed, () -> tree.index)));
    }
    
    @Hook
    private static Hook.Result visitUnary(final Attr $this, final JCTree.JCUnary tree) {
        if (skip(tree.arg.type))
            return Hook.Result.VOID;
        final OperatorOverloadingHandler handler = instance(OperatorOverloadingHandler.class);
        final TreeMaker maker = handler.maker;
        final Env<AttrContext> env = env($this);
        @Nullable JCTree.JCExpression lowerExpr = handler.methodInvocation(handler.name(tree.getTag()), env, tree, () -> tree.arg);
        if (lowerExpr == null) {
            final @Nullable Boolean flag = switch (tree.getTag()) {
                case PREINC,
                     PREDEC  -> true;
                case POSTINC,
                     POSTDEC -> false;
                default      -> null;
            };
            if (flag != null) {
                final UnaryOperator<JCTree.JCExpression>
                        pre = flag ? getter -> maker.Binary(tree.getTag() == PREINC ? JCTree.Tag.PLUS : MINUS, getter, maker.Literal(1)) : UnaryOperator.identity(),
                        post = flag ? UnaryOperator.identity() : getter -> maker.Binary(tree.getTag() == POSTINC ? JCTree.Tag.PLUS : MINUS, getter, maker.Literal(1));
                switch (tree.arg) {
                    case JCTree.JCMethodInvocation invocation when invocation.args.isEmpty() && invocation.typeargs.isEmpty()                   -> lowerExpr = handler.lowerSetter(env, invocation, pre, post);
                    case OperatorOverloadingHandler.OperatorInvocation invocation when invocation.source instanceof JCTree.JCArrayAccess access -> lowerExpr = handler.lowerPutter(env, access, pre, post);
                    default                                                                                                                     -> { }
                }
            }
            
        }
        return Hook.Result.nullToVoid(handler.lower(tree, env, lowerExpr));
    }
    
    @Hook
    private static Hook.Result visitBinary(final Attr $this, final JCTree.JCBinary tree) {
        if (skip(tree.lhs.type))
            return Hook.Result.VOID;
        if (canOverload(tree.getTag())) {
            final OperatorOverloadingHandler handler = instance(OperatorOverloadingHandler.class);
            final Env<AttrContext> env = env($this);
            return Hook.Result.nullToVoid(handler.lower(tree, env, handler.methodInvocation(handler.name(tree.getTag()), env, tree, () -> tree.lhs, () -> tree.rhs)));
        }
        return Hook.Result.VOID;
    }
    
    private static boolean skip(final @Nullable Type type) = type != null && !type.isErroneous() && !(type instanceof Type.UnknownType);
    
    public @Nullable JCTree.JCExpression lowerSetter(final Env<AttrContext> env, final JCTree.JCMethodInvocation invocation,
            final UnaryOperator<JCTree.JCExpression> pre, final UnaryOperator<JCTree.JCExpression> post = UnaryOperator.identity()) {
        final @Nullable Name name = name(invocation.meth);
        if (name != names._this && name != names._super) {
            maker.at(invocation.pos);
            final LetHandler let = instance(LetHandler.class);
            final Name resultName = let.nextName(env);
            if (invocation.meth instanceof JCTree.JCFieldAccess access && !(access.selected instanceof JCTree.JCIdent)) {
                final Name varName = let.nextName(env);
                return maker.LetExpr(List.of(
                                maker.VarDef(maker.Modifiers(FINAL), varName, null, access.selected),
                                maker.VarDef(maker.Modifiers(FINAL), resultName, null, pre.apply(maker.Apply(List.nil(), maker.Select(maker.Ident(varName), access.name), List.nil()))),
                                maker.Exec(maker.Apply(List.nil(), maker.Select(maker.Ident(varName), access.name), List.of(post.apply(maker.Ident(resultName)))))),
                        maker.Ident(resultName));
            } else {
                final TreeCopier<?> copier = { maker };
                return maker.LetExpr(List.of(
                                maker.VarDef(maker.Modifiers(FINAL), resultName, null, pre.apply(copier.copy(invocation))),
                                maker.Exec(maker.Apply(List.nil(), copier.copy(invocation.meth), List.of(post.apply(maker.Ident(resultName)))))),
                        maker.Ident(resultName));
            }
        }
        return null;
    }
    
    public @Nullable JCTree.JCExpression lowerPutter(final Env<AttrContext> env, final JCTree.JCArrayAccess access,
            final UnaryOperator<JCTree.JCExpression> pre, final UnaryOperator<JCTree.JCExpression> post = UnaryOperator.identity()) {
        maker.at(access.pos);
        final TreeCopier<?> copier = { maker };
        final LetHandler let = instance(LetHandler.class);
        final Name resultName = let.nextName(env);
        final JCTree.JCExpression letExpr = maker.LetExpr(List.of(
                        maker.VarDef(maker.Modifiers(FINAL), resultName, null, pre.apply(access)),
                        maker.Exec(maker.Assign(access, post.apply(maker.Ident(resultName))))),
                maker.Ident(resultName));
        return copier.copy(instance(LetHandler.class).let(env, letExpr, access.indexed, access.index));
    }
    
    public @Nullable JCTree.JCExpression lower(final JCTree tree, final Env<AttrContext> env, final @Nullable JCTree.JCExpression lowerExpr) {
        if (lowerExpr != null)
            throw new ReAttrException(() -> tree.type = lowerExpr.type, lowerExpr.type == null, next -> {
                switch (next) {
                    case JCTree.JCAssign assign           -> assign.type = null;
                    case JCTree.JCAssignOp assignOp       -> assignOp.type = null;
                    case JCTree.JCArrayAccess arrayAccess -> arrayAccess.type = null;
                    case JCTree.JCUnary unary             -> unary.type = null;
                    case JCTree.JCBinary binary           -> binary.type = null;
                    case null,
                         default                          -> { }
                }
            }, lowerExpr, tree);
        return lowerExpr;
    }
    
    @Hook(at = @At(method = @At.MethodInsn(name = "attribTree")), before = false, capture = true)
    private static void visitSelect(final Type capture, final Attr $this, final JCTree.JCFieldAccess tree) {
        if (tree instanceof OperatorFieldAccess)
            instance(OperatorOverloadingHandler.class).checkOperatorFieldAccess(capture, tree.name);
    }
    
    public void checkOperatorFieldAccess(final Type type, final Name name) {
        if (type.isPrimitiveOrVoid() || types.unboxedType(type) != Type.noType || type.tsym == symtab.stringType.tsym && name == PLUS || type.getTag() == TypeTag.ARRAY && (name == PUT || name == GET))
            throw OperatorFieldAccess.Failure.instance();
    }
    
    public final @Nullable JCTree.JCExpression methodInvocation(final Name name, final Env<AttrContext> env, final JCTree.JCExpression source, final Supplier<JCTree.JCExpression>... expressions) {
        final List<JCTree.JCExpression> args = List.from(expressions).map(Supplier::get);
        final Env<AttrContext> localEnv = env.dup(maker.at(source.pos).Apply(List.nil(), maker.Select(args.head, name), args.tail), (Privilege) env.info.dup());
        final ListBuffer<Type> argTypes = { };
        final Kinds.KindSelector kind = (Privilege) attr.attribArgs(Kinds.KindSelector.VAL, ((JCTree.JCMethodInvocation) localEnv.tree).args, localEnv, argTypes);
        final Type methodPrototype = (Privilege) attr.newMethodTemplate((Privilege) ((Privilege) attr.resultInfo).pt, argTypes.toList(), List.nil());
        (Privilege) (localEnv.info.pendingResolutionPhase = null);
        final Attr.ResultInfo resultInfo = (Privilege) attr.new ResultInfo(kind, methodPrototype, (Privilege) ((Privilege) attr.resultInfo).checkContext);
        final @Nullable Type methodType = discardDiagnostic(() -> {
            final List<JCTree.JCExpression> realArgs = List.from(expressions).map(Supplier::get);
            final OperatorFieldAccess access = { realArgs.head, name, null };
            access.pos = source.pos;
            final JCTree.JCMethodInvocation realApply = maker.at(source.pos).Apply(List.nil(), access, realArgs.tail);
            final LinkedList<JCTree> attrContext = HandlerSupport.attrContext();
            attrContext << (localEnv.tree = realApply);
            try {
                return (Privilege) attr.attribTree(realApply.meth, localEnv, resultInfo);
            } catch (final OperatorFieldAccess.Failure failure) {
                return null;
            } catch (final ReAttrException e) {
                if (e.breakTree == localEnv.tree) {
                    e.breakTree = source;
                    if (e.tree instanceof JCTree.JCMethodInvocation invocation)
                        e.tree = new OperatorInvocation(invocation.meth, invocation.args, source);
                }
                throw e;
            } finally { attrContext--; }
        });
        if (methodType != null)
            if (localEnv.tree instanceof JCTree.JCMethodInvocation overloading) {
                if (!methodType.isErroneous()) {
                    final Type returnType = methodType.getReturnType(), capturedReturnType = (Privilege) ((Privilege) resultInfo.checkContext).inferenceContext().cachedCapture(source, returnType, true);
                    (Privilege) (attr.result = (Privilege) attr.check(overloading, capturedReturnType, Kinds.KindSelector.VAL, resultInfo));
                    final Symbol symbol = symbol(overloading.meth);
                    final List<JCTree.JCExpression> realArgs = List.from(expressions).map(Supplier::get);
                    final OperatorInvocation invocation;
                    if (anyMatch(symbol.flags_field, STATIC))
                        invocation = { maker.at(source.pos).QualIdent(symbol), realArgs, source };
                    else
                        invocation = { maker.at(source.pos).Select(realArgs.head, symbol), realArgs.tail, source };
                    return invocation;
                }
            } else
                DebugHelper.breakpoint();
        return null;
    }
    
    // Making operator-only statements not evaluated as invalid statements.
    @Hook(value = TreeInfo.class, isStatic = true)
    private static Hook.Result isExpressionStatement(final JCTree.JCExpression expression) {
        final int ordinal = expression.getTag().ordinal();
        return Hook.Result.falseToVoid(ordinal >= POS.ordinal() && ordinal <= MOD.ordinal() || ordinal == INDEXED.ordinal());
    }
    
    @Hook(at = @At(method = @At.MethodInsn(name = "check"), ordinal = 1), capture = true)
    private static boolean complete(final boolean capture, final DeferredAttr.DeferredType $this, final Attr.ResultInfo resultInfo, final DeferredAttr.DeferredAttrContext deferredAttrContext) = true;
    
}
