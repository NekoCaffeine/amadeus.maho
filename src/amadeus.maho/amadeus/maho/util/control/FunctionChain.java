package amadeus.maho.util.control;

import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.vm.transform.mark.HotSpotJIT;

@HotSpotJIT
@Getter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FunctionChain<T, R> {
    
    Queue<Function<Optional<T>, Optional<R>>> chain = new ConcurrentLinkedQueue<>();
    
    public self add(final Function<Optional<T>, Optional<R>> function) = chain() += function;
    
    public self remove(final Function<Optional<T>, Optional<R>> function) = chain() -= function;
    
    public self addAll(final List<Function<Optional<T>, Optional<R>>> functions) = chain() *= functions;
    
    public self removeAll(final List<Function<Optional<T>, Optional<R>>> functions) = chain() /= functions;
    
    public Optional<R> apply(final @Nullable T target) = apply(Optional.ofNullable(target));
    
    public Optional<R> apply(final Optional<T> target) = chain.stream()
            .map(function -> {
                try {
                    return function.apply(target);
                } catch (final Throwable throwable) {
                    throwable.printStackTrace();
                    return Optional.<R>empty();
                }
            })
            .filter(Optional::isPresent)
            .findFirst()
            .orElseGet(Optional::empty);
    
    public @Nullable R applyNullable(final @Nullable T target) = apply(target).orElse(null);
    
    public @Nullable R applyNullable(final Optional<T> target) = apply(target).orElse(null);
    
}
