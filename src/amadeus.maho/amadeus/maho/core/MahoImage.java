package amadeus.maho.core;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;

import com.sun.jna.internal.ReflectionUtils;

import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformMetadata;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.runtime.MethodHandleHelper;

import static amadeus.maho.util.build.Jlink.MAHO_LINK_CONTEXT;

public interface MahoImage {
    
    String VARIABLE = "MAHO_IMAGE";
    
    @TransformProvider
    interface ImageTransformer {
        
        @Hook(direct = true, value = MahoImage.class, isStatic = true, forceReturn = true, metadata = @TransformMetadata(enable = MAHO_LINK_CONTEXT))
        static boolean isImage() = true;
        
        
        // JNA
        @SneakyThrows
        @Hook(value = ReflectionUtils.class, isStatic = true, forceReturn = true, metadata = @TransformMetadata(enable = MAHO_LINK_CONTEXT))
        static MethodHandle getMethodHandle(final Method method) = MethodHandleHelper.lookup().unreflect(method);
        
    }
    
    static boolean isImage() = false;
    
}
