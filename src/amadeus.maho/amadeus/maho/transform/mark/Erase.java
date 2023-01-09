package amadeus.maho.transform.mark;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import amadeus.maho.transform.handler.EraseTransformer;
import amadeus.maho.transform.mark.base.TransformMark;
import amadeus.maho.transform.mark.base.TransformMetadata;
import amadeus.maho.util.annotation.mark.IgnoredDefaultValue;

@TransformMark(EraseTransformer.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Erase {
    
    boolean field() default false;
    
    boolean method() default false;
    
    boolean annotation() default true;
    
    boolean innerClass() default false;
    
    int version() default -1;
    
    @IgnoredDefaultValue
    TransformMetadata metadata() default @TransformMetadata;
    
}
