package amadeus.maho.util.control;

import java.util.Iterator;
import java.util.function.Function;
import java.util.stream.Stream;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.runtime.StreamHelper;

@RequiredArgsConstructor(AccessLevel.PROTECTED)
@FieldDefaults(level = AccessLevel.PRIVATE)
public final class LinkedIterator<T> implements Iterator<T> {
    
    final Function<T, T> nextMapper;
    
    @Default
    @Nullable T context;
    
    @Nullable T cache;
    
    @Override
    public boolean hasNext() = cache != null || context != null && (cache = nextMapper.apply(context)) != null;
    
    public @Nullable T next() {
        if (cache != null)
            try { return cache; } finally { cache = null; }
        return context != null ? context = nextMapper.apply(context) : null; }
    
    public Stream<T> stream(final boolean include = false) = include ? Stream.concat(Stream.of(context), StreamHelper.takeWhileNonNull(this::next)) : StreamHelper.takeWhileNonNull(this::next);
    
    public static <T> LinkedIterator<T> of(final Function<T, T> nextMapper, final @Nullable T context) = { nextMapper, context };
    
}
