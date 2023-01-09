package amadeus.maho.core.extension.fix;

import jdk.internal.net.http.Http1Exchange;

import amadeus.maho.lang.Privilege;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public interface FixHttp1Exchange {
    
    // subscription == null => NPE
    @Hook
    private static Hook.Result cancelSubscription(final Http1Exchange.Http1BodySubscriber $this) = Hook.Result.falseToVoid((Privilege) $this.subscription == null, null);
    
}
