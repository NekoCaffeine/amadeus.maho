package amadeus.maho.transform.mark;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import amadeus.maho.transform.handler.ProxyTransformer;
import amadeus.maho.transform.mark.base.TransformMark;
import amadeus.maho.transform.mark.base.TransformMetadata;
import amadeus.maho.util.annotation.mark.DefaultClass;
import amadeus.maho.util.annotation.mark.DisallowLoading;
import amadeus.maho.util.annotation.mark.IgnoredDefaultValue;

@TransformMark(ProxyTransformer.class)
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Proxy {
    
    // Opcodes INVOKESTATIC INVOKEVIRTUAL INVOKESPECIAL INVOKEINTERFACE NEW GETSTATIC PUTSTATIC GETFIELD PUTFIELD INSTANCEOF
    int value();
    
    boolean useHandle() default true;
    
    // itf => interface
    // Is interface method
    boolean itf() default false;
    
    // makeSiteByNameWithBoot
    boolean reverse() default false;
    
    @DisallowLoading
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
    
    @IgnoredDefaultValue
    TransformMetadata metadata() default @TransformMetadata;
    
}
