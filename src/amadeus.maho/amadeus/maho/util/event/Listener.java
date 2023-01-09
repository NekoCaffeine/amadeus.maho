package amadeus.maho.util.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Listener {
    
    // Execution order from top to bottom
    enum Ordinal {
        
        TOP, // Just observing
        HIGHEST, // cancel event by mechanism
        HIGH, // cancel event by condition
        NORMAL, // default, addition, subtraction
        LOW, // multiplication
        LOWEST, // recovery
        BOTTOM // Just observing
        
    }
    
    Ordinal value() default Ordinal.NORMAL;
    
    boolean ignoreCanceled() default false;
    
}
