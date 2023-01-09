package amadeus.maho.lang;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.TYPE, ElementType.FIELD, ElementType.METHOD })
@Retention(RetentionPolicy.SOURCE)
public @interface Getter {
    
    AccessLevel value() default AccessLevel.PUBLIC;
    
    boolean lazy() default false;
    
    boolean nonStatic() default false;
    
    Annotation[] on() default { };
    
    Annotation[] onReference() default { };
    
}
