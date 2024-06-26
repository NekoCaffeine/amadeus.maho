package amadeus.maho.lang.inspection;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Inherited
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConstructorContract {
    
    @interface Parameters {
    
        Class<?>[] value();
    
    }
    
    Parameters[] value() default { };
    
    boolean anyMatch() default true;
    
}
