package amadeus.maho.util.concurrent;

import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import amadeus.maho.lang.inspection.Nullable;

import static amadeus.maho.util.runtime.ArrayHelper.*;
import static java.lang.Math.min;

public interface Synchronicity {
    
    record Frame(Thread owner = Thread.currentThread(), int eventId, SyncPoint points[]) {
        
        public static final Frame SINGLE_POINT = { -1, new SyncPoint[0] };
        
    }
    
    interface SyncPoint {
        
        VarHandle handle();
        
        default @Nullable Frame lock(final Frame frame) {
            final VarHandle handle = handle();
            @Nullable Frame exchange, cache = null;
            while ((exchange = (Frame) handle.compareAndExchange(this, null, frame)) != null) {
                if (exchange != cache) {
                    if (exchange == frame) // Re-lock same frame
                        return exchange;
                    final Thread current = Thread.currentThread();
                    if (exchange.owner == current) {
                        // Re-enter the sync point and inherit the lock of the sync point held in the previous frame.
                        final Frame next = { current, min(exchange.eventId(), frame.eventId()), addAll(exchange.points(), frame.points()) };
                        while (!handle.compareAndSet(this, exchange, next))
                            Thread.onSpinWait();
                        return exchange;
                    }
                    if (exchange != Frame.SINGLE_POINT && frame.eventId() < exchange.eventId()) { // Conflict checking is performed only for earlier events.
                        final SyncPoint pointsEx[] = exchange.points(), points[] = frame.points();
                        final int indexEx = indexOfRef(pointsEx, this), index = indexOfRef(frame.points(), this);
                        // If this is an attempt to get a lock on the first synchronization point, then there is no lock on the frame that can cause a deadlock conflict.
                        // Deadlock conflicts are also not possible if the exchange target gets all locks.
                        if (index != 0) {
                            for (int k = indexEx + 1; k < pointsEx.length; k++) {
                                final SyncPoint pointEx = pointsEx[k];
                                for (int i = 0; i < index; i++)
                                    // A deadlock conflict occurs and the exchange target needs the lock of the sync point already acquired in the current frame.
                                    // Since this conflict causes blocking of the exchange target thread, it is sufficient to assume that the current thread has obtained a lock for this sync point.
                                    if (pointEx == points[i]) {
                                        while (!handle.compareAndSet(this, exchange, frame))
                                            Thread.onSpinWait();
                                        return exchange;
                                    }
                            }
                        }
                    }
                    cache = exchange; // If the result of the next exchange is consistent with the cache, then no conflict checking is performed.
                }
                Thread.onSpinWait(); // Lock competition fails, spin occurs.
            }
            return null;
        }
        
        default void unlock(final @Nullable Frame exchange) = handle().setVolatile(this, exchange);
        
    }
    
    AtomicInteger counter();
    
    default int nextEventId() = counter().incrementAndGet();
    
    default void observation(final int eventId = nextEventId(), final Runnable event, final SyncPoint... points) {
        switch (points.length) {
            case 0  -> event.run();
            case 1  -> {
                final var point = points[0];
                final @Nullable Frame exchange = point.lock(Frame.SINGLE_POINT);
                try { event.run(); } finally { point.unlock(exchange); }
            }
            default -> {
                final Frame frame = { eventId, points }, exchange[] = new Frame[points.length];
                try {
                    for (int index = 0; index < points.length; index++)
                        exchange[index] = points[index].lock(frame);
                    event.run();
                } finally {
                    for (int index = 0; index < points.length; index++)
                        points[index].unlock(exchange[index]);
                }
            }
        }
    }
    
    default <T> T observation(final int eventId = nextEventId(), final Supplier<T> event, final SyncPoint... points) = (T) new Object[]{ null }.let(it -> observation(eventId, () -> it[0] = event.get(), points));
    
}
