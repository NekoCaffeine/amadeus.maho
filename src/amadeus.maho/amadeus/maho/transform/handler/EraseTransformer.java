package amadeus.maho.transform.handler;

import java.security.ProtectionDomain;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.ClassTransformer;
import amadeus.maho.transform.TransformerManager;
import amadeus.maho.transform.handler.base.BaseTransformer;
import amadeus.maho.transform.mark.Erase;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.context.TransformContext;

@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class EraseTransformer extends BaseTransformer<Erase> implements ClassTransformer.Limited {
    
    @Override
    public ClassNode doTransform(final TransformContext context, final ClassNode node, final @Nullable ClassLoader loader, final @Nullable Class<?> clazz, final @Nullable ProtectionDomain domain) {
        TransformerManager.transform("erase", ASMHelper.sourceName(node.name));
        context.markModified();
        if (annotation.field())
            node.fields.clear();
        if (annotation.method())
            node.methods.clear();
        if (annotation.annotation()) {
            node.visibleAnnotations = null;
            node.visibleTypeAnnotations = null;
            for (final MethodNode method : node.methods) {
                method.visibleAnnotations = null;
                method.visibleAnnotableParameterCount = 0;
                method.visibleParameterAnnotations = null;
                method.visibleLocalVariableAnnotations = null;
                method.visibleTypeAnnotations = null;
            }
            for (final FieldNode field : node.fields) {
                field.visibleAnnotations = null;
                field.visibleTypeAnnotations = null;
            }
        }
        if (annotation.innerClass()) {
            node.innerClasses.clear();
            node.nestMembers.clear();
        }
        if (annotation.version() != -1)
            node.version = annotation.version();
        return node;
    }
    
    @Override
    public Set<String> targets() = Set.of(ASMHelper.sourceName(sourceClass.name));
    
}
