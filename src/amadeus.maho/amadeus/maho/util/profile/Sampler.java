package amadeus.maho.util.profile;

import java.lang.invoke.MethodType;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.Extension;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.mark.StateSnapshot;
import amadeus.maho.util.function.FunctionHelper;
import amadeus.maho.util.tuple.Tuple;
import amadeus.maho.util.tuple.Tuple2;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Sampler<T> {
    
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class Interceptor extends Sampler<String> implements amadeus.maho.intercept.Interceptor {
        
        ThreadLocal<Deque<Handle>> handleLocal = ThreadLocal.withInitial(ArrayDeque::new);
        
        @Override
        public void enter(final Class<?> clazz, final String name, final MethodType methodType, final Object... args) = handleLocal.get() << handle("%s#%s%s".formatted(clazz.getName(), name, methodType.descriptorString()));
        
        @Override
        public void exit() = handleLocal.get().pollLast()?.close();
        
    }
    
    @EqualsAndHashCode
    public record Frame(long start, long end, long total = end - start) implements Comparable<Frame> {
        
        @Override
        public int compareTo(final Frame other) = Long.compare(total(), other.total());
        
        @Override
        public String toString() = "%d ns".formatted(total);
        
    }
    
    public static class Empty<T> extends Sampler<T> {
        
        @Override
        public void submit(final T mark, final Frame frame) { }
        
    }
    
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PROTECTED)
    public class Handle implements StateSnapshot {
        
        final T mark;
        
        long start = now(), end;
        
        public long now() = System.nanoTime();
        
        @Override
        public void close() = submit(mark, new Frame(start, now()));
        
    }
    
    AtomicReference<Tuple2<T, Frame>> min = { }, max = { };
    
    AtomicLong total = { }, count = { };
    
    @Getter
    ConcurrentHashMap<T, ConcurrentLinkedQueue<Frame>> data = { };
    
    @Extension.Operator("GET")
    public Handle handle(final T mark) = { mark };
    
    public void submit(final T mark, final Frame frame) {
        total().addAndGet(frame.total());
        count().incrementAndGet();
        final Supplier<Tuple2<T, Frame>> lazy = FunctionHelper.lazy(() -> Tuple.tuple(mark, frame));
        min().trySetWhen(tuple -> tuple == null || tuple.v2.total() > frame.total(), lazy);
        max().trySetWhen(tuple -> tuple == null || tuple.v2.total() < frame.total(), lazy);
        data().computeIfAbsent(mark, key -> new ConcurrentLinkedQueue<>()) += frame;
    }
    
    public void clear() {
        min().set(null);
        max().set(null);
        total().set(0L);
        count().set(0L);
        data().clear();
    }
    
    @Override
    public String toString() {
        final long total = ~total(), count = ~count();
        if (count == 0L)
            return "<empty>";
        final Tuple2<T, Frame> min = ~min(), max = ~max();
        return "min: %s ns, max: %s ns, total: %d ms, count: %d, avg: %.3f ms".formatted(min.v1 + " => " + min.v2, max.v1 + " => " + max.v2, total / (int) 1e6, count, total / 1e6 / count);
    }
    
}
