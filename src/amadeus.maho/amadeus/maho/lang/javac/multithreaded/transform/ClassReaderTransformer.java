package amadeus.maho.lang.javac.multithreaded.transform;

import java.security.ProtectionDomain;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.ClassTransformer;
import amadeus.maho.transform.mark.base.Transformer;
import amadeus.maho.util.bytecode.context.TransformContext;

@Transformer
public class ClassReaderTransformer implements ClassTransformer.Limited {
    
    @Nullable
    @Override
    public ClassNode transform(final TransformContext context, @Nullable final ClassNode node, @Nullable final ClassLoader loader, @Nullable final Class<?> clazz, @Nullable final ProtectionDomain domain) {
        context.markModified();
        node.methods.stream()
                .filter(method -> method.name.equals("getEnclosingType"))
                .forEach(method -> method.access |= Opcodes.ACC_SYNCHRONIZED);
        return node;
    }
    
    @Override
    public Set<String> targets() = Set.of("com.sun.tools.javac.jvm.ClassReader$1");
    
}
