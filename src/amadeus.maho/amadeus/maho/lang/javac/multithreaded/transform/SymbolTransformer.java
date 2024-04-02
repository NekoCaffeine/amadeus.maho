package amadeus.maho.lang.javac.multithreaded.transform;

import java.security.ProtectionDomain;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.ClassTransformer;
import amadeus.maho.transform.mark.base.Transformer;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.context.TransformContext;

import static org.objectweb.asm.Opcodes.*;

@Transformer
public class SymbolTransformer implements ClassTransformer {
    
    private static final Set<String> methods = Set.of("getConstValue", "getRawAttributes");
    
    @Override
    public @Nullable ClassNode transform(final TransformContext context, @Nullable final ClassNode node, @Nullable final ClassLoader loader, @Nullable final Class<?> clazz, @Nullable final ProtectionDomain domain) {
        context.markModified();
        node.methods.stream()
                .filter(method -> method.name.equals("complete") && method.desc.equals(ASMHelper.VOID_METHOD_DESC) || methods[method.name])
                .forEach(method -> method.access |= ACC_SYNCHRONIZED);
        return node;
    }
    
    @Override
    public boolean isTarget(final @Nullable ClassLoader loader, final String name) = name.startsWith("com.sun.tools.javac.code.Symbol");
    
    @Override
    public boolean canAOT() = true;
    
}
