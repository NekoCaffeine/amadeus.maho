package amadeus.maho.util.function;

import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
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
        static Runnable then(final @Nullable Runnable runnable, final @Nullable Runnable after) {
            if (runnable == null)
                return after;
            if (after == null)
                return runnable;
            return () -> {
                after.run();
                runnable.run();
            };
        }
        
        @Extension.Operator("~")
        static void safeRun(final @Nullable Runnable runnable) {
            if (runnable != null)
                runnable.run();
        }
        
        @Extension.Operator("^")
        static void safeRun(final @Nullable Runnable runnable, final Consumer<Throwable> handler) {
            try {
                if (runnable != null)
                    runnable.run();
            } catch (final Throwable t) { handler.accept(t); }
        }
        
        @Extension.Operator(">")
        static <T> Consumer<T> then(final @Nullable Consumer<? super T> consumer, final @Nullable Consumer<? super T> after) {
            if (consumer == null)
                return (Consumer<T>) after;
            if (after == null)
                return (Consumer<T>) consumer;
            return t -> {
                consumer.accept(t);
                after.accept(t);
            };
        }
        
        static <T> T TILDE(final Supplier<T> supplier) = supplier.get();
        
        static <A, B> B GET(final Function<A, B> function, final A a) = function.apply(a);
        
        static <T> void GET(final Consumer<T> consumer, final T t) = consumer.accept(t);
        
    }
    
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    class LazySupplier<T> implements Supplier<T> {
        
        /* non-volatile */ @Nullable T instance;
        
        @Default
        volatile @Nullable Supplier<T> supplier;
        
        @Override
        public T get() {
            if (supplier != null) // acquire
                synchronized (this) {
                    if (supplier != null) {
                        instance = supplier.get(); // happen-before
                        supplier = null; // release
                    }
                }
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
