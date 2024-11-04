package amadeus.maho.util.annotation.mark;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface IndirectCaller {
    
    Class<?>[] value() default { };
    
    boolean only() default false;
    
}
