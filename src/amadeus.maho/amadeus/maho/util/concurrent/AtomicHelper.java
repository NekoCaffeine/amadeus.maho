package amadeus.maho.util.concurrent;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import amadeus.maho.lang.Extension;
import amadeus.maho.lang.inspection.Nullable;

@Extension
public interface AtomicHelper {
    
    static <V> void trySetWhen(final AtomicReference<V> $this, final Predicate<V> predicate, final UnaryOperator<V> mapper) {
        @Nullable V now;
        while (predicate.test(now = $this.get()))
            if ($this.compareAndSet(now, mapper.apply(now)))
                return;
    }
    
    static <V> void trySetWhen(final AtomicReference<V> $this, final Predicate<V> predicate, final Supplier<V> supplier) = trySetWhen($this, predicate, source -> supplier.get());
    
    static <V> @Nullable V TILDE(final AtomicReference<V> reference) = reference.get();
    
    static boolean TILDE(final AtomicBoolean reference) = reference.get();
    
    static int TILDE(final AtomicInteger reference) = reference.get();
    
    static long TILDE(final AtomicLong reference) = reference.get();
    
    static <E> @Nullable E GET(final AtomicReferenceArray<E> referenceArray, final int index) = referenceArray.get(index);
    
    static <E> void PUT(final AtomicReferenceArray<E> referenceArray, final int index, final @Nullable E value) = referenceArray.set(index, value);
    
}
