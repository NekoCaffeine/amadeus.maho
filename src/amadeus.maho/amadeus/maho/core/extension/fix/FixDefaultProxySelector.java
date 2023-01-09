package amadeus.maho.core.extension.fix;

import java.net.Proxy;
import java.net.URI;
import java.util.List;

import amadeus.maho.lang.inspection.Fixed;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;

import sun.net.spi.DefaultProxySelector;

@TransformProvider
public class FixDefaultProxySelector {
    
    // Fixed NPE on KDE
    /*
        java.lang.NullPointerException: Cannot invoke "java.net.Proxy.address()" because "p" is null
            at java.base/sun.net.www.protocol.http.HttpURLConnection.plainConnect0(HttpURLConnection.java:1258)
            at java.base/sun.net.www.protocol.http.HttpURLConnection.plainConnect(HttpURLConnection.java:1128)
            at java.base/sun.net.www.protocol.https.AbstractDelegateHttpsURLConnection.connect(AbstractDelegateHttpsURLConnection.java:175)
            at java.base/sun.net.www.protocol.http.HttpURLConnection.getOutputStream0(HttpURLConnection.java:1430)
            at java.base/sun.net.www.protocol.http.HttpURLConnection.getOutputStream(HttpURLConnection.java:1401)
            at java.base/sun.net.www.protocol.https.HttpsURLConnectionImpl.getOutputStream(HttpsURLConnectionImpl.java:220)
     */
    @Fixed(domain = "openjdk", shortName = "libnet")
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static List<Proxy> select(final List<Proxy> capture, final DefaultProxySelector $this, final URI uri) = capture.stream().nonnull().toList(); // capture.contains(null) ? => NPE
    
}
