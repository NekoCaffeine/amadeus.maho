package amadeus.maho.util.function;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.Extension;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;

public interface FunctionHelper {
    
    @Extension
    interface Ext {
        
        @Extension.Operator(">")
        static @Nullable Runnable then(final @Nullable Runnable runnable, final @Nullable Runnable after)
            = runnable == null ? after : after == null ? runnable : () -> {
                after.run();
                runnable.run();
            };
        
        @Extension.Operator("~")
        static void safeRun(final @Nullable Runnable runnable) = runnable?.run();
        
        @Extension.Operator("^")
        static void safeRun(final @Nullable Runnable runnable, final Consumer<Throwable> handler) {
            try {
                runnable?.run();
            } catch (final Throwable t) { handler.accept(t); }
        }
        
        @Extension.Operator(">")
        static <T> @Nullable Consumer<T> then(final @Nullable Consumer<? super T> consumer, final @Nullable Consumer<? super T> after)
            = consumer == null ? (Consumer<T>) after : after == null ? (Consumer<T>) consumer : t -> {
                consumer.accept(t);
                after.accept(t);
            };
        
        @Extension.Operator(">")
        static <A, B> @Nullable BiConsumer<A, B> then(final @Nullable BiConsumer<? super A, ? super B> consumer, final @Nullable BiConsumer<? super A, ? super B> after)
            = consumer == null ? (BiConsumer<A, B>) after : after == null ? (BiConsumer<A, B>) consumer : (a, b) -> {
                consumer.accept(a, b);
                after.accept(a, b);
            };
        
        @Extension.Operator(">")
        static <T> Consumer<T> ifThen(final Predicate<? super T> predicate, final @Nullable Consumer<? super T> consumer)
            = consumer == null ? _ -> { } : t -> {
                if (predicate.test(t))
                    consumer.accept(t);
            };
        
        @Extension.Operator(">")
        static <A, B> BiConsumer<A, B> ifThen(final BiPredicate<? super A, ? super B> predicate, final @Nullable BiConsumer<? super A, ? super B> consumer)
            = consumer == null ? (a, b) -> { } : (a, b) -> {
                if (predicate.test(a, b))
                    consumer.accept(a, b);
            };
        
        @Extension.Operator("&")
        static <T> @Nullable Predicate<T> and(final @Nullable Predicate<? super T> predicate, final @Nullable Predicate<? super T> other)
            = predicate == null ? (Predicate<T>) other : other == null ? (Predicate<T>) predicate : t -> predicate.test(t) && other.test(t);
        
        @Extension.Operator("|")
        static <T> @Nullable Predicate<T> or(final @Nullable Predicate<? super T> predicate, final @Nullable Predicate<? super T> other)
            = predicate == null ? (Predicate<T>) other : other == null ? (Predicate<T>) predicate : t -> predicate.test(t) || other.test(t);
        
        @Extension.Operator("!")
        static <T> @Nullable Predicate<T> not(final @Nullable Predicate<? super T> predicate)
            = predicate == null ? null : (Predicate<T>) predicate.negate();
        
        @Extension.Operator("&")
        static <A, B> @Nullable BiPredicate<A, B> and(final @Nullable BiPredicate<? super A, ? super B> predicate, final @Nullable BiPredicate<? super A, ? super B> other)
            = predicate == null ? (BiPredicate<A, B>) other : other == null ? (BiPredicate<A, B>) predicate : (a, b) -> predicate.test(a, b) && other.test(a, b);
        
        @Extension.Operator("|")
        static <A, B> @Nullable BiPredicate<A, B> or(final @Nullable BiPredicate<? super A, ? super B> predicate, final @Nullable BiPredicate<? super A, ? super B> other)
            = predicate == null ? (BiPredicate<A, B>) other : other == null ? (BiPredicate<A, B>) predicate : (a, b) -> predicate.test(a, b) || other.test(a, b);
        
        @Extension.Operator("!")
        static <A, B> @Nullable BiPredicate<A, B> not(final @Nullable BiPredicate<? super A, ? super B> predicate)
            = predicate == null ? null : (a, b) -> !predicate.test(a, b);
        
        static int TILDE(final IntSupplier supplier) = supplier.getAsInt();
        
        static long TILDE(final LongSupplier supplier) = supplier.getAsLong();
        
        static double TILDE(final DoubleSupplier supplier) = supplier.getAsDouble();
        
        static boolean TILDE(final BooleanSupplier supplier) = supplier.getAsBoolean();
        
        static <T> T TILDE(final Supplier<T> supplier) = supplier.get();
        
        static <A, B> B GET(final Function<A, B> function, final A a) = function.apply(a);
        
        static <T> void GET(final Consumer<T> consumer, final T t) = consumer.accept(t);
        
        static <A, B> void PUT(final BiConsumer<A, B> consumer, final A a, final B b) = consumer.accept(a, b);
        
        static <A, B, R> R PUT(final BiFunction<A, B, R> function, final A a, final B b) = function.apply(a, b);
        
    }
    
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    class LazySupplier<T> implements Supplier<T> {
        
        // @formatter:off
        /* non-volatile */ @Nullable T instance;
        // @formatter:on
        
        @Default
        volatile @Nullable Supplier<T> supplier;
        
        @Override
        public T get() {
            if (supplier != null) // acquire
                synchronized (this) {
                    if (supplier != null) {
                        // noinspection DataFlowIssue
                        instance = supplier.get(); // happen-before
                        supplier = null; // release
                    }
                }
            // noinspection DataFlowIssue
            return instance;
        }
        
    }
    
    static <T> LazySupplier<T> lazy(final Supplier<T> supplier) = { supplier };
    
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
    
    static <A, B> Supplier<B> map(final Supplier<A> supplier, final Function<A, B> function) = () -> function.apply(supplier.get());
    
    static <U, R> Function<U, R> abandon(final Supplier<R> supplier) = u -> supplier.get();
    
    static <T> BinaryOperator<T> first() = (a, b) -> a;
    
    static <T> BinaryOperator<T> last() = (a, b) -> b;
    
    static Runnable nothing() = () -> { };
    
    static <T> Consumer<T> abandon() = _ -> { };
    
    static <T, R> Function<T, R> cast() = (Function<T, R>) Function.identity();
    
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
