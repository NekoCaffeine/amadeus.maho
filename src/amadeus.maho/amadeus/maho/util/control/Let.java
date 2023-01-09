package amadeus.maho.util.control;

import java.util.function.Consumer;

import amadeus.maho.lang.Extension;
import amadeus.maho.lang.inspection.Nullable;

@Extension
public interface Let {
    
    static <T> T let(final @Nullable T t, final Consumer<? super T> consumer) {
        consumer.accept(t);
        return t;
    }
    
}
