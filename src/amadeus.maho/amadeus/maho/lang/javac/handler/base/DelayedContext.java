package amadeus.maho.lang.javac.handler.base;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Check;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;

import amadeus.maho.lang.Getter;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.javac.JavacContext;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.dynamic.InvokeContext;

@TransformProvider
public final class DelayedContext {
    
    public static DelayedContext instance(final Context context) {
        @Nullable DelayedContext instance = context.get(DelayedContext.class);
        if (instance == null)
            instance = { context };
        return instance;
    }
    
    public DelayedContext(final Context context) = context.put(DelayedContext.class, this);
    
    @Getter
    ConcurrentLinkedQueue<Runnable> todos = { }, delayedImportCheck = { };
    
    private static final ThreadLocal<Boolean> checkImportsResolvableDelayed = ThreadLocal.withInitial(() -> true);
    
    private static final InvokeContext delayedCheckInvokeContext = checkImportsResolvableDelayed.fixedInvokeContext(false, true);
    
    private void enterDone() {
        todos.forEach(Runnable::run);
        todos.clear();
        delayedCheckInvokeContext ^ () -> {
            delayedImportCheck.forEach(Runnable::run);
            delayedImportCheck.clear();
        };
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)))
    private static void enterDone(final JavaCompiler $this) = JavacContext.instance(DelayedContext.class).enterDone();
    
    private void attributeDone() {
        todos.forEach(Runnable::run);
        todos.clear();
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)))
    private static void attribute(final JavaCompiler $this, final Queue<Env<AttrContext>> queue) = JavacContext.instance(DelayedContext.class).attributeDone();
    
    @Hook
    private static Hook.Result finalizeSingleScope(final Scope.ImportScope $this, final Scope impScope) = { impScope };
    
    @Hook
    private static Hook.Result checkImportsResolvable(final Check $this, final JCTree.JCCompilationUnit unit) {
        if (checkImportsResolvableDelayed.get()) {
            JavacContext.instance(DelayedContext.class).delayedImportCheck += () -> $this.checkImportsResolvable(unit);
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
}
