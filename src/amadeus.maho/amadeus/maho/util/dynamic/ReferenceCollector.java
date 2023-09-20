package amadeus.maho.util.dynamic;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.function.Consumer;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.concurrent.ConcurrentWeakIdentityHashMap;
import amadeus.maho.util.concurrent.ConcurrentWeakIdentityHashSet;

public interface ReferenceCollector {
    
    interface Collectible<T> {
        
        ReferenceQueue<T> referenceQueue();
        
        void collect(Reference<? extends T> reference);
        
        default boolean managed() = false;
        
    }
    
    interface Manageable<T> extends Collectible<T> {
        
        @Override
        default boolean managed() = true;
        
    }
    
    @FieldDefaults(level = AccessLevel.PROTECTED)
    class Base implements ReferenceCollector, Runnable {
        
        final ConcurrentWeakIdentityHashMap.Managed<Collectible<?>, Boolean> queue = { };
        
        { manage(queue); }
        
        @Getter
        @Nullable Thread looper;
        
        @Override
        public <C extends Manageable<T>, T> C manage(final C manageable) {
            queue[manageable] = Boolean.TRUE;
            return manageable;
        }
        
        @Override
        public void collect() = queue.keySet().forEach(ReferenceCollector::collect);
        
        @Override
        public void run() {
            // noinspection InfiniteLoopStatement
            while (true)
                try {
                    collect();
                    Thread.sleep(10);
                } catch (final InterruptedException e) { Thread.interrupted(); }
        }
        
        public synchronized void start() throws IllegalStateException {
            if (looper() != null)
                throw new IllegalStateException();
            looper = createLooper();
        }
        
        protected Thread createLooper() {
            final Thread looper = { this, "ReferenceCollector-Looper" };
            looper.setDaemon(true);
            looper.start();
            return looper;
        }
        
    }
    
    <C extends Manageable<T>, T> C manage(C collectible);
    
    void collect();
    
    default <K, V> ConcurrentWeakIdentityHashMap.Managed<K, V> makeManagedConcurrentWeakIdentityHashMap(final int initialCapacity = 1, final float loadFactor = 0.75F, final int concurrencyLevel = 1)
            = manage(new ConcurrentWeakIdentityHashMap.Managed<>(initialCapacity, loadFactor, concurrencyLevel));
    
    default <K> ConcurrentWeakIdentityHashSet<K> makeManagedConcurrentWeakIdentityHashSet(final int initialCapacity = 1, final float loadFactor = 0.75F, final int concurrencyLevel = 1)
            = { makeManagedConcurrentWeakIdentityHashMap(initialCapacity, loadFactor, concurrencyLevel) };
    
    static <T> void collect(final ReferenceQueue<T> referenceQueue, final Consumer<? super Reference<? extends T>> consumer) {
        @Nullable Reference<? extends T> reference;
        while ((reference = referenceQueue.poll()) != null)
            consumer.accept(reference);
    }
    
    static <T> void collect(final @Nullable Collectible<T> collectible) {
        if (collectible != null) {
            final ReferenceQueue<T> referenceQueue = collectible.referenceQueue();
            @Nullable Reference<? extends T> reference;
            while ((reference = referenceQueue.poll()) != null)
                collectible.collect(reference);
        }
    }
    
}
