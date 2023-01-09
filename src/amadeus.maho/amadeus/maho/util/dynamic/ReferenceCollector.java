package amadeus.maho.util.dynamic;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.function.Consumer;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.concurrent.ConcurrentWeakIdentityHashMap;

public interface ReferenceCollector {
    
    interface Collectible<T> {
        
        ReferenceQueue<T> referenceQueue();
        
        void collect(Reference<? extends T> reference);
        
    }
    
    @FieldDefaults(level = AccessLevel.PROTECTED)
    class Base implements ReferenceCollector, Runnable {
        
        ConcurrentWeakIdentityHashMap.Managed<Collectible<?>, Object> collectibles = { };
        
        { manage(collectibles); }
        
        @Getter
        @Nullable Thread looper;
        
        @Override
        public <T> void manage(final Collectible<T> collectible) = collectibles[collectible] = Boolean.TRUE;
        
        @Override
        public void collect() = collectibles.keySet().forEach(ReferenceCollector::collect);
        
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
            looper = { this, "ReferenceCollector-Looper" };
            looper().let(it -> it.setDaemon(true)).start();
        }
        
    }
    
    <T> void manage(Collectible<T> collectible);
    
    void collect();
    
    default <K, V> ConcurrentWeakIdentityHashMap.Managed<K, V> makeManagedConcurrentWeakIdentityHashMap(final int initialCapacity = 1, final float loadFactor = 0.75F, final int concurrencyLevel = 1)
            = new ConcurrentWeakIdentityHashMap.Managed<K, V>(initialCapacity, loadFactor, concurrencyLevel).let(this::manage);
    
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
