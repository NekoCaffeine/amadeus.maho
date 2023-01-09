package amadeus.maho.util.dynamic;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Extension;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;

public interface Local<K, V> {
    
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    class RecursiveGuard<K, V> implements Function<K, V>, AutoCloseable {
        
        Local<K, V> owner;
        
        Map<K, V> localMap = new HashMap<>();
        
        @Override
        public V apply(final K key) {
            @Nullable V value = owner.lookup(key);
            if (value != null)
                return value;
            if ((value = localMap[key]) == null)
                localMap[key] = value = owner.mapper().apply(key);
            return value;
        }
        
        @Override
        public void close() throws Exception = localMap.forEach(owner::putIfAbsent);
    
    }
    
    Function<K, V> mapper();

    @Extension.Operator("GET")
    V get(K key);
    
    @Nullable V lookup(K key);
    
    void putIfAbsent(K key, V value);
    
    default RecursiveGuard<K, V> recursiveGuard() = { this };

}
