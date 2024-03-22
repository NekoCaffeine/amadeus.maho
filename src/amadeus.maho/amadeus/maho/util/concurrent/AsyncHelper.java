package amadeus.maho.util.concurrent;

import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import amadeus.maho.lang.Getter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.inspection.TestOnly;
import amadeus.maho.util.runtime.ObjectHelper;
import amadeus.maho.util.throwable.RetryException;

public interface AsyncHelper {
    
    @Getter
    ConcurrentWeakIdentityHashMap<Thread, Executor> executorContext = { };
    
    static Supplier<Executor> defaultExecutorGetter() = ForkJoinPool::commonPool;
    
    static Executor defaultExecutor() = defaultExecutorGetter().get();
    
    @TestOnly("Unable to throw timeout exception in timeout")
    static Executor selfExecutor() = Runnable::run;
    
    static Executor newThreadExecutor(final String name, final boolean daemon = false, final @Nullable ThreadGroup group = null, final boolean inheritThreadLocals = true, final long stackSize = 0L)
            = task -> new Thread(group, task, name, stackSize, inheritThreadLocals).let(it -> it.setDaemon(daemon)).start();
    
    static Executor contextExecutor(final Thread thread) = executorContext()[thread] ?? defaultExecutor();
    
    static void contextExecutor(final Thread thread, final @Nullable Executor executor) {
        if (executor != null)
            executorContext.put(thread, executor);
        else
            executorContext.remove(thread);
    }
    
    static Executor contextExecutor() = contextExecutor(Thread.currentThread());
    
    static void contextExecutor(final @Nullable Executor executor) = contextExecutor(Thread.currentThread(), executor);
    
    static Throwable resolveExecutionException(final Throwable throwable) = throwable instanceof ExecutionException exception ? exception.getCause() : throwable;
    
    @SneakyThrows
    static CompletableFuture<Void> async(final Runnable task, final @Nullable Executor executor = contextExecutor(), final int retry, final long timeout, final TimeUnit unit) = CompletableFuture.runAsync(() -> {
        int count = retry;
        final LinkedList<Throwable> throwables = { };
        do
            try {
                await(timeout, unit, async(task, executor));
                return;
            } catch (final Throwable throwable) { throwables += throwable; }
        while (--count > -1);
        throw new RetryException(throwables);
    }, executor != null ? executor : defaultExecutor());
    
    @SneakyThrows
    static CompletableFuture<Void> async(final Runnable task, final @Nullable Executor executor = contextExecutor(), final int retry) = CompletableFuture.runAsync(() -> {
        int count = retry;
        final LinkedList<Throwable> throwables = { };
        do
            try {
                task.run();
                return;
            } catch (final Throwable throwable) { throwables += throwable; }
        while (--count > -1);
        throw new RetryException(throwables);
    }, executor != null ? executor : defaultExecutor());
    
    static CompletableFuture<Void> async(final Runnable task, final @Nullable Executor executor = contextExecutor()) = CompletableFuture.runAsync(task, executor != null ? executor : defaultExecutor());
    
    @SneakyThrows
    static <T> CompletableFuture<T> async(final Supplier<T> task, final @Nullable Executor executor = contextExecutor(), final int retry, final long timeout, final TimeUnit unit)
            = CompletableFuture.supplyAsync(() -> {
        int count = retry;
        final LinkedList<Throwable> throwables = { };
        do
            try {
                return await(timeout, unit, async(task, executor));
            } catch (final Throwable throwable) { throwables += throwable; }
        while (--count > -1);
        throw new RetryException(throwables);
    }, executor != null ? executor : defaultExecutor());
    
    @SneakyThrows
    static <T> CompletableFuture<T> async(final Supplier<T> task, final @Nullable Executor executor = contextExecutor(), final int retry)
            = CompletableFuture.supplyAsync(() -> {
        int count = retry;
        final LinkedList<Throwable> throwables = { };
        do
            try {
                return task.get();
            } catch (final Throwable throwable) { throwables += throwable; }
        while (--count > -1);
        throw new RetryException(throwables);
    }, executor != null ? executor : defaultExecutor());
    
    static <T> CompletableFuture<T> async(final Supplier<T> task, final @Nullable Executor executor = contextExecutor()) = CompletableFuture.supplyAsync(task, executor != null ? executor : defaultExecutor());
    
    static <T> CompletableFuture<T> completed(final @Nullable T result = null) = CompletableFuture.completedFuture(result);
    
    static <T> CompletableFuture<T> failed(final Throwable throwable) = CompletableFuture.failedFuture(throwable);
    
    @SneakyThrows
    static <T> T await(final Future<T> future) = future.get();
    
    @SneakyThrows
    static <T> T await(final long timeout, final TimeUnit unit = TimeUnit.MILLISECONDS, final Future<T> future) = future.get(timeout, unit);
    
    @SneakyThrows
    static void await(final CompletableFuture<?>... futures) = CompletableFuture.allOf(futures).get();
    
    @SneakyThrows
    static void await(final long timeout, final TimeUnit unit = TimeUnit.MILLISECONDS, final CompletableFuture<?>... futures) = CompletableFuture.allOf(futures).get(timeout, unit);
    
    static void await(final Stream<? extends CompletableFuture<?>> futures) = await(futures.toArray(CompletableFuture[]::new));
    
    static void await(final Collection<? extends CompletableFuture<?>> futures) = await(futures.toArray(CompletableFuture[]::new));
    
    static void await(final long timeout, final TimeUnit unit = TimeUnit.MILLISECONDS, final Stream<CompletableFuture<?>> futures) = await(timeout, unit, futures.toArray(CompletableFuture[]::new));
    
    static void awaitRecursion(final Consumer<ConcurrentLinkedQueue<CompletableFuture<Void>>> consumer) {
        final ConcurrentLinkedQueue<CompletableFuture<Void>> asyncTasks = { };
        consumer[asyncTasks];
        asyncTasks.forEach(AsyncHelper::await);
    }
    
}
