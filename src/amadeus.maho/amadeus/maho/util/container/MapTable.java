package amadeus.maho.util.container;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.function.Consumer3;
import amadeus.maho.util.function.FunctionHelper;
import amadeus.maho.util.runtime.ObjectHelper;
import amadeus.maho.util.tuple.Tuple2;
import amadeus.maho.util.tuple.Tuple3;
import amadeus.maho.vm.transform.mark.HotSpotJIT;

@HotSpotJIT
public interface MapTable<R, C, V> {
    
    static <R, C, V> MapTable<R, C, V> newMapTable(final Map<R, Map<C, V>> backingMap, final Function<R, Map<C, V>> factory) = new MapTable<>() {
        
        @Override
        public Map<R, Map<C, V>> backingMap() = backingMap;
        
        @Override
        public Function<R, Map<C, V>> factory() = factory;
        
        @Override
        public int hashCode() = backingMap().hashCode();
        
        @Override
        public boolean equals(final Object obj) = obj instanceof MapTable table && backingMap().equals(table.backingMap());
        
        @Override
        public String toString() = backingMap().toString();
        
    };
    
    static <R, C, V> MapTable<R, C, V> newMapTable(final Supplier<Map<R, Map<C, V>>> backingMapSupplier, final Supplier<Map<C, V>> factory) = newMapTable(backingMapSupplier.get(), FunctionHelper.abandon(factory));
    
    static <R, C, V> MapTable<R, C, V> newHashMapTable() = newMapTable(new HashMap<>(), FunctionHelper.abandon(HashMap::new));
    
    static <R, C, V> MapTable<R, C, V> newLinkedHashMapTable() = newMapTable(new LinkedHashMap<>(), FunctionHelper.abandon(LinkedHashMap::new));
    
    static <R, C, V> MapTable<R, C, V> newWeakHashMapTable() = newMapTable(new WeakHashMap<>(), FunctionHelper.abandon(WeakHashMap::new));
    
    static <R, C, V> MapTable<R, C, V> newIdentityHashMapTable() = newMapTable(new IdentityHashMap<>(), FunctionHelper.abandon(IdentityHashMap::new));
    
    static <R, C, V> MapTable<R, C, V> newConcurrentHashMapTable() = newMapTable(new ConcurrentHashMap<>(), FunctionHelper.abandon(ConcurrentHashMap::new));
    
    static <R extends Comparable<R>, C extends Comparable<C>, V> MapTable<R, C, V> newConcurrentSkipListMapTable() = newMapTable(new ConcurrentSkipListMap<>(), FunctionHelper.abandon(ConcurrentSkipListMap::new));
    
    static <R, C, V> MapTable<R, C, V> unmodifiableTable(final MapTable<R, C, V> table) = newMapTable(Collections.unmodifiableMap(table.backingMap()), FunctionHelper.abandon(Collections::emptyMap));
    
    static <R, C, V> MapTable<R, C, V> synchronizedTable(final MapTable<R, C, V> table) {
        table.backingMap().replaceAll((row, map) -> Collections.synchronizedMap(map));
        return newMapTable(Collections.synchronizedMap(table.backingMap()), FunctionHelper.map(table.factory(), Collections::synchronizedMap));
    }
    
    Map<R, Map<C, V>> backingMap();
    
    Function<R, Map<C, V>> factory();
    
    default int size() = backingMap().values().stream().mapToInt(Map::size).sum();
    
    default boolean isEmpty() = backingMap().values().stream().anyMatch(((Predicate<Map<C, V>>) Map::isEmpty).negate());
    
    default void clear() = backingMap().clear();
    
    default @Nullable Map<C, V> get(final @Nullable R rowKey) = backingMap().get(rowKey);
    
    default @Nullable V get(final @Nullable R rowKey, final @Nullable C columnKey) {
        final @Nullable Map<C, V> columnMap = backingMap().get(rowKey);
        return columnMap != null ? columnMap.get(columnKey) : null;
    }
    
    default @Nullable V get(final Tuple2<R, C> tuple) = get(tuple.v1, tuple.v2);
    
    default @Nullable V getOrDefault(final @Nullable R rowKey, final @Nullable C columnKey, final @Nullable V defaultValue) {
        final @Nullable Map<C, V> columnMap = backingMap().get(rowKey);
        final @Nullable V value;
        return columnMap != null ? (value = columnMap.get(columnKey)) != null || columnMap.containsKey(columnKey) ? value : defaultValue : defaultValue;
    }
    
    default @Nullable V getOrDefault(final Tuple2<R, C> tuple, final @Nullable V defaultValue) = getOrDefault(tuple.v1, tuple.v2, defaultValue);
    
    default @Nullable V put(final @Nullable R rowKey, final @Nullable C columnKey, final @Nullable V value) = row(rowKey).put(columnKey, value);
    
    default @Nullable V put(final Tuple3<R, C, V> tuple) = put(tuple.v1, tuple.v2, tuple.v3);
    
    default void putAll(final MapTable<R, C, V> table) = table.backingMap().forEach((rowKey, columnMap) -> row(rowKey).putAll(columnMap));
    
    default Map<C, V> row(final @Nullable R rowKey) = backingMap().computeIfAbsent(rowKey, factory());
    
    default @Nullable V remove(final @Nullable R rowKey, final @Nullable C columnKey) {
        final @Nullable Map<C, V> columnMap = backingMap().get(rowKey);
        return columnMap != null ? columnMap.remove(columnKey) : null;
    }
    
    default @Nullable V remove(final Tuple2<R, C> tuple) = remove(tuple.v1, tuple.v2);
    
    default @Nullable Map<C, V> removeRow(final @Nullable R rowKey) = backingMap().remove(rowKey);
    
    default List<V> removeColumn(final @Nullable C columnKey) = backingMap().values().stream().map(columnMap -> columnMap.remove(columnKey)).toList();
    
    default boolean removeValue(final @Nullable V value) = backingMap().values().stream().anyMatch(columnMap -> columnMap.values().removeIf(Predicate.isEqual(value)));
    
    default boolean contains(final @Nullable R rowKey, final @Nullable C columnKey) {
        final @Nullable Map<C, V> columnMap = backingMap().get(rowKey);
        return columnMap != null && columnMap.containsKey(columnKey);
    }
    
    default boolean contains(final @Nullable R rowKey, final @Nullable C columnKey, final @Nullable V value) = ObjectHelper.equals(row(rowKey).get(columnKey), value);
    
    default boolean containsRow(final @Nullable R rowKey) = backingMap().containsKey(rowKey);
    
    default boolean containsColumn(final @Nullable C columnKey) = backingMap().values().stream().map(Map::keySet).flatMap(Collection::stream).anyMatch(Predicate.isEqual(columnKey));
    
    default boolean containsValue(final @Nullable V value) = backingMap().values().stream().map(Map::values).flatMap(Collection::stream).anyMatch(Predicate.isEqual(value));
    
    default void forEach(final Consumer3<R, C, V> consumer) = backingMap().forEach((row, map) -> map.forEach((column, value) -> consumer.accept(row, column, value)));
    
}
