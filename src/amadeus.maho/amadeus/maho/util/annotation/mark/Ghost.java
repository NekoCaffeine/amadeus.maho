package amadeus.maho.util.annotation.mark;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR })
@Retention(RetentionPolicy.RUNTIME)
public @interface Ghost {
    
    class TouchGhostError extends IncompatibleClassChangeError {
        
        public TouchGhostError() { }
        
        public TouchGhostError(final String message) = super(message);
    
    }
    
}
