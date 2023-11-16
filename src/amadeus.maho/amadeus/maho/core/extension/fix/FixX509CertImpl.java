package amadeus.maho.core.extension.fix;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Redirect;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.Slice;
import amadeus.maho.transform.mark.base.TransformProvider;

import sun.security.x509.CertificateExtensions;
import sun.security.x509.Extension;
import sun.security.x509.X509CertImpl;

@TransformProvider
public interface FixX509CertImpl {
    
    @Redirect(targetClass = X509CertImpl.class, selector = "getExtensionValue", slice = @Slice(@At(method = @At.MethodInsn(name = "getExtension"))))
    private static @Nullable Extension getExtension(final CertificateExtensions $this, final String extAlias) = $this?.getExtension(extAlias) ?? null;
    
}
