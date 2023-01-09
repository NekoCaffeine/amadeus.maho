package amadeus.maho.util.runtime;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import amadeus.maho.lang.Extension;

@Extension
public interface OptionalHelper {
    
    static <T> T NOT(final Optional<T> optional) = optional.orElseThrow();
    
    static int NOT(final OptionalInt optional) = optional.orElseThrow();
    
    static long NOT(final OptionalLong optional) = optional.orElseThrow();
    
    static double NOT(final OptionalDouble optional) = optional.orElseThrow();
    
    static <T> T TILDE(final Optional<T> optional) = optional.orElse(null);
    
    static int TILDE(final OptionalInt optional) = optional.orElse(0);
    
    static long TILDE(final OptionalLong optional) = optional.orElse(0L);
    
    static double TILDE(final OptionalDouble optional) = optional.orElse(0.0);
    
    static <T> Optional<T> cast(final Optional<?> optional, final Class<T> type) = (Optional<T>) optional.filter(type::isInstance);
    
}
