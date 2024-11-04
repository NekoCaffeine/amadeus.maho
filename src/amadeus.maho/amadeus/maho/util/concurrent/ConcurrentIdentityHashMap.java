package amadeus.maho.util.concurrent;

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
import amadeus.maho.util.runtime.ObjectHelper;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
public class ConcurrentIdentityHashMap<K, V> extends AbstractMap<K, V> implements ConcurrentMap<K, V> {
    
    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class Key<K> extends WeakReference<K> {
        
        K referent;
        
        int hashCode;
        
        public Key(final K key) {
            super(ObjectHelper.requireNonNull(key));
            hashCode = System.identityHashCode(referent = key);
        }
        
        @Override
        public boolean equals(final Object object) {
            if (this == object)
                return true;
            return object instanceof Key key && key.get() == referent;
        }
        
    }
    
    protected class EntrySet extends AbstractSet<Entry<K, V>> {
        
        @NoArgsConstructor
        protected class Entry extends SimpleEntry<K, V> {
            
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
                try {
                    // noinspection DataFlowIssue
                    return nextEntry;
                } finally { nextEntry = null; }
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
        public void clear() = ConcurrentIdentityHashMap.this.clear();
        
        @Override
        public int size() = ConcurrentIdentityHashMap.this.size();
        
        @Override
        public boolean isEmpty() = ConcurrentIdentityHashMap.this.isEmpty();
        
        @Override
        public boolean contains(final Object object) = object instanceof Map.Entry<?, ?> entry && get(entry.getKey()) == entry.getValue();
        
        @Override
        public boolean remove(final Object object) = object instanceof Map.Entry<?, ?> entry && ConcurrentIdentityHashMap.this.remove(entry.getKey(), entry.getValue());
        
    }
    
    @Default
    ConcurrentHashMap<Key<K>, V> map = { };
    
    @Getter(lazy = true)
    Set<Entry<K, V>> entrySet = new EntrySet();
    
    ThreadLocal<Supplier<V>> recursiveGuard = { }, recursiveCall = { };
    
    public V recursiveGuard(final Supplier<V> task) {
        final boolean guard = recursiveGuard.get() == null;
        (guard ? recursiveGuard : recursiveCall).set(task);
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
    
    public ConcurrentIdentityHashMap(final int initialCapacity, final float loadFactor = 0.75F, final int concurrencyLevel = 1) = this(new ConcurrentHashMap<K, V>(initialCapacity, loadFactor, concurrencyLevel));
    
    public ConcurrentIdentityHashMap(final Map<K, V> map) {
        this();
        putAll(map);
    }
    
    public <K> Key<K> key(final K key) = { key };
    
    @Override
    public V get(final Object key) = map.get(key(key));
    
    @Override
    public V put(final K key, final V value) = map.put(key(key), value);
    
    @Override
    public int size() = map.size();
    
    @Override
    public V putIfAbsent(final K key, final V value) = map.putIfAbsent(key(key), value);
    
    @Override
    public V remove(final Object key) = map.remove(key(key));
    
    @Override
    public boolean remove(final Object key, final Object value) = map.remove(key(key), value);
    
    @Override
    public boolean replace(final K key, final V oldValue, final V newValue) = map.replace(key(key), oldValue, newValue);
    
    @Override
    public V replace(final K key, final V value) = map.replace(key(key), value);
    
    @Override
    public boolean containsKey(final Object key) = map.containsKey(key(key));
    
    @Override
    public void clear() = map.clear();
    
    @Override
    public boolean containsValue(final Object value) = map.containsValue(value);
    
    @Override
    public V getOrDefault(final Object key, final V defaultValue) = map.getOrDefault(key(key), defaultValue);
    
    @Override
    public void forEach(final BiConsumer<? super K, ? super V> action) = map.forEach((key, value) -> {
        final @Nullable K k = key.get();
        if (k != null)
            action.accept(k, value);
    });
    
    @Override
    public void replaceAll(final BiFunction<? super K, ? super V, ? extends V> function) = map.replaceAll((key, value) -> {
        final @Nullable K k = key.get();
        return k != null ? function.apply(k, value) : value;
    });
    
    @Override
    public @Nullable V computeIfPresent(final K key, final BiFunction<? super K, ? super V, ? extends V> remappingFunction) = map.computeIfPresent(key(key), (_, value) -> remappingFunction.apply(key, value));
    
    @Override
    public V computeIfAbsent(final K key, final Function<? super K, ? extends V> mappingFunction) {
        final ConcurrentHashMap<Key<K>, V> inner = map;
        return inner[key(key)] ?? recursiveGuard(() -> inner.computeIfAbsent(key(key), _ -> mappingFunction.apply(key)));
    }
    
    public V weakComputeIfAbsent(final K key, final Function<? super K, ? extends V> mappingFunction) {
        final ConcurrentHashMap<Key<K>, V> inner = map;
        return inner[key(key)] ?? recursiveGuard(() -> {
            final V v = mappingFunction.apply(key);
            return inner.computeIfAbsent(key(key), _ -> v);
        });
    }
    
    @Override
    public V compute(final K key, final BiFunction<? super K, ? super V, ? extends V> remappingFunction) = recursiveGuard(() -> map.compute(key(key), (_, value) -> remappingFunction.apply(key, value)));
    
    @Override
    public V merge(final K key, final V value, final BiFunction<? super V, ? super V, ? extends V> remappingFunction) = map.merge(key(key), value, remappingFunction);
    
}
