package amadeus.maho.lang;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToString {
    
    @Target({ ElementType.FIELD, ElementType.RECORD_COMPONENT })
    @Retention(RetentionPolicy.RUNTIME)
    @interface Mark { }
    
    Annotation[] on() default { };
    
}
