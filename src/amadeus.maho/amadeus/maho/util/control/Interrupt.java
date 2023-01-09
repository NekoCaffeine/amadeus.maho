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
    
    static void doInterruptible(final InterruptibleRunnable runnable, final Runnable interruptCallback = () -> { }) {
        try {
            runnable.run();
        } catch (final InterruptedException e) {
            Thread.interrupted();
            interruptCallback.run();
        }
    }
    
    static <T> @Nullable T getInterruptible(final InterruptibleGetter<T> getter, final Supplier<T> interruptGetter = () -> null) {
        try {
            return getter.get();
        } catch (final InterruptedException e) {
            Thread.interrupted();
            return interruptGetter.get();
        }
    }
    
    @SneakyThrows
    static void doUninterruptible(final Runnable runnable) {
        try {
            runnable.run();
        } catch (final InterruptedException e) {
            Thread.interrupted();
            throw new InterruptedIOException();
        }
    }
    
    @SneakyThrows
    static <T> T getUninterruptible(final Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (final InterruptedException e) {
            Thread.interrupted();
            throw new InterruptedIOException();
        }
    }
    
}
