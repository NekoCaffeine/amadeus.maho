package amadeus.maho.util.tuple;

import amadeus.maho.lang.inspection.Nullable;

public class Range<T extends Comparable<T>> extends Tuple2<T, T> {
    
    public Range(final @Nullable T lowerInclusive, final @Nullable T upperInclusive) = super(lowerInclusive != null && upperInclusive != null && lowerInclusive.compareTo(upperInclusive) <= 0 ?
            Tuple.tuple(upperInclusive, lowerInclusive) : Tuple.tuple(lowerInclusive, upperInclusive));
    
    public Range(final Tuple2<T, T> tuple) = this(tuple.v1, tuple.v2);
    
    public boolean overlaps(final Range<T> other) = (v1 == null
                                                     || other.v2 == null
                                                     || v1.compareTo(other.v2) <= 0)
                                                    && (v2 == null
                                                        || other.v1 == null
                                                        || v2.compareTo(other.v1) >= 0);
    
    public boolean overlaps(final T lowerInclusive, final T upperInclusive) = overlaps(new Range<>(lowerInclusive, upperInclusive));
    
    public @Nullable Range<T> intersect(final Range<T> other) {
        if (overlaps(other))
            return {
                    v1 == null
                            ? other.v1 : other.v1 == null
                            ? v1 : v1.compareTo(other.v1) >= 0
                            ? v1 : other.v1,
                    v2 == null
                            ? other.v2 : other.v2 == null
                            ? v2 : v2.compareTo(other.v2) <= 0
                            ? v2 : other.v2
            };
        return null;
    }
    
    public @Nullable Range<T> intersect(final T lowerInclusive, final T upperInclusive) = intersect(new Range<>(lowerInclusive, upperInclusive));
    
    public boolean contains(final T t) = t != null && (v1 == null || v1.compareTo(t) <= 0) && (v2 == null || v2.compareTo(t) >= 0);
    
    public boolean contains(final Range<T> other) = (other.v1 == null && v1 == null || contains(other.v1)) && (other.v2 == null && v2 == null || contains(other.v2));
    
}
