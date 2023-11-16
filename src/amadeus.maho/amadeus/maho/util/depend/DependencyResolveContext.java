package amadeus.maho.util.depend;

import java.util.function.Predicate;

import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.ToString;

@ToString
@EqualsAndHashCode
public record DependencyResolveContext(Predicate<Project> exclude = _ -> false, Predicate<Project> allowMissing = _ -> false) {
    
    public static final DependencyResolveContext
            DEFAULT       = { },
            ALLOW_MISSING = { _ -> false, _ -> true };
    
}
