package amadeus.maho.lang;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import amadeus.maho.lang.inspection.RegularExpression;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ResourceAgent {
    
    enum Type { FILE, DIRECTORY }
    
    // path regular expressions
    @RegularExpression
    String value();
    
    String format() default "%s";
    
    Type[] types() default Type.FILE;
    
}
