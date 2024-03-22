package amadeus.maho.lang.javac.multithreaded.parallel;

import java.util.concurrent.CompletableFuture;

import com.sun.tools.javac.comp.Check;
import com.sun.tools.javac.comp.Modules;
import com.sun.tools.javac.comp.TransTypes;
import com.sun.tools.javac.jvm.ClassWriter;
import com.sun.tools.javac.jvm.JNIWriter;
import com.sun.tools.javac.util.Context;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.javac.multithreaded.MultiThreadedContext;
import amadeus.maho.lang.javac.multithreaded.concurrent.ConcurrentTransTypes;
import amadeus.maho.lang.javac.multithreaded.dispatch.DispatchCompiler;
import amadeus.maho.lang.javac.multithreaded.dispatch.DispatchContext;
import amadeus.maho.lang.javac.multithreaded.concurrent.ConcurrentCheck;
import amadeus.maho.util.control.Interrupt;
import amadeus.maho.util.runtime.ObjectHelper;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
public class ParallelContext extends Context implements MultiThreadedContext {
    
    @Getter
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class Worker extends Thread {
        
        ParallelContext context;
        
        @Override
        public void run() {
            context.copySharedComponents();
            final DispatchCompiler dispatchCompiler = DispatchCompiler.instance(context.context);
            final ParallelCompiler parallelCompiler = ParallelCompiler.instance(context);
            context.sharedComponentsConfiguration();
            context.initialization.complete(null);
            while (!dispatchCompiler.shutdown())
                Interrupt.doInterruptible(() -> dispatchCompiler.queue.take().accept(parallelCompiler), () -> dispatchCompiler.barrier()?.cross(parallelCompiler));
        }
        
    }
    
    DispatchContext context;
    
    {
        put((Privilege) Check.checkKey, (Factory<Check>) ConcurrentCheck::new);
        put((Privilege) TransTypes.transTypesKey, (Factory<TransTypes>) ConcurrentTransTypes::new);
    }
    
    CompletableFuture<Void> initialization = { };
    
    Worker worker = { STR."ParallelContextWorker\{context.workerIndex().getAndIncrement()}", this };
    
    protected void copySharedComponents() = context.sharedKeys().forEach((key, shared) -> {
        if (shared)
            super.put((Key) key, ObjectHelper.requireNonNull(context.get(key)));
    });
    
    { worker.start(); }
    
    public void sharedComponentsConfiguration() {
        final Modules modules = Modules.instance(this);
        final boolean multiModuleMode = modules.multiModuleMode;
        JNIWriter.instance(this).multiModuleMode = multiModuleMode;
        ClassWriter.instance(this).multiModuleMode = multiModuleMode;
    }
    
    @Override
    public <T> Key<T> key(final Class<T> clazz) = context.key(clazz);
    
    @Override
    public synchronized <T> void put(final Key<T> key, final T data) = super.put(key, data);
    
    @Override
    public synchronized <T> void put(final Key<T> key, final Factory<T> fac) = super.put(key, fac);
    
    @Override
    public synchronized <T> T get(final Key<T> key) = context.isolated(key) ? super.get(key) : context.get(key);
    
    public void interrupt() = worker.interrupt();
    
}
