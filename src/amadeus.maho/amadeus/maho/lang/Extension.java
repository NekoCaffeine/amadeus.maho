package amadeus.maho.lang;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import amadeus.maho.lang.inspection.RegularExpression;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Extension {
    
    @Documented
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Operator {
        
        String value();
        
    }
    
    @Documented
    @Target(ElementType.MODULE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Provider { }
    
    @Documented
    @Target(ElementType.MODULE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Import {
        
        @RegularExpression
        String[] includes() default { "*" };
    
        @RegularExpression
        String[] exclusions() default { };
        
    }
    
}
