package amadeus.maho.transform.mark;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import amadeus.maho.transform.handler.PatchTransformer;
import amadeus.maho.transform.mark.base.TransformMark;
import amadeus.maho.transform.mark.base.TransformMetadata;
import amadeus.maho.util.annotation.mark.DefaultClass;
import amadeus.maho.util.annotation.mark.DisallowLoading;
import amadeus.maho.util.annotation.mark.IgnoredDefaultValue;
import amadeus.maho.util.annotation.mark.paradigm.AOP;

@AOP
@TransformMark(PatchTransformer.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Patch {
    
    @Target({ ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.FIELD })
    @Retention(RetentionPolicy.RUNTIME)
    @interface Exception { }
    
    @Target({ ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.FIELD })
    @Retention(RetentionPolicy.RUNTIME)
    @interface Spare { }
    
    @Target({ ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.FIELD })
    @Retention(RetentionPolicy.RUNTIME)
    @interface Specific { }
    
    @Target({ ElementType.CONSTRUCTOR, ElementType.METHOD, ElementType.FIELD })
    @Retention(RetentionPolicy.RUNTIME)
    @interface Remove { }
    
    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Generic {
        
        String value();
        
    }
    
    @Target(ElementType.CONSTRUCTOR)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Replace { }
    
    @Target(ElementType.CONSTRUCTOR)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Super { }
    
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Inline { }
    
    @DisallowLoading
    @Remap.Class
    @IgnoredDefaultValue("target")
    Class<?> value() default DefaultClass.class;
    
    @Remap.Class
    @IgnoredDefaultValue("target")
    String target() default "";
    
    boolean onlyFirstTime() default false;
    
    Remap remap() default @Remap;
    
    @IgnoredDefaultValue
    TransformMetadata metadata() default @TransformMetadata;
    
}
