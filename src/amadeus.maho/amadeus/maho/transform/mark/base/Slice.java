package amadeus.maho.transform.mark.base;

import amadeus.maho.util.annotation.mark.IgnoredDefaultValue;

public @interface Slice {
    
    At value(); // start
    
    @IgnoredDefaultValue
    At end() default @At(ordinal = Integer.MIN_VALUE);
    
}
