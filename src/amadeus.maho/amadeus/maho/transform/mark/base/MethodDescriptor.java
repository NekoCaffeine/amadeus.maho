package amadeus.maho.transform.mark.base;

import java.util.List;

import org.objectweb.asm.Type;

import amadeus.maho.util.annotation.AnnotationHandler;
import amadeus.maho.util.annotation.mark.DisallowLoading;
import amadeus.maho.util.bytecode.remap.RemapHandler;

public @interface MethodDescriptor {
    
    interface Mapper {
        
        static String methodDescriptor(final MethodDescriptor descriptor, final RemapHandler.ASMRemapper remapper, final boolean remap) {
            final AnnotationHandler<MethodDescriptor> handler = AnnotationHandler.asOneOfUs(descriptor);
            final Type returnType = handler.<Type>lookupSourceValue(MethodDescriptor::value) ?? Type.VOID_TYPE,
                    argumentTypes[] = handler.<List<Type>>lookupSourceValue(MethodDescriptor::parameters)?.toArray(Type[]::new) ?? new Type[0];
            return remap ? remapper.mapDesc(Type.getMethodDescriptor(returnType, argumentTypes)) : Type.getMethodDescriptor(returnType, argumentTypes);
        }
        
    }
    
    @DisallowLoading
    Class<?> value() default void.class;
    
    @DisallowLoading
    Class<?>[] parameters() default { };
    
}
