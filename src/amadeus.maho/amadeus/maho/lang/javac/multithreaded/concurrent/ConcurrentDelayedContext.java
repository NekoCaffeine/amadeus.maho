package amadeus.maho.lang.javac.multithreaded.concurrent;

import java.util.Queue;
import java.util.function.Consumer;

import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.util.Context;

import amadeus.maho.lang.javac.handler.base.DelayedContext;
import amadeus.maho.lang.javac.multithreaded.dispatch.DispatchCompiler;

import static amadeus.maho.util.concurrent.AsyncHelper.await;

public class ConcurrentDelayedContext extends DelayedContext {
    
    private void dispatch(final DispatchCompiler compiler, final Queue<Consumer<Context>> queue) {
        await(compiler.dispatch(queue.stream(), (parallelCompiler, todo) -> todo[parallelCompiler.context]));
        queue.clear();
    }
    
    @Override
    public synchronized void beforeEnterDone(final JavaCompiler compiler) {
        final DispatchCompiler dispatchCompiler = (DispatchCompiler) compiler;
        dispatch(dispatchCompiler, todos());
        checkImportsResolvableDelayed = false;
        dispatch(dispatchCompiler, delayedImportCheck());
    }
    
    @Override
    public synchronized void beforeAttributeDone(final JavaCompiler compiler) {
        final DispatchCompiler dispatchCompiler = (DispatchCompiler) compiler;
        dispatch(dispatchCompiler, todos());
    }
    
}
