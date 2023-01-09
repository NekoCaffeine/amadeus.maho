package amadeus.maho.util.annotation.mark;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR })
@Retention(RetentionPolicy.RUNTIME)
public @interface HiddenDanger {
    
    String GC = "GC", JVM_DATA_STRUCTURE = "JVM data structure", JVM_NOT_CATCH = "JVM not catch", INVALID_BYTECODE = "Invalid bytecode";
    
    String value();
    
}
