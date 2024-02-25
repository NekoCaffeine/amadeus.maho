package amadeus.maho.util.profile;

import java.lang.invoke.MethodType;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.mark.StateSnapshot;
import amadeus.maho.profile.Profiler;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InvokeSampler<T> {
    
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class Interceptor extends InvokeSampler<String> implements Profiler {
        
        ThreadLocal<Deque<Handle>> handlesLocal = ThreadLocal.withInitial(ArrayDeque::new);
        
        @Override
        public void enter(final Class<?> clazz, final String name, final MethodType methodType) {
            final Deque<InvokeSampler<String>.Handle> handles = handlesLocal.get();
            handles << handle(handles.peekLast(), STR."\{clazz.getName()}#\{name}\{methodType.descriptorString()}");
        }
        
        @Override
        public void exit() = handlesLocal.get().pollLast()?.close();
        
    }
    
    @Getter
    @ToString
    @EqualsAndHashCode
    public static abstract class Frame implements Comparable<Frame> {
        
        long total;
        
        @Override
        public int compareTo(final Frame other) = Long.compare(other.total(), total());
        
        @Override
        public String toString() = "%d ms".formatted(total);
        
    }
    
    public static class Empty<T> extends InvokeSampler<T> {
        
        @Override
        public void submit(final T mark, final Frame frame) { }
        
    }
    
    @Getter
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PROTECTED)
    public class Handle extends Frame implements StateSnapshot {
        
        final @Nullable Handle prev;
        
        final T mark;
        
        long start = now(), end, cover;
        
        public long now() = System.currentTimeMillis();
        
        @Override
        public void close() {
            total = (end = now()) - start - cover;
            if (prev != null)
                prev.cover += total + cover;
            submit(mark, this);
        }
        
    }
    
    @Getter
    ConcurrentHashMap<T, ConcurrentLinkedQueue<Frame>> data = { };
    
    public Handle handle(final @Nullable Handle prev, final T mark) = { prev, mark };
    
    public void submit(final T mark, final Frame frame) = data().computeIfAbsent(mark, key -> new ConcurrentLinkedQueue<>()) += frame;
    
    public void clear() = data().clear();
    
    @Override
    public String toString() = data.entrySet().stream()
            .map(entry -> Map.entry(entry.getKey(), entry.getValue().stream().mapToLong(Frame::total).sum()))
            .sorted(Map.Entry.<T, Long>comparingByValue().reversed())
            .map(entry -> STR."\{entry.getKey()}: \{entry.getValue()} ms")
            .collect(Collectors.joining("\n"));
    
    
    
}
