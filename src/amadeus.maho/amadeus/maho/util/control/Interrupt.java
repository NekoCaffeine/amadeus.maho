package amadeus.maho.util.control;

import java.io.InterruptedIOException;
import java.util.function.Supplier;

import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;

public interface Interrupt {
    
    interface InterruptibleRunnable {
        
        void run() throws InterruptedException;
        
    }
    
    interface InterruptibleGetter<T> {
        
        @Nullable T get() throws InterruptedException;
        
    }
    
    static void doInterruptible(final InterruptibleRunnable runnable, final @Nullable Runnable interruptCallback = null) {
        try {
            runnable.run();
        } catch (final InterruptedException e) {
            Thread.interrupted();
            interruptCallback.run();
        }
    }
    
    static <T> @Nullable T getInterruptible(final InterruptibleGetter<T> getter, final @Nullable Supplier<T> interruptGetter = null) {
        try {
            return getter.get();
        } catch (final InterruptedException e) {
            Thread.interrupted();
            return interruptGetter?.get() ?? null;
        }
    }
    
    @SneakyThrows
    static void doUninterruptible(final InterruptibleRunnable runnable) {
        while (true)
            try {
                runnable.run();
                return;
            } catch (final InterruptedException e) { Thread.interrupted(); }
    }
    
    @SneakyThrows
    static <T> T getUninterruptible(final InterruptibleGetter<T> supplier) {
        while (true)
            try {
                return supplier.get();
            } catch (final InterruptedException e) { Thread.interrupted(); }
    }
    
}
