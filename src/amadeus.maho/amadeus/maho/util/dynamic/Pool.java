package amadeus.maho.util.dynamic;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;

public interface Pool<K, V> extends Recycler<V>, Function<K, V> {
    
    @Getter
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    class Concurrent<K, V> implements Pool<K, V> {
        
        ConcurrentHashMap<K, ConcurrentLinkedQueue<V>> cache = { };
        
        Function<K, V> allocator;
        
        Function<V, K> key;
        
        @Override
        public V allocate(final K k) = cache.computeIfAbsent(k, _ -> new ConcurrentLinkedQueue<>()).poll() ?? allocator[k];
        
        @Override
        public void recycle(final V v) = cache.computeIfAbsent(key(v), _ -> new ConcurrentLinkedQueue<>()).offer(v);
        
        @Override
        public K key(final V v) = key[v];
        
    }
    
    V allocate(final K k);
    
    K key(V v);
    
    @Override
    void recycle(V v);
    
    @Override
    default V apply(final K k) = allocate(k);
    
    static Pool.Concurrent<Long, MemorySegment> createMemorySegmentPool() = { Arena.global()::allocate, MemorySegment::address };
    
    static Pool.Concurrent<Integer, ByteBuffer> createByteBufferPool() = { ByteBuffer::allocateDirect, ByteBuffer::capacity };
    
}
