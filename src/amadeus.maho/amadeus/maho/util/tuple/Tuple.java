package amadeus.maho.util.tuple;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import amadeus.maho.lang.inspection.Nullable;

public interface Tuple extends Iterable<Object>, Cloneable {
    
    Object[] array();
    
    default List<Object> list() = List.of(array());
    
    default Stream<Object> stream() = Stream.of(array());
    
    @Override
    default Iterator<Object> iterator() = list().iterator();
    
    static <T extends Comparable<T>> Range<T> range(final @Nullable T v1, final @Nullable T v2) = { v1, v2 };
    
    static <T1> Tuple1<T1> tuple(final @Nullable T1 v1) = { v1 };
    
    static <T1, T2> Tuple2<T1, T2> tuple(final @Nullable T1 v1, final @Nullable T2 v2) = { v1, v2 };
    
    static <T1, T2, T3> Tuple3<T1, T2, T3> tuple(final @Nullable T1 v1, final @Nullable T2 v2, final @Nullable T3 v3) = { v1, v2, v3 };
    
    static <T1, T2, T3, T4> Tuple4<T1, T2, T3, T4> tuple(final @Nullable T1 v1, final @Nullable T2 v2, final @Nullable T3 v3, final @Nullable T4 v4) = { v1, v2, v3, v4 };
    
    static <T1, T2, T3, T4, T5> Tuple5<T1, T2, T3, T4, T5> tuple(final @Nullable T1 v1, final @Nullable T2 v2, final @Nullable T3 v3, final @Nullable T4 v4, final @Nullable T5 v5) = { v1, v2, v3, v4, v5 };
    
    static <T1, T2, T3, T4, T5, T6> Tuple6<T1, T2, T3, T4, T5, T6> tuple(final @Nullable T1 v1, final @Nullable T2 v2, final @Nullable T3 v3, final @Nullable T4 v4, final @Nullable T5 v5, final @Nullable T6 v6) = { v1, v2, v3, v4, v5, v6 };
    
    static <T1, T2, T3, T4, T5, T6, T7> Tuple7<T1, T2, T3, T4, T5, T6, T7> tuple(final @Nullable T1 v1, final @Nullable T2 v2, final @Nullable T3 v3, final @Nullable T4 v4, final @Nullable T5 v5, final @Nullable T6 v6,
            final @Nullable T7 v7) = { v1, v2, v3, v4, v5, v6, v7 };
    
    static <T1, T2, T3, T4, T5, T6, T7, T8> Tuple8<T1, T2, T3, T4, T5, T6, T7, T8> tuple(final @Nullable T1 v1, final @Nullable T2 v2, final @Nullable T3 v3, final @Nullable T4 v4, final @Nullable T5 v5, final @Nullable T6 v6,
            final @Nullable T7 v7, final @Nullable T8 v8) = { v1, v2, v3, v4, v5, v6, v7, v8 };
    
}
