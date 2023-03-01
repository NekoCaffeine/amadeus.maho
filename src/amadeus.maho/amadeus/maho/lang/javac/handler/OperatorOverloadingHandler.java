package amadeus.maho.lang.javac.handler;

import java.util.EnumSet;
import java.util.LinkedList;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
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

import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.javac.handler.base.BaseSyntaxHandler;
import amadeus.maho.lang.javac.handler.base.HandlerMarker;
import amadeus.maho.lang.javac.handler.base.Syntax;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.InvisibleType;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.runtime.DebugHelper;

import static amadeus.maho.lang.javac.handler.OperatorOverloadingHandler.PRIORITY;
import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Kinds.Kind.MTH;
import static com.sun.tools.javac.tree.JCTree.Tag.*;

@TransformProvider
@NoArgsConstructor
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
    
    private static final EnumSet<JCTree.Tag> cannotOverload = EnumSet.of(AND, OR);
    
    public static boolean canOverload(final JCTree.Tag tag) = !cannotOverload.contains(tag);
    
    // FIXME optimization kind selector, see also: Attr#check
    @Hook
    private static Hook.Result check(final Attr $this, final JCTree tree, final Type found, @Hook.Reference Kinds.KindSelector kind, final @InvisibleType(Attr$ResultInfo) Object resultInfo) {
        if (tree instanceof JCTree.JCMethodInvocation invocation && invocation.args.isEmpty() && invocation.typeargs.isEmpty()) {
            final Name name = name(invocation.meth);
            final Names names = name.table.names;
            if (name != names._this && name != names._super) {
                final OperatorOverloadingHandler handler = instance(OperatorOverloadingHandler.class);
                final Env<AttrContext> env = env($this);
                final Type site = invocation.meth instanceof JCTree.JCFieldAccess access ? access.selected.type : env.enclClass.sym.type;
                if (handler.discardDiagnostic(() -> ((Privilege) handler.resolve.resolveQualifiedMethod(invocation.pos(), env, site, name, List.of(found), List.nil())).kind == MTH)) {
                    kind = Kinds.KindSelector.VAR;
                    return { };
                }
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
            lowerExpr = handler.methodInvocation(handler.name("PUT"), env, tree, () -> access.indexed, () -> access.index, () -> tree.rhs);
        else if (tree.lhs instanceof JCTree.JCArrayAccess access)
            lowerExpr = handler.methodInvocation(handler.name("PUT"), env, tree, () -> access.indexed, () -> access.index, () -> tree.rhs);
        if (lowerExpr == null && tree.lhs instanceof JCTree.JCMethodInvocation invocation && invocation.args.isEmpty())
            lowerExpr = handler.lowerSetter(invocation, getter -> tree.rhs);
        return Hook.Result.nullToVoid(handler.lower(tree, env, lowerExpr));
    }
    
    @Hook
    private static Hook.Result visitAssignop(final Attr $this, final JCTree.JCAssignOp tree) {
        if (skip(tree.lhs.type))
            return Hook.Result.VOID;
        @Nullable JCTree.JCExpression lowerExpr;
        final OperatorOverloadingHandler handler = instance(OperatorOverloadingHandler.class);
        final TreeMaker maker = handler.maker.at(tree.pos);
        final Names names = handler.names;
        final Env<AttrContext> env = env($this);
        final JCTree.Tag tag = tree.getTag();
        if ((lowerExpr = handler.methodInvocation(handler.name(tag), env, tree, () -> tree.lhs, () -> tree.rhs)) == null)
            if (tree.lhs instanceof OperatorInvocation invocation && invocation.source instanceof JCTree.JCArrayAccess getter) {
                final Name resultName = LetHandler.nextName(names), indexedName = LetHandler.nextName(names), indexName = LetHandler.nextName(names);
                lowerExpr = maker.LetExpr(List.of(
                                maker.VarDef(maker.Modifiers(FINAL), indexedName, null, getter.indexed),
                                maker.VarDef(maker.Modifiers(FINAL), indexName, null, getter.index),
                                maker.VarDef(maker.Modifiers(FINAL), resultName, null, maker.Binary(tag.noAssignOp(),
                                        maker.Apply(List.nil(), maker.Select(maker.Ident(indexedName), handler.name("GET")), List.of(maker.Ident(indexName))), tree.rhs)),
                                maker.Exec(maker.Apply(List.nil(), maker.Select(maker.Ident(indexedName), handler.name("PUT")), List.of(maker.Ident(indexName), maker.Ident(resultName))))),
                        maker.Ident(resultName));
            } else if (tree.lhs instanceof JCTree.JCArrayAccess getter && canOverload(tag.noAssignOp()) &&
                    (lowerExpr = handler.methodInvocation(handler.name(tag.noAssignOp()), env, tree, () -> tree.lhs, () -> tree.rhs)) != null) {
                final Name resultName = LetHandler.nextName(names), indexedName = LetHandler.nextName(names), indexName = LetHandler.nextName(names);
                lowerExpr = maker.LetExpr(List.of(
                                maker.VarDef(maker.Modifiers(FINAL), indexedName, null, getter.indexed),
                                maker.VarDef(maker.Modifiers(FINAL), indexName, null, getter.index),
                                maker.VarDef(maker.Modifiers(FINAL), resultName, null, maker.Binary(tag.noAssignOp(), maker.Indexed(maker.Ident(indexedName), maker.Ident(indexName)), tree.rhs)),
                                maker.Exec(maker.Assign(maker.Indexed(maker.Ident(indexedName), maker.Ident(indexName)), maker.Ident(resultName)))),
                        maker.Ident(resultName));
            } else if ((tree.lhs instanceof JCTree.JCFieldAccess || tree.lhs instanceof JCTree.JCIdent) && canOverload(tag.noAssignOp()) &&
                    (lowerExpr = handler.methodInvocation(handler.name(tag.noAssignOp()), env, tree, () -> tree.lhs, () -> tree.rhs)) != null) {
                final Name resultName = LetHandler.nextName(names);
                if (tree.lhs instanceof JCTree.JCFieldAccess access && !(access.selected instanceof JCTree.JCIdent)) {
                    final Name varName = LetHandler.nextName(names);
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
        if (lowerExpr == null && tree.lhs instanceof JCTree.JCMethodInvocation invocation && invocation.args.isEmpty())
            lowerExpr = handler.lowerSetter(invocation, getter -> maker.Binary(tag.noAssignOp(), getter, tree.rhs));
        return Hook.Result.nullToVoid(handler.lower(tree, env, lowerExpr));
    }
    
    @Hook
    private static Hook.Result visitIndexed(final Attr $this, final JCTree.JCArrayAccess tree) {
        if (skip(tree.indexed.type))
            return Hook.Result.VOID;
        final OperatorOverloadingHandler handler = instance(OperatorOverloadingHandler.class);
        final Env<AttrContext> env = env($this);
        return Hook.Result.nullToVoid(handler.lower(tree, env, handler.methodInvocation(handler.name("GET"), env, tree, () -> tree.indexed, () -> tree.index)));
    }
    
    @Hook
    private static Hook.Result visitUnary(final Attr $this, final JCTree.JCUnary tree) {
        if (skip(tree.arg.type))
            return Hook.Result.VOID;
        final OperatorOverloadingHandler handler = instance(OperatorOverloadingHandler.class);
        final TreeMaker maker = handler.maker;
        final Env<AttrContext> env = env($this);
        @Nullable JCTree.JCExpression lowerExpr = handler.methodInvocation(handler.name(tree.getTag()), env, tree, () -> tree.arg);
        if (lowerExpr == null && tree.arg instanceof JCTree.JCMethodInvocation invocation && invocation.args.isEmpty() && switch (tree.getTag()) {
            case PREINC, PREDEC, POSTINC, POSTDEC -> true;
            default                               -> false;
        }) {
            final boolean flag = switch (tree.getTag()) {
                case PREINC, PREDEC   -> true;
                case POSTINC, POSTDEC -> false;
                default               -> throw new IllegalStateException("Unexpected value: " + tree.getTag());
            };
            lowerExpr = handler.lowerSetter(invocation,
                    flag ? getter -> maker.Binary(tree.getTag() == PREINC ? PLUS : MINUS, getter, maker.Literal(1)) : UnaryOperator.identity(),
                    flag ? UnaryOperator.identity() : getter -> maker.Binary(tree.getTag() == POSTINC ? PLUS : MINUS, getter, maker.Literal(1)));
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
    
    public @Nullable JCTree.JCExpression lowerSetter(final JCTree.JCMethodInvocation invocation, final UnaryOperator<JCTree.JCExpression> result,
            final UnaryOperator<JCTree.JCExpression> resultWrapper = UnaryOperator.identity()) {
        final Name name = name(invocation.meth);
        final Names names = name.table.names;
        if (name != names._this && name != names._super) {
            maker.at(invocation.pos);
            final Name resultName = LetHandler.nextName(names);
            if (invocation.meth instanceof JCTree.JCFieldAccess access && !(access.selected instanceof JCTree.JCIdent)) {
                final Name varName = LetHandler.nextName(names);
                return maker.LetExpr(List.of(
                                maker.VarDef(maker.Modifiers(FINAL), varName, null, access.selected),
                                maker.VarDef(maker.Modifiers(FINAL), resultName, null, result.apply(maker.Apply(List.nil(), maker.Select(maker.Ident(varName), access.name), List.nil()))),
                                maker.Exec(maker.Apply(List.nil(), maker.Select(maker.Ident(varName), access.name), List.of(resultWrapper.apply(maker.Ident(resultName)))))),
                        maker.Ident(resultName));
            } else {
                final TreeCopier<?> copier = { maker };
                return maker.LetExpr(List.of(
                                maker.VarDef(maker.Modifiers(FINAL), resultName, null, result.apply(copier.copy(invocation))),
                                maker.Exec(maker.Apply(List.nil(), copier.copy(invocation.meth), List.of(resultWrapper.apply(maker.Ident(resultName)))))),
                        maker.Ident(resultName));
            }
        }
        return null;
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
                    case null, default                    -> { }
                }
            }, lowerExpr, tree);
        return lowerExpr;
    }
    
    @Privilege
    public final @Nullable JCTree.JCExpression methodInvocation(final Name name, final Env<AttrContext> env, final JCTree.JCExpression source, final Supplier<JCTree.JCExpression>... expressions) {
        final List<JCTree.JCExpression> args = List.from(expressions).map(Supplier::get);
        final Env<AttrContext> localEnv = env.dup(maker.at(source.pos).Apply(List.nil(), maker.Select(args.head, name), args.tail), env.info.dup());
        final ListBuffer<Type> argTypes = { };
        final Kinds.KindSelector kind = attr.attribArgs(Kinds.KindSelector.VAL, ((JCTree.JCMethodInvocation) localEnv.tree).args, localEnv, argTypes);
        final Type methodPrototype = attr.newMethodTemplate(attr.resultInfo.pt, argTypes.toList(), List.nil());
        localEnv.info.pendingResolutionPhase = null;
        final Attr.ResultInfo resultInfo = attr.new ResultInfo(kind, methodPrototype, attr.resultInfo.checkContext);
        final Type methodType = discardDiagnostic(() -> {
            final List<JCTree.JCExpression> realArgs = List.from(expressions).map(Supplier::get);
            final JCTree.JCMethodInvocation realApply = maker.at(source.pos).Apply(List.nil(), maker.Select(realArgs.head, name), realArgs.tail);
            final LinkedList<JCTree> attrContext = HandlerMarker.attrContext();
            attrContext << (localEnv.tree = realApply);
            try {
                return attr.attribTree(realApply.meth, localEnv, resultInfo);
            } catch (final ReAttrException e) {
                if (e.breakTree == localEnv.tree) {
                    e.breakTree = source;
                    if (e.tree instanceof JCTree.JCMethodInvocation invocation)
                        e.tree = new OperatorInvocation(invocation.meth, invocation.args, source);
                }
                throw e;
            } finally { attrContext--; }
        });
        if (localEnv.tree instanceof JCTree.JCMethodInvocation overloading) {
            if (!methodType.isErroneous()) {
                final Type returnType = methodType.getReturnType(), capturedReturnType = resultInfo.checkContext.inferenceContext().cachedCapture(source, returnType, true);
                attr.result = attr.check(overloading, capturedReturnType, Kinds.KindSelector.VAL, resultInfo);
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
