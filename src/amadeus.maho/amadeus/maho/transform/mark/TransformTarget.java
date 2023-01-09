package amadeus.maho.transform.mark;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import amadeus.maho.transform.handler.TargetTransformer;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.MethodDescriptor;
import amadeus.maho.transform.mark.base.TransformMark;
import amadeus.maho.transform.mark.base.TransformMetadata;
import amadeus.maho.util.annotation.mark.DefaultClass;
import amadeus.maho.util.annotation.mark.IgnoredDefaultValue;

@TransformMark(TargetTransformer.class)
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TransformTarget {
    
    // The fully qualified name of the set class
    @Remap.Class
    @IgnoredDefaultValue("target")
    String target() default "";
    
    @IgnoredDefaultValue("target")
    Class<?> targetClass() default DefaultClass.class;
    
    @Remap.Method
    String selector() default At.Lookup.WILDCARD;
    
    @IgnoredDefaultValue
    MethodDescriptor desc() default @MethodDescriptor;
    
    @IgnoredDefaultValue
    TransformMetadata metadata() default @TransformMetadata;
    
}
