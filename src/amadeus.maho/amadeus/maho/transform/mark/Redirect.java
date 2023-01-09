package amadeus.maho.transform.mark;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import amadeus.maho.transform.handler.RedirectTransformer;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.Slice;
import amadeus.maho.transform.mark.base.TransformMark;
import amadeus.maho.transform.mark.base.TransformMetadata;
import amadeus.maho.util.annotation.mark.DefaultClass;
import amadeus.maho.util.annotation.mark.IgnoredDefaultValue;
import amadeus.maho.util.annotation.mark.paradigm.AOP;

@AOP
@TransformMark(RedirectTransformer.class)
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Redirect {
    
    Slice slice();
    
    boolean direct() default false;
    
    @Remap.Class
    @IgnoredDefaultValue("target")
    Class<?> targetClass() default DefaultClass.class;
    
    // The fully qualified name of the set class
    @Remap.Class
    @IgnoredDefaultValue("target")
    String target() default "";
    
    // Target method name, if it is empty, use the current method name
    @Remap.Method
    @IgnoredDefaultValue
    String selector() default "";
    
    @Remap.Descriptor
    String descriptor() default At.Lookup.WILDCARD;
    
    @IgnoredDefaultValue
    TransformMetadata metadata() default @TransformMetadata;
    
}
