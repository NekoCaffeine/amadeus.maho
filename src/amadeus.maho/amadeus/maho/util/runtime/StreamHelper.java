package amadeus.maho.util.runtime;

import java.util.Iterator;
import java.util.Optional;
import java.util.Spliterators;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import amadeus.maho.lang.Extension;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.type.TypeToken;

@Extension
public interface StreamHelper {
    
    enum MatchType {
        ANY, ALL, NONE
    }
    
    static <T> Stream<T> fromIterable(final Iterable<T> iterable) = fromIterator(iterable.iterator());
    
    static <T> Stream<T> fromIterator(final Iterator<T> iterator) = StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, 0), false);
    
    static <T> Stream<T> takeWhileNonNull(final Supplier<T> generator) = Stream.generate(generator).takeWhile(ObjectHelper::nonNull);
    
    static <S, R> Stream<R> cast(final Stream<S> $this, final Class<R> type) = (Stream<R>) $this.filter(type::isInstance);
    
    static <S, R> Stream<R> cast(final Stream<S> $this, final TypeToken<R> token) = (Stream<R>) $this.filter(token.erasedType()::isInstance);
    
    static <T> Stream<T> filterNot(final Stream<T> $this, final Predicate<? super T> predicate) = $this.filter(predicate.negate());
    
    static <R> Stream<Class<? extends R>> filterAssignableFrom(final Stream<Class<?>> $this, final Class<R> type) = (Stream<Class<? extends R>>) (Object) $this.filter(type::isAssignableFrom);
    
    static <T> Stream<T> nonnull(final Stream<T> $this) = $this.filter(ObjectHelper::nonNull);
    
    static <S, R, T extends Throwable> Stream<R> safeMap(final Stream<S> $this, final Function<S, R> mapper, final Class<T> type = (Class<T>) Throwable.class, final BiFunction<S, T, R> handler = (_, _) -> null)
            = $this.map(it -> {
        try {
            return mapper.apply(it);
        } catch (final Throwable e) {
            if (type.isInstance(e))
                return handler.apply(it, (T) e);
            throw e;
        }
    }).nonnull();
    
    static <T> Optional<T> lookup(final Stream<T> $this, final Predicate<T> predicate) = $this.filter(predicate).findFirst();
    
    static <T> Optional<T> lookup(final Stream<T> $this, final Class<T> type) = $this.cast(type).findFirst();
    
    static <T> @Nullable T TILDE(final Stream<T> $this) = $this.findFirst().orElse(null);
    
    static <T> T NOT(final Stream<T> $this) = $this.findFirst().orElseThrow();
    
    static String collectCodepoints(final IntStream $this) = $this.collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
    
    
}
