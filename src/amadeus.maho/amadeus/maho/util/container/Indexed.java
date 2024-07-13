package amadeus.maho.util.container;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.Extension;
import amadeus.maho.lang.ToString;

import static amadeus.maho.util.runtime.ObjectHelper.requireNonNull;

public interface Indexed<T> {
    
    @ToString
    @EqualsAndHashCode
    record Base<T>(@ToString.Mark @EqualsAndHashCode.Mark Map<T, Integer> ids = new HashMap<>(), List<T> values = new ArrayList<>()) implements Indexed<T> {
        
        @Override
        public int id(final T value) = ids.computeIfAbsent(value, it -> {
            final int id = values.size();
            values += it;
            return id;
        });
        
        @Override
        public T value(final int id) = requireNonNull(values[id]);
        
        @Override
        public void clear() {
            ids.clear();
            values.clear();
        }
        
    }
    
    @ToString
    @EqualsAndHashCode
    record Concurrent<T>(@ToString.Mark @EqualsAndHashCode.Mark ConcurrentHashMap<T, Integer> ids = { }, CopyOnWriteArrayList<T> values = { }) implements Indexed<T> {
        
        @Override
        public int id(final T value) = ids.computeIfAbsent(value, it -> {
            final int id;
            synchronized (this) {
                id = values.size();
                values += it;
            }
            return id;
        });
        
        @Override
        public T value(final int id) = requireNonNull(values[id]);
        
        @Override
        public synchronized void clear() {
            ids.clear();
            values.clear();
        }
        
    }
    
    @Extension.Operator("GET")
    int id(T value);
    
    @Extension.Operator("GET")
    T value(int id);
    
    List<T> values();
    
    void clear();
    
    static <T> Base<T> of(final Map<T, Integer> ids = new HashMap<>(), final List<T> values = new ArrayList<>()) = { ids, values };
    
    static <T> Base<T> ofIdentity() = { new IdentityHashMap<>() };
    
    static <T> Concurrent<T> ofConcurrent() = { };
    
}
