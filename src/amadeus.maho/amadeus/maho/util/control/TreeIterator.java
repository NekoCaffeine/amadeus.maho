package amadeus.maho.util.control;

import java.util.function.Function;
import java.util.stream.Stream;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.runtime.StreamHelper;

@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TreeIterator<T> {
    
    final Function<T, T> parentMapper;
    final Function<T, T> nextMapper;
    final Function<T, T> forkMapper;
    
    T context;
    
    public @Nullable T parent() {
        final @Nullable T parent = parentMapper.apply(context);
        return parent != null ? context = parent : null;
    }
    
    public @Nullable T next() {
        @Nullable final T fork;
        @Nullable T next;
        if ((fork = forkMapper.apply(context)) != null)
            return context = fork;
        @Nullable T parent = context;
        do {
            if ((next = nextMapper.apply(parent)) != null)
                return context = next;
        } while ((parent = parentMapper.apply(parent)) != null);
        return null;
    }
    
    public Stream<T> parentward(final boolean include = false) = include ? Stream.concat(Stream.of(context), StreamHelper.takeWhileNonNull(this::parent)) : StreamHelper.takeWhileNonNull(this::parent);
    
    public Stream<T> dfs(final boolean include = false) = include ? Stream.concat(Stream.of(context), StreamHelper.takeWhileNonNull(this::next)) : StreamHelper.takeWhileNonNull(this::next);
    
    public static <T> TreeIterator<T> ofRoot(final Function<T, T> parentMapper, final Function<T, T> nextMapper, final Function<T, T> forkMapper, final T context)
            = { it -> it == context ? null : parentMapper.apply(it), nextMapper, forkMapper, context };
    
}
