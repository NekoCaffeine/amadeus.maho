package amadeus.maho.core;

import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformMetadata;
import amadeus.maho.transform.mark.base.TransformProvider;

import static amadeus.maho.util.build.Jlink.MAHO_LINK_CONTEXT;

public interface MahoImage {
    
    String VARIABLE = "MAHO_IMAGE";
    
    @TransformProvider
    interface ImageTransformer {
        
        @Hook(direct = true, value = MahoImage.class, isStatic = true, forceReturn = true, metadata = @TransformMetadata(enable = MAHO_LINK_CONTEXT))
        static boolean isImage() = true;
        
    }
    
    static boolean isImage() = false;
    
}
