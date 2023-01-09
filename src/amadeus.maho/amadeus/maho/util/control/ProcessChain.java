package amadeus.maho.util.control;

import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.runtime.StreamHelper;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProcessChain<T> {
    
    Queue<Predicate<Optional<T>>> chain = new ConcurrentLinkedQueue<>();
    
    StreamHelper.MatchType matchType;
    
    public self add(final Predicate<Optional<T>> function) = chain() += function;
    
    public self remove(final Predicate<Optional<T>> function) = chain() -= function;
    
    public self addAll(final List<Predicate<Optional<T>>> functions) = chain() *= functions;
    
    public self removeAll(final List<Predicate<Optional<T>>> functions) = chain() /= functions;
    
    public boolean process(final @Nullable T target) = process(Optional.ofNullable(target));
    
    public boolean process(final Optional<T> target) = switch (matchType) {
        case ALL -> chain.stream().allMatch(predicate -> process(predicate, target));
        case ANY -> chain.stream().anyMatch(predicate -> process(predicate, target));
        case NONE -> chain.stream().noneMatch(predicate -> process(predicate, target));
    };
    
    private boolean process(final Predicate<Optional<T>> predicate, final Optional<T> target) {
        try {
            return predicate.test(target);
        } catch (final Throwable throwable) {
            throwable.printStackTrace();
            return false;
        }
    }
    
}
