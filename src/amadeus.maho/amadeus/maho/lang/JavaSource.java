package amadeus.maho.lang;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import amadeus.maho.util.runtime.ObjectHelper;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface JavaSource {
    
    interface Attach {
        
        default JavaSource accessJavaSource() = ObjectHelper.requireNonNull(getClass().getAnnotation(JavaSource.class));
        
    }
    
    String importCode();
    
    String bodyCode();
    
    long time() default -1L;
    
}
