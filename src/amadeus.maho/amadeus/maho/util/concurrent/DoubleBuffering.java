package amadeus.maho.util.concurrent;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.Setter;

@Setter
@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DoubleBuffering<V> {
    
    final V first, second;
    
    volatile boolean index;
    
    final ReentrantLock fastLock = { true }, slowLock = { false };
    
    public DoubleBuffering(final Supplier<? extends V> provider) {
        first = provider.get();
        second = provider.get();
    }
    
    public V now() = index ? second : first;
    
    public V swap() = (index = !index) ? first : second;
    
    public void fast(final Consumer<? super V> consumer) {
        fastLock.lock();
        try {
            consumer.accept(now());
        } finally { fastLock.unlock(); }
    }
    
    public void slow(final Consumer<? super V> consumer) {
        slowLock.lock();
        try {
            fastLock.lock();
            final V backstage = swap();
            fastLock.unlock();
            consumer.accept(backstage);
        } finally { slowLock.unlock(); }
    }
    
    public Stream<V> stream() = Stream.of(first(), second());
    
}
