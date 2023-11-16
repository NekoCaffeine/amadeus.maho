package amadeus.maho.core.extension;

import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public class ForeignAccess {
    
    @Hook(value = Module.EnableNativeAccess.class, isStatic = true, forceReturn = true)
    private static boolean isNativeAccessEnabled(final Module $this) = true;
    
}
