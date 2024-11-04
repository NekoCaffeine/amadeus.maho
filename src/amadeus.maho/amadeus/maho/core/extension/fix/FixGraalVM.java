package amadeus.maho.core.extension.fix;

import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public interface FixGraalVM {
    
    @Hook(target = "org.graalvm.compiler.options.OptionKey", forceReturn = true)
    private static boolean checkDescriptorExists(final Object $this) = true;
    
}
