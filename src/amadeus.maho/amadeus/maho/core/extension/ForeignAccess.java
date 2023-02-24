package amadeus.maho.core.extension;

import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public class ForeignAccess {
    
    @Hook(forceReturn = true)
    private static boolean implIsEnableNativeAccess(final Module $this) = true;
    
}
