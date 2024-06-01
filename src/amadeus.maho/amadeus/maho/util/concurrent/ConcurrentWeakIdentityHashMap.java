package amadeus.maho.util.concurrent;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.dynamic.ReferenceCollector;
import amadeus.maho.util.runtime.ObjectHelper;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
public class ConcurrentWeakIdentityHashMap<K, V> extends AbstractMap<K, V> implements ConcurrentMap<K, V>, ReferenceCollector.Collectible<K> {
    
    @NoArgsConstructor
    public static class Managed<K, V> extends ConcurrentWeakIdentityHashMap<K, V> implements ReferenceCollector.Manageable<K> {
        
        @Override
        public ConcurrentHashMap<Key<K>, V> purgeKeys() = map;
        
    }
    
    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    protected static class Key<K> extends WeakReference<K> {
        
        int hashCode;
        
        public Key(final K key, final @Nullable ReferenceQueue<? super K> queue = null) {
            super(ObjectHelper.requireNonNull(key), queue);
            hashCode = System.identityHashCode(key);
        }
        
        @Override
        public boolean equals(final Object object) {
            if (this == object)
                return true;
            final @Nullable K value = get();
            return value != null && object instanceof Key key && key.get() == value;
        }
        
    }
    
    protected class EntrySet extends AbstractSet<Map.Entry<K, V>> {
        
        @NoArgsConstructor
        protected class Entry extends AbstractMap.SimpleEntry<K, V> {
            
            @Override
            public V setValue(final V value) {
                put(getKey(), value);
                return super.setValue(value);
            }
            
            @Override
            public boolean equals(final Object object) = object instanceof Map.Entry<?, ?> entry && getKey() == entry.getKey() && getValue() == entry.getValue();
            
            @Override
            public int hashCode() = System.identityHashCode(this) ^ System.identityHashCode(getValue());
            
        }
        
        @RequiredArgsConstructor
        @FieldDefaults(level = AccessLevel.PROTECTED)
        protected class EntryIterator implements Iterator<Map.Entry<K, V>> {
            
            final Iterator<Map.Entry<Key<K>, V>> iterator;
            
            @Nullable Entry nextEntry;
            
            @Override
            public boolean hasNext() {
                if (nextEntry != null)
                    return true;
                while (iterator.hasNext()) {
                    final Map.Entry<Key<K>, V> entry = iterator.next();
                    final @Nullable K key = entry.getKey().get();
                    if (key != null) {
                        nextEntry = { key, entry.getValue() };
                        return true;
                    } else
                        iterator.remove();
                }
                return false;
            }
            
            @Override
            public Map.Entry<K, V> next() {
                if (!hasNext())
                    throw new NoSuchElementException();
                try { return nextEntry; } finally { nextEntry = null; }
            }
            
            @Override
            public void remove() {
                iterator.remove();
                nextEntry = null;
            }
            
        }
        
        @Override
        public EntryIterator iterator() = { map.entrySet().iterator() };
        
        @Override
        public void clear() = ConcurrentWeakIdentityHashMap.this.clear();
        
        @Override
        public int size() = ConcurrentWeakIdentityHashMap.this.size();
        
        @Override
        public boolean isEmpty() = ConcurrentWeakIdentityHashMap.this.isEmpty();
        
        @Override
        public boolean contains(final Object object) = object instanceof Map.Entry<?, ?> entry && get(entry.getKey()) == entry.getValue();
        
        @Override
        public boolean remove(final Object object) = object instanceof Map.Entry<?, ?> entry && ConcurrentWeakIdentityHashMap.this.remove(entry.getKey(), entry.getValue());
        
    }
    
    @Default
    ConcurrentHashMap<Key<K>, V> map = { };
    
    @Getter
    ReferenceQueue<K> referenceQueue = { };
    
    @Getter(lazy = true)
    Set<Map.Entry<K, V>> entrySet = new EntrySet();
    
    ThreadLocal<Supplier<V>> recursiveGuard = { }, recursiveCall = { };
    
