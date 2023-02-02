package amadeus.maho.util.dynamic;

import java.util.function.Function;

import jdk.internal.loader.ClassLoaderValue;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.inspection.Nullable;

@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
public class ClassLoaderLocal<V> implements Local<ClassLoader, V> {
    
    ClassLoaderValue<V> value = { };
    
    @Getter
    Function<ClassLoader, V> mapper;
    
    @Override
    public V get(final @Nullable ClassLoader key) = value.computeIfAbsent(key, (loader, value) -> mapper.apply(loader));
    
    @Override
    public V lookup(final ClassLoader key) = value.get(key);
    
    @Override
    public void putIfAbsent(final ClassLoader key, final V value) = this.value.putIfAbsent(key, value);
    
}
