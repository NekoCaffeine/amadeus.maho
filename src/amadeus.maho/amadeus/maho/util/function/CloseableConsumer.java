package amadeus.maho.util.function;

import java.util.function.Consumer;

import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;

public interface CloseableConsumer<T> extends Consumer<T>, AutoCloseable {
    
    static <T> CloseableConsumer<T> of(final Consumer<T> consumer, final @Nullable AutoCloseable closeable) = new CloseableConsumer<>() {
        
        @Override
        @SneakyThrows
        public void close() throws Exception = closeable?.close();
    
        @Override
        public void accept(final T t) = consumer.accept(t);
    
    };
    
}
