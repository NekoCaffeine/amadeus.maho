package amadeus.maho.lang;

import amadeus.maho.lang.inspection.RegularExpression;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ResourceAgent {
    
    enum Type { FILE, DIRECTORY }
    
    // path regular expressions
    @RegularExpression
    String value();
    
    Type[] types() default Type.FILE;
    
}
