package amadeus.maho.lang.javac.handler.base;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Check;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.javac.multithreaded.SharedComponent;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;

import static amadeus.maho.lang.javac.JavacContext.instance;

@TransformProvider
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED)
public class DelayedContext implements SharedComponent {
    
    @Getter
    final ConcurrentLinkedQueue<Consumer<Context>> todos = { }, delayedImportCheck = { };
    
    boolean checkImportsResolvableDelayed = true;
    
    public DelayedContext(final Context context) { }
    
    private void process(final Context context, final Queue<Consumer<Context>> queue) {
        queue.forEach(consumer -> consumer[context]);
        queue.clear();
    }
    
    public void beforeEnterDone(final JavaCompiler compiler) {
        final Context context = (Privilege) compiler.context;
        process(context, todos());
        checkImportsResolvableDelayed = false;
        process(context, delayedImportCheck());
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)))
    private static void enterDone(final JavaCompiler $this) = instance(DelayedContext.class).beforeEnterDone($this);
    
    public void beforeAttributeDone(final JavaCompiler compiler) = process((Privilege) compiler.context, todos());
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)))
    private static void attribute(final JavaCompiler $this, final Queue<Env<AttrContext>> queue) = instance(DelayedContext.class).beforeAttributeDone($this);
    
    @Hook
    private static Hook.Result finalizeSingleScope(final Scope.ImportScope $this, final Scope impScope) = { impScope };
    
    @Hook
    private static Hook.Result checkImportsResolvable(final Check $this, final JCTree.JCCompilationUnit unit) {
        final DelayedContext instance = instance(DelayedContext.class);
        if (instance.checkImportsResolvableDelayed) {
            instance.delayedImportCheck += context -> Check.instance(context).checkImportsResolvable(unit);
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
}
