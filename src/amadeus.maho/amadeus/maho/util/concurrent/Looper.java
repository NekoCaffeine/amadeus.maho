package amadeus.maho.util.concurrent;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Builder;
import amadeus.maho.lang.Extension;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.reference.Mutable;
import amadeus.maho.util.control.Interrupt;
import amadeus.maho.util.event.Event;
import amadeus.maho.util.event.EventBus;
import amadeus.maho.util.function.FunctionHelper;
import amadeus.maho.util.runtime.DebugHelper;

import static amadeus.maho.util.function.FunctionHelper.nothing;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Looper implements Runnable {
    
    public sealed interface LoopEvent {
        
        Looper looper();
        
        @Getter
        @RequiredArgsConstructor
        @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
        final class Pre extends Event.Cancellable implements LoopEvent {
            
            Looper looper;
            
        }
        
        @Getter
        @RequiredArgsConstructor
        @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
        final class Post extends Event implements LoopEvent {
            
            Looper looper;
            
        }
        
    }
    
    @Getter
    @RequiredArgsConstructor(on = @Builder)
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class WithBus extends Looper {
        
        Supplier<EventBus> bus;
        
        LoopEvent.Pre pre = { this };
        
        LoopEvent.Post post = { this };
        
        @Override
        protected void tick() {
            if ((bus().get() >> pre()).cancel())
                pre().cancel(false);
            else {
                super.tick();
                bus().get() >> post();
            }
        }
        
    }
    
    protected CountDownLatch latch() = { 1 };
    
    CountDownLatch ready = latch(), state = latch(), end = latch();
    
    Runnable init, loop, over;
    
    LongSupplier sync;
    
    BooleanSupplier checker;
    
    Consumer<Throwable> handler;
    
    TaskQueue preQueue = TaskQueue.of(), postQueue = TaskQueue.of();
    
    @Setter(AccessLevel.PRIVATE)
    @Mutable
    @Nullable Thread context;
    
    @Builder
    public Looper(
            final @Nullable Runnable init,
            final @Nullable BooleanSupplier checker,
            final @Nullable Runnable loop,
            final @Nullable Runnable over,
            final @Nullable LongSupplier sync,
            final @Nullable Consumer<Throwable> handler
            ) {
        this.init = init ?? nothing();
        this.checker = checker ?? () -> true;
        this.loop = loop ?? nothing();
        this.over = over ?? nothing();
        this.sync = sync ?? () -> 0L;
        this.handler = handler ?? FunctionHelper::rethrow;
    }
    
    public TaskQueue taskQueue(final boolean pre) = pre ? preQueue() : postQueue();
    
    @Extension.Operator(">>")
    public self addPreTask(final Runnable task) = preQueue().add(task);
    
    @Extension.Operator("<<")
    public self addPostTask(final Runnable task) = postQueue().add(task);
    
    @SneakyThrows
    @Extension.Operator("*")
    public <T> @Nullable T handshake(final Supplier<T> supplier) {
        final @Nullable Thread context = context();
        if (context == null || context == Thread.currentThread())
            return supplier.get();
        final CountDownLatch latch = { 1 };
        final CompletableFuture p_result[] = { null };
        addPreTask(() -> {
            try {
                p_result[0] = CompletableFuture.completedFuture(supplier.get());
            } catch (final Throwable throwable) {
                p_result[0] = CompletableFuture.failedFuture(throwable);
            } finally { latch.countDown(); }
        });
        latch.await();
        return (T) p_result[0].get();
    }
    
    protected synchronized void markContext() {
        if (context() != null)
            throw new IllegalThreadStateException(STR."The loop is being executed by other threads: \{context()}");
        context(Thread.currentThread());
    }
    
    protected void tick() {
        final long sync = sync().getAsLong();
        if (sync > 0L) {
            final long record = System.currentTimeMillis();
            preQueue().work();
            loop().run();
            postQueue().work();
            final long sleep = sync - (System.currentTimeMillis() - record);
            if (sleep > 0L)
                Interrupt.doInterruptible(() -> Thread.sleep(sleep));
        } else {
            preQueue().work();
            loop().run();
            postQueue().work();
        }
    }
    
    @Override
    public void run() {
        markContext();
        handle(() -> {
            init().run();
            ready().countDown();
            while (state().getCount() > 0 && checker().getAsBoolean())
                tick();
        }, this::doFinally);
    }
    
    @SneakyThrows
    protected void handle(final Runnable runnable, final Runnable doFinally) {
        try { ~runnable; } catch (final Throwable throwable) {
            try { handler().accept(throwable); } catch (final Throwable rethrow) { throw DebugHelper.breakpointBeforeThrow(rethrow); } finally { ~doFinally; }
        } finally { ~doFinally; }
    }
    
    @SneakyThrows
    protected synchronized void doFinally() = handle(() -> {
        ready().countDown();
        state().countDown();
        if (end().getCount() > 0)
            over().run();
    }, () -> {
        end().countDown();
        context(null);
    });
    
    public void stop() {
        state().countDown();
        if (context() != null)
            Interrupt.doInterruptible(end()::await);
    }
    
}
