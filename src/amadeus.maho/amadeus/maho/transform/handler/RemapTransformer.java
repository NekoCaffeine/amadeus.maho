package amadeus.maho.transform.handler;

import java.security.ProtectionDomain;
import java.util.Set;
import java.util.stream.Stream;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.ClassTransformer;
import amadeus.maho.transform.handler.base.BaseTransformer;
import amadeus.maho.transform.mark.Remap;
import amadeus.maho.util.annotation.AnnotationHandler;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.context.TransformContext;
import amadeus.maho.util.bytecode.remap.ClassNameRemapHandler;

import org.objectweb.asm.tree.ClassNode;

@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class RemapTransformer extends BaseTransformer<Remap> implements ClassTransformer.Limited {
    
    @Nullable ClassNameRemapHandler remapHandler = annotation.mapping().length > 0 ? ClassNameRemapHandler.of(AnnotationHandler.valueToMap(
            Stream.of(annotation.mapping()).map(name -> name.replace('.', '/')).toList())) : null;
    
    @Override
    public ClassNode doTransform(final TransformContext context, ClassNode node, final @Nullable ClassLoader loader, final @Nullable Class<?> clazz, final @Nullable ProtectionDomain domain) {
        if (manager.hasRemapHandlers()) {
            context.markModified();
            node = manager.mapClassNode(node);
        }
        if (remapHandler != null) {
            context.markModified();
            node = remapHandler.mapClassNode(node);
        }
        return node;
    }
    
    @Override
    public Set<String> targets() = Set.of(ASMHelper.sourceName(sourceClass.name));
    
}
