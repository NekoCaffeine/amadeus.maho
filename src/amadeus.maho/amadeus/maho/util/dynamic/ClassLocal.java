package amadeus.maho.util.dynamic;

import java.util.function.Function;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.concurrent.ConcurrentWeakIdentityHashMap;
import amadeus.maho.vm.transform.mark.HotSpotJIT;

@HotSpotJIT
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
public class ClassLocal<V> implements Local<Class<?>, V> { // Don't use the stupid fucking ClassValue
    
    @NoArgsConstructor
    public static class Recursive<V> extends ClassLocal<V> {
        
        ThreadLocal<RecursiveGuard<Class<?>, V>> guardLocal = { };
    
        @Override
        @SneakyThrows
        public V get(final Class<?> key) {
            @Nullable RecursiveGuard<Class<?>, V> guard = guardLocal.get();
            if (guard == null) {
                guardLocal.set(guard = recursiveGuard());
                try {
                    return super.get(key);
                } finally {
                    guard.close();
                    guardLocal.set(null);
                }
            }
            return guard.apply(key);
        }
        
    }
    
    protected record RedefinedRecord(Object value, int classRedefinedCount) { }
    
    ClassLoaderLocal<ConcurrentWeakIdentityHashMap<Class<?>, Object>> local = { loader -> new ConcurrentWeakIdentityHashMap<>() };
    
    @Getter
    Function<Class<?>, V> mapper;
    
    @Default
    boolean strongCorrelation = false;
    
    @Override
    public V get(final Class<?> key) = (V) (strongCorrelation ?
            ((RedefinedRecord) local[key.getClassLoader()].compute(key,
                    // `(Privilege) k.classRedefinedCount` cannot be stored in a local variable here, because mapper may cause redefinition.
                    (k, v) -> v instanceof RedefinedRecord record && record.classRedefinedCount == (Privilege) k.classRedefinedCount ?
                            record : new RedefinedRecord(mapper.apply(k), (Privilege) k.classRedefinedCount))).value() :
            local[key.getClassLoader()].computeIfAbsent(key, mapper));
    
    @Override
    public @Nullable V lookup(final Class<?> key) = (V) (strongCorrelation ?
            local[key.getClassLoader()].get(key) instanceof RedefinedRecord record && record.classRedefinedCount == (Privilege) key.classRedefinedCount ? record.value() : null :
            local[key.getClassLoader()].get(key));
    
    @Override
    public void putIfAbsent(final Class<?> key, final V value) {
        if (strongCorrelation)
            ((RedefinedRecord) local[key.getClassLoader()].compute(key,
                    // `(Privilege) k.classRedefinedCount` cannot be stored in a local variable here, because mapper may cause redefinition.
                    (k, v) -> v instanceof RedefinedRecord record && record.classRedefinedCount == (Privilege) k.classRedefinedCount ?
                            record : new RedefinedRecord(value, (Privilege) k.classRedefinedCount))).value();
        else
            local[key.getClassLoader()].putIfAbsent(key, value);
    }
    
}
