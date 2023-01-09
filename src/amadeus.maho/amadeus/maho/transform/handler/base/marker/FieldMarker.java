package amadeus.maho.transform.handler.base.marker;

import java.lang.annotation.Annotation;
import java.security.ProtectionDomain;

import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.handler.base.FieldTransformer;
import amadeus.maho.util.bytecode.context.TransformContext;

import org.objectweb.asm.tree.ClassNode;

@RequiredArgsConstructor
public abstract class FieldMarker<A extends Annotation> extends FieldTransformer<A> implements Marker {
    
    { markHandleNullNode(); }
    
    @Override
    public @Nullable ClassNode doTransform(final TransformContext context, final @Nullable ClassNode node, final @Nullable ClassLoader loader, final @Nullable Class<?> clazz, final @Nullable ProtectionDomain domain) = null;
    
    @Override
    public boolean isTarget(final ClassLoader loader, final String name) = false;
    
    @Override
    public boolean valid() = false;
    
}
