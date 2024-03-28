package amadeus.maho.util.concurrent;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;
import java.util.stream.Stream;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Extension;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.reference.Mutable;
import amadeus.maho.util.control.Interrupt;

import static amadeus.maho.util.concurrent.AsyncHelper.async;

@SneakyThrows
@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LazyWorker implements Executor, Runnable {
    
    @Setter(AccessLevel.PRIVATE)
    @Mutable
    @Nullable Thread context;
    
    LinkedBlockingQueue<Runnable> queue = { };
    
    @Setter
    @Mutable
    boolean closed = false;
    
    protected synchronized void markContext() {
        if (context() != null)
            throw new IllegalThreadStateException(STR."The loop is being executed by other threads: \{context()}");
        context(Thread.currentThread());
    }
    
    @Override
    public void run() {
        markContext();
        while (!closed())
            Interrupt.doInterruptible(() -> Stream.generate(queue()::take).forEach(Runnable::run));
    }
    
    @Override
    public void execute(final Runnable command) {
        final @Nullable Thread context = context();
        if (context == null || Thread.currentThread() == context)
            command.run();
        else
            queue() += command;
    }
    
    @Extension.Operator("^")
    public void sync(final Runnable runnable) = async(runnable, this).get();
    
    @Extension.Operator("^")
    public <T> @Nullable T sync(final Supplier<T> supplier) = async(supplier, this).get();
    
}
