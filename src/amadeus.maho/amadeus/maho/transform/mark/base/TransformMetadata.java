package amadeus.maho.transform.mark.base;

import amadeus.maho.transform.AOTTransformer;
import amadeus.maho.util.annotation.mark.IgnoredDefaultValue;

public @interface TransformMetadata {
    
    int order() default 0;
    
    @IgnoredDefaultValue
    String[] enable() default { };
    
    @IgnoredDefaultValue
    String[] disable() default { };
    
    boolean remap() default true;
    
    AOTTransformer.Level aotLevel() default AOTTransformer.Level.OPEN_WORLD;
    
}
