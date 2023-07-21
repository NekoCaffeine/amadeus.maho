package amadeus.maho.core.bootstrap;

import amadeus.maho.core.Maho;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.inspection.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.security.ProtectionDomain;
import java.util.List;

import static org.objectweb.asm.Opcodes.RETURN;

public enum UnsafeInjector implements Injector {
    
    @Getter
    instance;
    
    @Override
    public @Nullable byte[] transform(final @Nullable Module module, final @Nullable ClassLoader loader, final @Nullable String className,
            final @Nullable Class<?> classBeingRedefined, final @Nullable ProtectionDomain protectionDomain, final @Nullable byte[] bytecode) {
        if (className != null && className.equals(className()) || classBeingRedefined != null && classBeingRedefined.getName().equals(target())) {
            Maho.debug("UnsafeInjector -> jdk.internal.misc.Unsafe");
            final ClassReader reader = { bytecode };
            final ClassNode node = { };
            reader.accept(node, 0);
            for (final MethodNode methodNode : node.methods)
                if (methodNode.name.equals("checkPrimitiveArray")) {
                    Maho.debug("UnsafeInjector -> jdk.internal.misc.Unsafe::checkPrimitiveArray");
                    methodNode.localVariables = List.of();
                    methodNode.instructions.clear();
                    methodNode.instructions.add(new InsnNode(RETURN));
                    methodNode.maxStack = 0;
                    methodNode.maxLocals = 2;
                }
            final ClassWriter writer = { 0 };
            node.accept(writer);
            return writer.toByteArray();
        }
        return null;
    }
    
    @Override
    public String className() = "jdk/internal/misc/Unsafe";
    
    @Override
    public String target() = "jdk.internal.misc.Unsafe";
    
}
