package amadeus.maho.lang.javac.handler.base;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Check;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.tree.JCTree;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.javac.JavacContext;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public final class DelayedContext extends JavacContext {
    
    @Getter
    final LinkedList<Runnable> todos = { }, delayedImportCheck = { };
    
    boolean checkImportsResolvableDelayed = true;
    
    public void enterDone() {
        todos.forEach(Runnable::run);
        todos.clear();
        checkImportsResolvableDelayed = false;
        try {
            delayedImportCheck.forEach(Runnable::run);
            delayedImportCheck.clear();
        } finally { checkImportsResolvableDelayed = true; }
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)))
    private static void enterDone(final JavaCompiler $this) = instance(DelayedContext.class).enterDone();
    
    public void attributeDone() {
        todos.forEach(Runnable::run);
        todos.clear();
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)))
    private static void attribute(final JavaCompiler $this, final Queue<Env<AttrContext>> queue) = instance(DelayedContext.class).attributeDone();
    
    @Hook
    private static Hook.Result finalizeSingleScope(final Scope.ImportScope $this, final Scope impScope) = { impScope };
    
    @Hook
    private static Hook.Result checkImportsResolvable(final Check $this, final JCTree.JCCompilationUnit unit) {
        final DelayedContext instance = instance(DelayedContext.class);
        if (instance.checkImportsResolvableDelayed) {
            instance.delayedImportCheck += () -> $this.checkImportsResolvable(unit);
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
}
