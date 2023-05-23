package amadeus.maho.transform.mark.base;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.TYPE, ElementType.MODULE })
@Retention(RetentionPolicy.RUNTIME)
public @interface TransformProvider {
    
    @Target({ ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD })
    @Retention(RetentionPolicy.RUNTIME)
    @interface Exception { }
    
}
