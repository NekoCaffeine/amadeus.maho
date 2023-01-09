package amadeus.maho.util.control;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.inspection.RegularExpression;
import amadeus.maho.util.tuple.Tuple;
import amadeus.maho.util.tuple.Tuple2;

@Setter
@Getter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Switch<T, R> {
    
    final List<Tuple2<Predicate<T>, Function<T, R>>> cases = new CopyOnWriteArrayList<>();
    
    volatile @Nullable Function<T, R> defaultHandler;
    
    public Optional<Function<T, R>> rollback() = Optional.ofNullable(defaultHandler());
    
    protected Function<T, R> function(final Consumer<T> consumer) = target -> {
        consumer.accept(target);
        return null;
    };
    
    public self whenF(final Predicate<T> predicate, final Function<T, R> function) = cases().add(Tuple.tuple(predicate, function));
    
    public self whenC(final Predicate<T> predicate, final Consumer<T> consumer) = whenF(predicate, function(consumer));
    
    public self whenDefaultF(final Function<T, R> function) = defaultHandler(function);
    
    public self whenDefaultC(final Consumer<T> consumer) = defaultHandler(function(consumer));
    
    public self equalF(final @Nullable T value, final Function<T, R> function) = whenF(Predicate.isEqual(value), function);
    
    public self equalC(final @Nullable T value, final Consumer<T> consumer) = equalF(value, function(consumer));
    
    public self referenceF(final @Nullable T value, final Function<T, R> function) = whenF(target -> target == value, function);
    
    public self referenceC(final @Nullable T value, final Consumer<T> consumer) = referenceF(value, function(consumer));
    
    public self isNullF(final Function<T, R> function) = whenF(Objects::isNull, function);
    
    public self isNullC(final Consumer<T> consumer) = isNullF(function(consumer));
    
    public self ignoreNull() = whenF(Objects::isNull, target -> null);
    
    public self nonNullF(final Function<T, R> function) = whenF(Objects::nonNull, function);
    
    public self nonNullC(final Consumer<T> consumer) = nonNullF(function(consumer));
    
    public self regexF(final @RegularExpression String regex, final Function<T, R> function) = whenF(target -> Objects.toString(target).matches(regex), function);
    
    public self regexC(final @RegularExpression String regex, final Consumer<T> consumer) = regexF(regex, function(consumer));
    
    public boolean match(final @Nullable T target, final boolean includeDefault = true) = cases.stream().anyMatch(tuple -> tuple.v1.test(target)) || includeDefault && defaultHandler() != null;
    
    public boolean parallelMatch(final @Nullable T target, final boolean includeDefault = true) = cases.stream().parallel().anyMatch(tuple -> tuple.v1.test(target)) || includeDefault && defaultHandler() != null;
    
    public Optional<R> apply(final @Nullable T target) = cases.stream()
            .filter(tuple -> tuple.v1.test(target))
            .findFirst()
            .map(Tuple2::v2)
            .or(this::rollback)
            .map(function -> function.apply(target));
    
    public Optional<R> parallelApply(final @Nullable T target) = cases.stream()
            .parallel()
            .filter(tuple -> tuple.v1.test(target))
            .findFirst()
            .map(Tuple2::v2)
            .or(this::rollback)
            .map(function -> function.apply(target));
    
    public @Nullable R applyNullable(final @Nullable T target) = apply(target).orElse(null);
    
    public @Nullable R parallelApplyNullable(final @Nullable T target) = parallelApply(target).orElse(null);
    
}
