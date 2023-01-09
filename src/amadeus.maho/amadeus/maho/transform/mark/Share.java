package amadeus.maho.transform.mark;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import amadeus.maho.transform.handler.ShareMarker;
import amadeus.maho.transform.mark.base.TransformMark;
import amadeus.maho.transform.mark.base.TransformMetadata;
import amadeus.maho.util.annotation.mark.DefaultClass;
import amadeus.maho.util.annotation.mark.IgnoredDefaultValue;

@Share(erase = @Erase(method = true))
@TransformMark(ShareMarker.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Share { // Share class to boot class loader
    
    // Remapping is fully qualified of the target class fully qualified, default is not mapped
    @IgnoredDefaultValue("target")
    Class<?> value() default DefaultClass.class;
    
    @IgnoredDefaultValue("target")
    String target() default "";
    
    boolean privilegeEscalation() default false;
    
    boolean makePublic() default false;
    
    boolean shareAnonymousInnerClass() default true;
    
    String[] required() default { };
    
    @IgnoredDefaultValue
    Remap remap() default @Remap;
    
    @IgnoredDefaultValue
    Erase erase() default @Erase;
    
    @IgnoredDefaultValue
    Init init() default @Init;
    
    @IgnoredDefaultValue
    TransformMetadata metadata() default @TransformMetadata;
    
}