    public V recursiveGuard(final Supplier<V> task) {
        final boolean guard;
        if (guard = recursiveGuard.get() == null)
            recursiveGuard.set(task);
        else
            recursiveCall.set(task);
        while (true)
            try {
                return task.get();
            } catch (final IllegalStateException recursiveEx) {
                if (guard) {
                    recursiveCall.get()?.get();
                    continue;
                }
                throw recursiveEx;
            } finally {
                if (guard) {
                    recursiveGuard.remove();
                    recursiveCall.remove();
                }
            }
    }
    
    public ConcurrentWeakIdentityHashMap(final int initialCapacity, final float loadFactor = 0.75F, final int concurrencyLevel = 1) = this(new ConcurrentHashMap<>(initialCapacity, loadFactor, concurrencyLevel));
    
    @Override
    public void collect(final Reference<? extends K> reference) = map.remove(reference);
    
    public ConcurrentHashMap<Key<K>, V> purgeKeys() {
        ReferenceCollector.collect(this);
        return map;
    }
    
    @Override
    public V get(final Object key) = purgeKeys().get(new Key<>(key));
    
    @Override
    public V put(final K key, final V value) = purgeKeys().put(new Key<>(key, referenceQueue), value);
    
    @Override
    public int size() = purgeKeys().size();
    
    @Override
    public V putIfAbsent(final K key, final V value) = purgeKeys().putIfAbsent(new Key<>(key, referenceQueue), value);
    
    @Override
    public V remove(final Object key) = purgeKeys().remove(new Key<>(key));
    
    @Override
    public boolean remove(final Object key, final Object value) = purgeKeys().remove(new Key<>(key), value);
    
    @Override
    public boolean replace(final K key, final V oldValue, final V newValue) = purgeKeys().replace(new Key<>(key), oldValue, newValue);
    
    @Override
    public V replace(final K key, final V value) = purgeKeys().replace(new Key<>(key), value);
    
    @Override
    public boolean containsKey(final Object key) = purgeKeys().containsKey(new Key<>(key));
    
    @Override
    public void clear() {
        while (referenceQueue.poll() != null) ;
        map.clear();
    }
    
    @Override
    public boolean containsValue(final Object value) = purgeKeys().containsValue(value);
    
    @Override
    public V getOrDefault(final Object key, final V defaultValue) = purgeKeys().getOrDefault(new Key<>(key), defaultValue);
    
    @Override
    public void forEach(final BiConsumer<? super K, ? super V> action) = purgeKeys().forEach((key, value) -> {
        final @Nullable K k = key.get();
        if (k != null)
            action.accept(k, value);
    });
    
    @Override
    public void replaceAll(final BiFunction<? super K, ? super V, ? extends V> function) = purgeKeys().replaceAll((key, value) -> {
        final @Nullable K k = key.get();
        return k != null ? function.apply(k, value) : value;
    });
    
    @Override
    public V computeIfPresent(final K key, final BiFunction<? super K, ? super V, ? extends V> remappingFunction) = purgeKeys().computeIfPresent(new Key<>(key), (_, value) -> remappingFunction.apply(key, value));
    
    @Override
    public V computeIfAbsent(final K key, final Function<? super K, ? extends V> mappingFunction) {
        final ConcurrentHashMap<Key<K>, V> inner = purgeKeys();
        return inner[new Key<>(key)] ?? recursiveGuard(() -> inner.computeIfAbsent(new Key<>(key, referenceQueue), _ -> mappingFunction.apply(key)));
    }
    
    public V weakComputeIfAbsent(final K key, final Function<? super K, ? extends V> mappingFunction) {
        final ConcurrentHashMap<Key<K>, V> inner = purgeKeys();
        return inner[new Key<>(key)] ?? recursiveGuard(() -> {
            final V v = mappingFunction.apply(key);
            return inner.computeIfAbsent(new Key<>(key, referenceQueue), _ -> v);
        });
    }
    
    @Override
    public V compute(final K key, final BiFunction<? super K, ? super V, ? extends V> remappingFunction) = recursiveGuard(() -> purgeKeys().compute(new Key<>(key, referenceQueue), (_, value) -> remappingFunction.apply(key, value)));
    
    @Override
    public V merge(final K key, final V value, final BiFunction<? super V, ? super V, ? extends V> remappingFunction) = purgeKeys().merge(new Key<>(key, referenceQueue), value, remappingFunction);
    
}
