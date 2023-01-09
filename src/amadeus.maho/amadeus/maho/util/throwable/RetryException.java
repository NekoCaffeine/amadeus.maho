package amadeus.maho.util.throwable;

import java.util.Collection;
import java.util.HashSet;
import java.util.concurrent.ExecutionException;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;

@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RetryException extends ExecutionException {
    
    Collection<Throwable> throwables;
    
    {
        final HashSet<String> set = { };
        throwables.stream().filter(throwable -> set.add(throwable.getMessage())).forEach(this::addSuppressed);
    }
    
}
