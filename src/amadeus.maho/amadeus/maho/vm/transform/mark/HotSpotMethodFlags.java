package amadeus.maho.vm.transform.mark;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import amadeus.maho.transform.mark.base.TransformMark;
import amadeus.maho.transform.mark.base.TransformMetadata;
import amadeus.maho.util.annotation.mark.IgnoredDefaultValue;
import amadeus.maho.vm.transform.handler.HotSpotMethodFlagsMarker;

@TransformMark(HotSpotMethodFlagsMarker.class)
@Target({ ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR })
@Retention(RetentionPolicy.RUNTIME)
public @interface HotSpotMethodFlags {
    
    short value();
    
    short mask() default (short) 0xFFFF;
    
    @IgnoredDefaultValue
    TransformMetadata meta() default @TransformMetadata;
    
}
