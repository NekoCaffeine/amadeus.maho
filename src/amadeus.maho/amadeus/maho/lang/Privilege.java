package amadeus.maho.lang;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;

import amadeus.maho.util.dynamic.ClassLocal;

@Target({ ElementType.METHOD, ElementType.CONSTRUCTOR })
@Retention(RetentionPolicy.RUNTIME)
public @interface Privilege {
    
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Mark { }
    
    @SneakyThrows
    interface Outer {
        
        ClassLocal<Field> local = { it -> it.getDeclaredField("this$0"), true };
        
        static <T> T access(final Object object) = (T) local[object.getClass()].get(object);
        
    }
    
}
