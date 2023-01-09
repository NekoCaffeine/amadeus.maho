package amadeus.maho.lang.javac.handler;

import java.util.Map;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.CallChain;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.javac.JavacContext;
import amadeus.maho.lang.javac.handler.base.BaseSyntaxHandler;
import amadeus.maho.lang.javac.handler.base.HandlerMarker;
import amadeus.maho.lang.javac.handler.base.Syntax;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.InvisibleType;
import amadeus.maho.transform.mark.base.TransformProvider;

import static amadeus.maho.lang.javac.handler.SelfHandler.PRIORITY;
import static com.sun.tools.javac.code.Flags.STATIC;

@TransformProvider
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
@Syntax(priority = PRIORITY)
public class SelfHandler extends BaseSyntaxHandler {
    
    public static final int PRIORITY = -1 << 8;
    
    private static final String TypeEnter$HeaderPhase = "com.sun.tools.javac.comp.TypeEnter$HeaderPhase";
    
    private static final ThreadLocal<Boolean> headEnterContextLocal = ThreadLocal.withInitial(() -> true);
    
    @Hook
    private static void runPhase_$Enter(final @InvisibleType(TypeEnter$HeaderPhase) Object $this, final Env<AttrContext> env) = headEnterContextLocal.set(Boolean.FALSE);
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.FINALLY)))
    private static void runPhase_$Exit(final @InvisibleType(TypeEnter$HeaderPhase) Object $this, final Env<AttrContext> env) = headEnterContextLocal.set(Boolean.TRUE);
    
    @Hook
    private static Hook.Result attribType(final Attr $this, final JCTree type, final Env<AttrContext> env) {
        if (headEnterContextLocal.get() && type instanceof JCTree.JCIdent ident && ident.name == instance(SelfHandler.class).self) {
            final Symbol symbol = JavacContext.thisSym($this, type, env);
            ident.sym = symbol;
            type.type = symbol.type;
            return { symbol.type };
        }
        return Hook.Result.VOID;
    }
    
    @Hook
    private static Hook.Result adjustMethodReturnType(final Attr $this, final Symbol symbol, final Type qualifierType, final Name methodName, final List<Type> argTypes, final Type returnType)
            = Hook.Result.falseToVoid(isCallChain(symbol), qualifierType);
    
    Name self = name("self"), CallChainName = name(CallChain.class);
    
    @Override
    public void process(final Env<AttrContext> env, final JCTree tree, final JCTree owner, final boolean advance) {
        if (advance && tree instanceof JCTree.JCMethodDecl methodDecl)
            if (methodDecl.restype instanceof JCTree.JCIdent ident && ident.name == self) {
                if (methodDecl.mods.annotations.stream().noneMatch(annotation -> annotation.type.tsym.flatName() == CallChainName))
                    methodDecl.mods.annotations = methodDecl.mods.annotations.append(maker.Annotation(IdentQualifiedName(CallChain.class), List.nil()));
                if (methodDecl.body != null && !(methodDecl.body.stats.last() instanceof JCTree.JCReturn))
                    methodDecl.body.stats = methodDecl.body.stats.append(maker.Return(maker.Ident(names._this)));
            }
    }
    
    @Override
    public void attribTree(final JCTree tree, final Env<AttrContext> env) {
        if (tree instanceof JCTree.JCMethodInvocation invocation && !(HandlerMarker.attrContext()[-2] instanceof JCTree.JCExpressionStatement ||
                HandlerMarker.attrContext()[-1] instanceof OperatorOverloadingHandler.OperatorInvocation && HandlerMarker.attrContext()[-3] instanceof JCTree.JCExpressionStatement)) {
            final Symbol symbol = symbol(invocation.meth);
            if (symbol instanceof Symbol.MethodSymbol && noneMatch(symbol.flags_field, STATIC) && !(symbol.type instanceof Type.JCVoidType) && isCallChain(symbol)) {
                final Type caller = invocation.meth instanceof JCTree.JCFieldAccess access ? access.selected.type : thisSym(attr, tree, env).type;
                TreeTranslator.translate(Map.of(invocation, maker.TypeCast(caller, invocation)), TreeTranslator.upper(env, invocation));
            }
        }
    }
    
    public static boolean isCallChain(final Symbol symbol) = symbol?.getAnnotationMirrors().stream().anyMatch(compound -> compound.type.tsym.getQualifiedName().toString().equals(CallChain.class.getCanonicalName())) ?? false;
    
}
