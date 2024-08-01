package amadeus.maho.util.control;

import java.util.function.Consumer;

import amadeus.maho.lang.Extension;

@Extension
public interface Let {
    
    static <T> T let(final T t, final Consumer<? super T> consumer) {
        consumer.accept(t);
        return t;
    }
    
}
