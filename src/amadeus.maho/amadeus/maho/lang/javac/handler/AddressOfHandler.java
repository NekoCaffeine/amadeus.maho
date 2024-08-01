package amadeus.maho.lang.javac.handler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Predicate;

import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.Operators;
import com.sun.tools.javac.parser.JavacParser;
import com.sun.tools.javac.parser.Tokens;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.javac.JavacContext;
import amadeus.maho.lang.javac.MahoJavac;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.function.FunctionHelper;
import amadeus.maho.util.runtime.DebugHelper;

import static com.sun.tools.javac.code.Flags.FINAL;
import static com.sun.tools.javac.comp.Operators.OperatorType.*;
import static com.sun.tools.javac.jvm.ByteCodes.nop;
import static com.sun.tools.javac.parser.Tokens.TokenKind.AMP;
import static com.sun.tools.javac.tree.JCTree.Tag.*;

@TransformProvider
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PUBLIC)
public class AddressOfHandler extends JavacContext {
    
    private record ExpressionWithTags(JCTree.JCExpression arg, Set<JCTree.Tag> tags) { }
    
    @Hook
    private static Hook.Result term3(final JavacParser $this) {
        final Tokens.Token token = (Privilege) $this.token;
        if (token.kind == AMP) {
            $this.nextToken();
            (Privilege) $this.selectExprMode();
            final JCTree.JCExpression t = (Privilege) $this.term3();
            return { F($this).at(token.pos).Unary(BITAND, t) };
        }
        return Hook.Result.VOID;
    }
    
    @Hook
    @Privilege
    private static void initUnaryOperators(final Operators $this) = $this.initOperators($this.unaryOperators, $this.new UnaryNumericOperator(BITAND)
            .addUnaryOperator(BYTE, LONG, nop)
            .addUnaryOperator(SHORT, LONG, nop)
            .addUnaryOperator(INT, LONG, nop)
            .addUnaryOperator(LONG, LONG, nop)
            .addUnaryOperator(FLOAT, LONG, nop)
            .addUnaryOperator(DOUBLE, LONG, nop)
    );
    
    Name
            UnsafeHelper   = name("amadeus.maho.util.runtime.UnsafeHelper"),
            unsafe         = name("unsafe"),
            Unsafe         = name("jdk.internal.misc.Unsafe"),
            allocateMemory = name("allocateMemory"),
            freeMemory     = name("freeMemory");
    
    private Symbol.MethodSymbol lookup(final Symbol.ClassSymbol owner, final Name member, final Predicate<Symbol.MethodSymbol> filter = _ -> true)
            = (Symbol.MethodSymbol) owner.members().getSymbolsByName(member, symbol -> symbol instanceof Symbol.MethodSymbol methodSymbol && filter.test(methodSymbol), Scope.LookupKind.NON_RECURSIVE).iterator().next();
    
    private static Predicate<Symbol.MethodSymbol> arg(final int count) = method -> method.params().size() == count;
    
    private static final Predicate<Symbol.MethodSymbol> arg1 = arg(1), arg2 = arg(2);
    
    @Hook
    private static Hook.Result visitApply(final Attr $this, final JCTree.JCMethodInvocation invocation) {
        if (!(Privilege) ((Privilege) ((Privilege) $this.env).info.attributionMode).isSpeculative) {
            final List<JCTree.JCUnary> addressOfArgs = invocation.args.stream()
                    .cast(JCTree.JCUnary.class)
                    .filter(unary -> unary.getTag() == BITAND)
                    .collect(List.collector());
            if (!addressOfArgs.isEmpty())
                instance(AddressOfHandler.class).letExpr(invocation, addressOfArgs);
        }
        return Hook.Result.VOID;
    }
    
    private static ExpressionWithTags arg(final JCTree.JCUnary expr) {
        JCTree.JCExpression arg = TreeInfo.skipParens(expr.arg);
        if (!(expr.arg instanceof JCTree.JCUnary))
            return { arg, Set.of() };
        final HashSet<JCTree.Tag> tags = { };
        while (arg instanceof JCTree.JCUnary unary) {
            tags += unary.getTag();
            arg = TreeInfo.skipParens(unary.arg);
        }
        return { arg, tags };
    }
    
