package amadeus.maho.transform.mark;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import amadeus.maho.transform.handler.InitMarker;
import amadeus.maho.transform.mark.base.TransformMark;
import amadeus.maho.transform.mark.base.TransformMetadata;
import amadeus.maho.util.annotation.mark.IgnoredDefaultValue;

@TransformMark(InitMarker.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Init {
    
    boolean initialized() default false;
    
    boolean reflectionData() default false;
    
    String invokeMethod() default "";
    
    @IgnoredDefaultValue
    TransformMetadata metadata() default @TransformMetadata;
    
}
