package amadeus.maho.lang;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface NoArgsConstructor {
    
    AccessLevel value() default AccessLevel.PUBLIC;
    
    boolean varargs() default false;
    
    Annotation[] on() default { };
    
}
