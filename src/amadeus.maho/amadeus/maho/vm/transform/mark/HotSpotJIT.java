package amadeus.maho.vm.transform.mark;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import amadeus.maho.transform.mark.base.TransformMark;
import amadeus.maho.transform.mark.base.TransformMetadata;
import amadeus.maho.util.annotation.mark.IgnoredDefaultValue;
import amadeus.maho.vm.tools.hotspot.jit.JITCompiler;
import amadeus.maho.vm.transform.handler.HotSpotJITMarker;

@TransformMark(HotSpotJITMarker.class)
@Target({ ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR })
@Retention(RetentionPolicy.RUNTIME)
public @interface HotSpotJIT {
    
    JITCompiler.Level value() default JITCompiler.Level.FULL_OPTIMIZATION;
    
    @IgnoredDefaultValue
    TransformMetadata meta() default @TransformMetadata;
    
}
