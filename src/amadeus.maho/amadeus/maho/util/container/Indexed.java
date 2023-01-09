package amadeus.maho.util.container;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.inspection.Nullable;

public interface Indexed<T> {
    
    @Setter
    @Getter
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PROTECTED)
    class Default<T> implements Indexed<T> {
        
        ToIntFunction<T> submit;
        IntFunction<T> fetch, remove;
    
        public Default(final ConcurrentHashMap<Integer, T> backend = { }, final AtomicInteger p_index = { }) = this(value -> {
            synchronized (backend) {
                return backend.entrySet().stream()
                        .filter(entry -> entry.getValue() == value)
                        .map(Map.Entry::getKey)
                        .findAny()
                        .orElseGet(() -> {
                            final int index = p_index.getAndIncrement();
                            backend.put(index, value);
                            return index;
                        });
            }
        }, backend::get, backend::remove);
    
        @Override
        public int submit(final @Nullable T value) = submit.applyAsInt(value);
        
        @Override
        public @Nullable T fetch(final int id) = fetch.apply(id);
        
        @Override
        public @Nullable T remove(final int id) = remove.apply(id);
        
    }
    
    int submit(final @Nullable T value);
    
    @Nullable T fetch(final int id);
    
    @Nullable T remove(final int id);
    
}
