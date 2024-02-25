package amadeus.maho.util.control;

import java.util.Deque;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.inspection.Nullable;

@ToString
@FieldDefaults(level = AccessLevel.PROTECTED)
public class SwitchPoint<T> {
    
    public static class Safety extends SwitchPoint<Safety.State> {
        
        public enum State {SAFE, UNSAFE}
        
        public Handle ensureSafe() = ensure(State.SAFE);
        
        public Handle ensureUnsafe() = ensure(State.UNSAFE);
        
    }
    
    public class Handle implements AutoCloseable {
        
        @Override
        public void close() {
            lock.lock();
            try {
                deque -= this;
                if (deque.isEmpty()) {
                    context = null;
                    condition.signalAll();
                }
            } finally { lock.unlock(); }
        }
        
    }
    
    volatile @Nullable T context;
    
    final ConcurrentLinkedQueue<Handle> deque = { };
    
    final Lock lock = new ReentrantLock();
    
    final Condition condition = lock.newCondition();
    
    public void runWhen(final T context, final Runnable runnable) { try (final var ignored = ensure(context)) { runnable.run(); } }
    
    public Handle ensure(final T context) {
        lock.lock();
        try {
            while (!unsafeCheck(context))
                condition.awaitUninterruptibly();
            if (this.context == null)
                this.context = context;
            final Handle result = { };
            deque.add(result);
            return result;
        } finally { lock.unlock(); }
    }
    
    protected boolean unsafeCheck(final T context) = this.context == null || this.context == context;
    
}
