package amadeus.maho.lang;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.FileVisitOption;

import amadeus.maho.util.dynamic.CallerContext;

@Documented
@Repeatable
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ResourceBundle {
    
    interface Helper {
    
        static String location(final Class<?> caller = CallerContext.caller()) = caller.getAnnotation(ResourceBundle.class).value();
        
    }
    
    String value();
    
    FieldDefaults fieldDefaults() default @FieldDefaults;
    
    FileVisitOption[] visitOptions() default FileVisitOption.FOLLOW_LINKS;
    
}
