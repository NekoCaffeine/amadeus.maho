package amadeus.maho.transform;

import java.security.ProtectionDomain;
import java.util.Set;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.bytecode.context.TransformContext;

import org.objectweb.asm.tree.ClassNode;

public interface ClassTransformer extends TransformRange {
    
    interface Limited extends ClassTransformer {
    
        @Nullable ClassNode transform(TransformContext context, ClassNode node, @Nullable ClassLoader loader, @Nullable Class<?> clazz, @Nullable ProtectionDomain domain);
        
        default boolean limited() = true;
        
        Set<String> targets();
        
        @Override
        default boolean isTarget(final @Nullable ClassLoader loader, final String name) = targets().contains(name);
    
        @Override
        default boolean canAOT() = true;
        
    }
    
    @Nullable ClassNode transform(TransformContext context, @Nullable ClassNode node, @Nullable ClassLoader loader, @Nullable Class<?> clazz, @Nullable ProtectionDomain domain);
    
    default int compareTo(final ClassTransformer target) = 0;
    
    default boolean canAOT() = false;
    
    default AOTTransformer.Level aotLevel() = AOTTransformer.Level.OPEN_WORLD;
    
}
