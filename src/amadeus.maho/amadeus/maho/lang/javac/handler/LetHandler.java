package amadeus.maho.lang.javac.handler;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

import com.sun.source.tree.Tree;
import com.sun.source.tree.TreeVisitor;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.ArgumentAttr;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.DeferredAttr;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.jvm.ByteCodes;
import com.sun.tools.javac.jvm.Gen;
import com.sun.tools.javac.jvm.Items;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.javac.JavacContext;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.runtime.DebugHelper;

import static com.sun.tools.javac.code.Flags.FINAL;

@RequiredArgsConstructor
@TransformProvider
public class LetHandler extends JavacContext {
    
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class LetType extends ArgumentAttr.ArgumentType<JCTree.JCExpression> {
        
        ArgumentAttr argumentAttr;
        
        public LetType(final ArgumentAttr argumentAttr, final JCTree.JCExpression tree, final Env<AttrContext> env, final JCTree.LetExpr speculativeExpr, final Map<Attr.ResultInfo, Type> speculativeTypes = new HashMap<>()) {
            argumentAttr.super(tree, env, speculativeExpr, speculativeTypes);
            this.argumentAttr = argumentAttr;
        }
        
        @Override
        protected Type overloadCheck(final Attr.ResultInfo resultInfo, final DeferredAttr.DeferredAttrContext deferredAttrContext) = (Privilege) argumentAttr.checkSpeculative(((JCTree.LetExpr) speculativeTree).expr, resultInfo);
        
        @Override
        protected LetType dup(final JCTree.JCExpression tree, final Env<AttrContext> env) = { argumentAttr, tree, env, (JCTree.LetExpr) speculativeTree, speculativeTypes };
        
    }
    
    public static final AtomicInteger counter = { };
    
    public static Name nextName(final Names names = instance().names) = names.fromString(STR."$let$\{counter.getAndIncrement()}");
    
    @Hook
    private static Hook.Result visitTree(final Attr $this, final JCTree tree) {
        if (tree instanceof JCTree.LetExpr letExpr) {
            attribLetExpr($this, letExpr);
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    @Privilege
    private static void attribLetExpr(final Attr attr, final JCTree.LetExpr letExpr) {
        final Env<AttrContext> env = attr.env, localEnv = env.dup(letExpr, env.info.dup(env.info.scope.dup()));
        try {
            attr.attribStats(letExpr.defs, localEnv);
            attr.result = attr.check(letExpr, attr.attribExpr(letExpr.expr, localEnv), Kinds.KindSelector.VAL, attr.resultInfo);
            if (letExpr.type.constValue() != null)
                attr.result = letExpr.type = attr.result.constType(null);
        } finally { localEnv.info.scope.leave(); }
    }
    
    @Hook
    private static Hook.Result visitApply(final ArgumentAttr $this, final JCTree.JCMethodInvocation that) {
        if (that.getTypeArguments().isEmpty()) {
            (Privilege) $this.processArg((JCTree.JCExpression) that,
                    (Function<JCTree.JCExpression, ArgumentAttr.ArgumentType<JCTree.JCExpression>>)
                            (Object)
                                    (Function<JCTree.JCExpression, ArgumentAttr.ArgumentType<? extends JCTree.JCExpression>>)
                                            speculativeTree -> speculativeTree instanceof JCTree.JCMethodInvocation invocation ? $this.new ResolvedMethodType(that, (Privilege) $this.env, invocation) :
                                                    speculativeTree instanceof JCTree.LetExpr letExpr ? new LetType($this, that, (Privilege) $this.env, letExpr) :
                                                            DebugHelper.<ArgumentAttr.ArgumentType<JCTree.JCExpression>>breakpointThenError());
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    @Hook(forceReturn = true)
    private static Tree.Kind getKind(final JCTree.LetExpr $this) = Tree.Kind.OTHER;
    
    @Hook(forceReturn = true)
    private static <R, D> R accept(final JCTree.LetExpr $this, final TreeVisitor<R, D> v, final D d) = v.visitOther($this, d);
    
    @Hook(at = @At(method = @At.MethodInsn(name = "load")), capture = true, jump = @At(method = @At.MethodInsn(name = "load"), offset = 1))
    private static Hook.Result visitLetExpr(final Items.Item capture, final Gen $this, final JCTree.LetExpr letExpr) = (Privilege) capture.typecode == ByteCodes.VOIDcode ? new Hook.Result().jump() : Hook.Result.VOID;
    
    public JCTree.JCExpression let(final JCTree.JCExpression root, final JCTree.JCExpression... expressions) {
        final LinkedList<JCTree.JCVariableDecl> letVariables = { };
        final IdentityHashMap<JCTree.JCExpression, JCTree.JCIdent> letIdentities = { };
        Stream.of(expressions)
                .filter(expression -> !(expression instanceof JCTree.JCIdent) && !(expression instanceof JCTree.JCLiteral))
                .forEach(expression -> {
                    final Name varName = nextName(names);
                    letVariables << maker.VarDef(maker.Modifiers(FINAL), varName, null, expression);
                    letIdentities[expression] = maker.Ident(varName);
                });
        if (letIdentities.isEmpty())
            return root;
        TreeTranslator.translate(letIdentities, root);
        if (root instanceof JCTree.LetExpr letExpr) {
            letExpr.defs = Stream.concat(letVariables.stream(), letExpr.defs.stream()).collect(List.collector());
            return root;
        }
        return maker.LetExpr(letVariables.stream().collect(List.collector()), root);
    }
    
}
