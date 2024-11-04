package amadeus.maho.lang.javac.multithreaded.concurrent;

import java.util.Queue;
import java.util.function.Consumer;

import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.util.Context;

import amadeus.maho.lang.javac.handler.base.DelayedContext;
import amadeus.maho.lang.javac.multithreaded.dispatch.DispatchCompiler;

public class ConcurrentDelayedContext extends DelayedContext {
    
    private void dispatch(final String name, final DispatchCompiler compiler, final Queue<Consumer<Context>> queue) {
        compiler.dispatch(name, queue, (parallelCompiler, todo) -> todo[parallelCompiler.context]);
        queue.clear();
    }
    
    @Override
    public synchronized void beforeEnterDone(final JavaCompiler compiler) {
        final DispatchCompiler dispatchCompiler = (DispatchCompiler) compiler;
        dispatch("beforeEnterDone-todos", dispatchCompiler, todos());
        checkImportsResolvableDelayed = false;
        dispatch("beforeEnterDone-delayedImportCheck", dispatchCompiler, delayedImportCheck());
    }
    
    @Override
    public synchronized void beforeAttributeDone(final JavaCompiler compiler) {
        final DispatchCompiler dispatchCompiler = (DispatchCompiler) compiler;
        dispatch("beforeAttributeDone-todos", dispatchCompiler, todos());
    }
    
}
