package amadeus.maho.lang.javac.multithreaded.concurrent;

import java.util.Queue;
import java.util.function.Consumer;

import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.util.Context;

import amadeus.maho.lang.javac.handler.base.DelayedContext;
import amadeus.maho.lang.javac.multithreaded.dispatch.DispatchCompiler;
import amadeus.maho.util.runtime.DebugHelper;

import static amadeus.maho.util.concurrent.AsyncHelper.await;

public class ConcurrentDelayedContext extends DelayedContext {
    
    private void dispatch(final DispatchCompiler compiler, final Queue<Consumer<Context>> queue) {
        await(compiler.dispatch(queue.stream(), (parallelCompiler, todo) -> todo[parallelCompiler.context]));
        queue.clear();
    }
    
    @Override
    public synchronized void beforeEnterDone(final JavaCompiler compiler) {
        if (compiler instanceof DispatchCompiler dispatchCompiler) {
            dispatch(dispatchCompiler, todos());
            checkImportsResolvableDelayed = false;
            dispatch(dispatchCompiler, delayedImportCheck());
        } else
            DebugHelper.breakpoint();
    }
    
    @Override
    public synchronized void beforeAttributeDone(final JavaCompiler compiler) {
        if (compiler instanceof DispatchCompiler dispatchCompiler)
            dispatch(dispatchCompiler, todos());
        else
            DebugHelper.breakpoint();
    }
    
}
