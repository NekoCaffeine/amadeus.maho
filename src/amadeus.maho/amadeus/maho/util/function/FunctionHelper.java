package amadeus.maho.util.function;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;

public interface FunctionHelper {
    
    @SafeVarargs
    static <T> void always(final Consumer<T> consumer, final T... ts) = Stream.of(ts).forEach(consumer);
    
    @SafeVarargs
    static <A, B> void alwaysA(final BiConsumer<A, B> consumer, final A a, final B... bs) = Stream.of(bs).forEach(b -> consumer.accept(a, b));
    
    @SafeVarargs
    static <A, B> void alwaysB(final BiConsumer<A, B> consumer, final B b, final A... as) = Stream.of(as).forEach(a -> consumer.accept(a, b));
    
    static <A, B> BiConsumer<B, A> exchange(final BiConsumer<A, B> consumer) = (b, a) -> consumer.accept(a, b);
    
    static Runnable link(final Runnable... runnables) = () -> Stream.of(runnables).forEach(Runnable::run);
    
    @SafeVarargs
    static <T> Consumer<T> link(final Consumer<T>... consumers) = t -> Stream.of(consumers).forEach(consumer -> consumer.accept(t));
    
    static <T> Predicate<T> not(final Predicate<T> predicate) = predicate.negate();
    
    static <A> Runnable link(final Supplier<A> supplier, final Consumer<A> consumer) = () -> consumer.accept(supplier.get());
    
    static <A, B> Consumer<A> link(final Function<A, B> function, final Consumer<B> consumer) = a -> consumer.accept(function.apply(a));
    
    static <A, B, C> Function<A, C> map(final Function<A, B> functionA, final Function<B, C> functionB) = a -> functionB.apply(functionA.apply(a));
    
    static <A, B, C> BiConsumer<A, C> link(final Function<A, B> function, final BiConsumer<B, C> consumer) = (a, c) -> consumer.accept(function.apply(a), c);
    
    static <A, B> Consumer<A> linkA(final Supplier<B> supplier, final BiConsumer<A, B> consumer) = a -> consumer.accept(a, supplier.get());
    
    static <A, B> Consumer<B> linkB(final Supplier<A> supplier, final BiConsumer<A, B> consumer) = b -> consumer.accept(supplier.get(), b);
    
    static <A, B, C> Function<A, C> linkA(final Supplier<B> supplier, final BiFunction<A, B, C> consumer) = a -> consumer.apply(a, supplier.get());
    
    static <A, B, C> Function<B, C> linkB(final Supplier<A> supplier, final BiFunction<A, B, C> consumer) = b -> consumer.apply(supplier.get(), b);
    
    static <A, B> Supplier<B> map(final Supplier<A> supplier, final Function<A, B> function) = () -> function.apply(supplier.get());
    
    static <U, R> Function<U, R> abandon(final Supplier<R> supplier) = u -> supplier.get();
    
    static <T> BinaryOperator<T> first() = (a, b) -> a;
    
    static <T> BinaryOperator<T> last() = (a, b) -> b;
    
    static Runnable nothing() = () -> { };
    
    static <T> Consumer<T> abandon() = _ -> { };
    
    static <T, R> Function<T, R> cast() = (Function<T, R>) Function.identity();
    
    static <T> Supplier<T> lazy(final Supplier<T> supplier) {
        final boolean p_flag[] = { false };
        final Object p_value[] = { null };
        return () -> {
            if (!p_flag[0]) {
                p_value[0] = supplier.get();
                p_flag[0] = true;
            }
            return (T) p_value[0];
        };
    }
    
    static <T> Predicate<T> count(final int count) {
        final int p_count[] = { count };
        return _ -> --p_count[0] < 0;
    }
    
    static <T> Supplier<T> constant(final @Nullable T constant) = () -> constant;
    
    static BooleanSupplier constant(final boolean constant) = () -> constant;
    
    static IntSupplier constant(final int constant) = () -> constant;
    
    static LongSupplier constant(final long constant) = () -> constant;
    
    @SneakyThrows
    static <T> T rethrow(final Throwable throwable) { throw throwable; }
    
    static void ignored(final Throwable throwable) { }
    
    @SneakyThrows
    static void rethrowVoid(final Throwable throwable) { throw throwable; }
    
    static <T> Consumer<T> ignored(final Consumer<T> consumer) = t -> {
        try {
            consumer.accept(t);
        } catch (final Throwable ignored) { }
    };
    
}
