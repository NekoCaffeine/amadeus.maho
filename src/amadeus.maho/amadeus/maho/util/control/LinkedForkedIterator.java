package amadeus.maho.util.control;

import java.util.List;
import java.util.ListIterator;
import java.util.function.Function;
import java.util.stream.Stream;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.runtime.StreamHelper;

@RequiredArgsConstructor(AccessLevel.PROTECTED)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LinkedForkedIterator<T> {
    
    final Function<T, T> parentMapper;
    final Function<T, List<T>> forksMapper;
    
    @Default
    T context;
    
    public @Nullable T parent() {
        final @Nullable T parent = parentMapper.apply(context);
        return parent != null ? context = parent : null;
    }
    
    public @Nullable T back() {
        @Nullable final List<T> forks;
        @Nullable final T parent;
        if ((parent = parentMapper.apply(context)) != null) {
            if ((forks = forksMapper.apply(parent)) != null && !forks.isEmpty())
                for (final ListIterator<T> iterator = forks.listIterator(); iterator.hasNext(); )
                    if (iterator.next() == context && iterator.hasPrevious())
                        return context = iterator.previous();
            return context = parent;
        }
        return null;
    }
    
    public @Nullable T next() {
        @Nullable List<T> forks;
        if ((forks = forksMapper.apply(context)) != null && !forks.isEmpty())
            return context = forks[0];
        @Nullable T parent = context, prev = parent;
        while ((parent = parentMapper.apply(parent)) != null) {
            if ((forks = forksMapper.apply(parent)) != null && !forks.isEmpty())
                for (final ListIterator<T> iterator = forks.listIterator(); iterator.hasNext(); )
                    if (iterator.next() == prev && iterator.hasNext())
                        return context = iterator.next();
            prev = parent;
        }
        return null;
    }
    
    public Stream<T> parentward(final boolean include = false) = include ? Stream.concat(Stream.of(context), StreamHelper.takeWhileNonNull(this::parent)) : StreamHelper.takeWhileNonNull(this::parent);
    
    public Stream<T> backward(final boolean include = false) = include ? Stream.concat(Stream.of(context), StreamHelper.takeWhileNonNull(this::back)) : StreamHelper.takeWhileNonNull(this::back);
    
    public Stream<T> forward(final boolean include = false) = include ? Stream.concat(Stream.of(context), StreamHelper.takeWhileNonNull(this::next)) : StreamHelper.takeWhileNonNull(this::next);
    
    public static <T> LinkedForkedIterator<T> ofRoot(final Function<T, T> parentMapper, final Function<T, List<T>> forksMapper, final T context)
            = { it -> it == context ? null : parentMapper.apply(it), forksMapper, context };
    
}