    private void letExpr(final JCTree.JCMethodInvocation invocation, final List<JCTree.JCUnary> addressOfArgs) {
        final Env<AttrContext> env = (Privilege) attr.env;
        final JCTree.JCMethodInvocation speculativeTree = (JCTree.JCMethodInvocation) (Privilege) ((Privilege) attr.deferredAttr).attribSpeculative(invocation, env, (Privilege) attr.resultInfo);
        final Type returnType = speculativeTree.type;
        final boolean isVoid = returnType.getTag() == TypeTag.VOID;
        final Symbol.ClassSymbol UnsafeHelper = symtab.enterClass(mahoModule, this.UnsafeHelper), Unsafe = symtab.enterClass(mahoModule, this.Unsafe);
        final Symbol owner = ((Privilege) env.info.scope).owner;
        final Symbol.VarSymbol $unsafe = unsafe(invocation, env), $address = { FINAL, names.fromString("$address"), symtab.longType, owner };
        final @Nullable Symbol.VarSymbol $result = isVoid ? null : new Symbol.VarSymbol(FINAL, names.fromString("$result"), returnType, owner);
        long size = 0;
        final ListBuffer<JCTree.JCStatement> before = { }, after = { };
        final HashMap<String, Symbol.MethodSymbol> unsafeGetterCache = { };
        final IdentityHashMap<JCTree.JCUnary, JCTree.JCExpression> mapping = { };
        for (final JCTree.JCUnary unary : addressOfArgs) {
            final ExpressionWithTags expressionWithTags = arg(unary);
            final JCTree.JCExpression arg = expressionWithTags.arg();
            final Set<JCTree.Tag> tags = expressionWithTags.tags();
            final Type type = (Privilege) attr.attribTree(arg, env.dup(arg), tags.contains(POS) ? (Privilege) attr.unknownExprInfo : (Privilege) attr.varAssignmentInfo);
            if (type instanceof Type.JCPrimitiveType primitiveType) {
                final @Nullable String name = switch ((Privilege) primitiveType.tag) {
                    case BYTE   -> "Byte";
                    case SHORT  -> "Short";
                    case INT    -> "Int";
                    case LONG   -> "Long";
                    case FLOAT  -> "Float";
                    case DOUBLE -> "Double";
                    default     -> {
                        log.error(JCDiagnostic.DiagnosticFlag.RESOLVE_ERROR, arg, new JCDiagnostic.Error(MahoJavac.KEY, "address.of.type"));
                        yield null;
                    }
                };
                if (name == null)
                    continue;
                final JCTree.JCExpression offset = size == 0 ? maker.Ident($address) : maker.Binary(PLUS, maker.Ident($address), maker.Literal(size));
                if (offset instanceof JCTree.JCBinary binary)
                    binary.operator = (Privilege) operators.resolveBinary(offset, PLUS, symtab.longType, symtab.longType);
                offset.type = symtab.longType;
                mapping[unary] = offset;
                if (!tags.contains(NEG)) {
                    final JCTree.JCMethodInvocation put = maker.App(maker.Select(maker.Ident($unsafe), unsafeGetterCache.computeIfAbsent(STR."put\{name}", key -> lookup(Unsafe, name(key), arg2))), List.of(offset, arg));
                    put.type = symtab.voidType;
                    before.append(maker.Exec(put));
                }
                if (!tags.contains(POS)) {
                    final JCTree.JCAssign assign = maker.Assign(arg, maker.App(maker.Select(maker.Ident($unsafe), unsafeGetterCache.computeIfAbsent(STR."get\{name}", key -> lookup(Unsafe, name(key), arg1))), List.of(offset)));
                    assign.type = type;
                    after.append(maker.Exec(assign));
                }
                size += switch ((Privilege) primitiveType.tag) {
                    case BYTE   -> 1;
                    case SHORT  -> 2;
                    case INT    -> 4;
                    case LONG   -> 8;
                    case FLOAT  -> 4;
                    case DOUBLE -> 8;
                    default     -> DebugHelper.<Integer>breakpointThenError();
                };
            }
        }
        before.prepend(maker.VarDef($address, maker.App(maker.Select(maker.Ident($unsafe), lookup(Unsafe, allocateMemory)), List.of(maker.Literal(size)))));
        after.append(maker.Exec(maker.App(maker.Select(maker.Ident($unsafe), lookup(Unsafe, freeMemory)), List.of(maker.Ident($address)))));
        speculativeTree.args = invocation.args.map(arg -> mapping.getOrDefault(arg, arg));
        final Scope.WriteableScope scope = (Privilege) env.info.scope;
        scope.enter($unsafe);
        scope.enter($address);
        if (!isVoid)
            scope.enter($result);
        final JCTree.LetExpr letExpr = maker.LetExpr(before.toList()
                        .appendList(List.of(isVoid ? maker.Exec(speculativeTree) : maker.VarDef($result, speculativeTree)))
                        .appendList(after.toList()),
                isVoid ? maker.App(maker.Ident(lookup(UnsafeHelper, names.fromString("nop"))), List.nil()) : maker.Ident($result));
        letExpr.type = invocation.type = speculativeTree.type = attr.attribExpr(speculativeTree, env, (Privilege) attr.pt());
        throw new ReAttrException(FunctionHelper.nothing(), false, letExpr, invocation);
    }
    
}
