package amadeus.maho.core.extension;

import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public class ForeignAccess {
    
    @Hook
    private static Hook.Result implIsEnableNativeAccess(final Module $this) = Hook.Result.TRUE;
    
}
