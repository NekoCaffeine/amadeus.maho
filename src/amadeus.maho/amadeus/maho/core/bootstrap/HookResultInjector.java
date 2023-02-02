package amadeus.maho.core.bootstrap;

import java.security.ProtectionDomain;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import amadeus.maho.core.Maho;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.inspection.Nullable;

import static amadeus.maho.util.math.MathHelper.max;
import static org.objectweb.asm.Opcodes.*;

public enum HookResultInjector implements Injector {
    
    @Getter
    instance;
    
    @Override
    public @Nullable byte[] transform(final @Nullable Module module, final @Nullable ClassLoader loader, final @Nullable String className,
            final @Nullable Class<?> classBeingRedefined, final @Nullable ProtectionDomain protectionDomain, final @Nullable byte[] bytecode) {
        if (classBeingRedefined != null && classBeingRedefined.getName().equals(target())) {
            Maho.debug("HookResultInjector -> jdk.internal.loader.BuiltinClassLoader");
            final ClassReader reader = { bytecode };
            final ClassNode node = { };
            reader.accept(node, 0);
            for (final MethodNode methodNode : node.methods)
                if (methodNode.name.equals("loadClassOrNull") && methodNode.desc.equals("(Ljava/lang/String;Z)Ljava/lang/Class;")) {
                    Maho.debug("HookResultInjector -> jdk.internal.loader.BuiltinClassLoader::loadClassOrNull");
                    final InsnList instructions = { };
                    instructions.add(new LdcInsnNode("amadeus.maho.transform.mark.Hook$Result"));
                    instructions.add(new VarInsnNode(ALOAD, 1));
                    instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Object", "equals", "(Ljava/lang/Object;)Z"));
                    final LabelNode label = { };
                    instructions.add(new JumpInsnNode(IFEQ, label));
                    instructions.add(new MethodInsnNode(INVOKESTATIC, "jdk/internal/loader/ClassLoaders", "bootLoader", "()Ljdk/internal/loader/BuiltinClassLoader;"));
                    instructions.add(new VarInsnNode(ALOAD, 1));
                    instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/ClassLoader", "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;"));
                    instructions.add(new InsnNode(ARETURN));
                    instructions.add(label);
                    instructions.add(new FrameNode(F_SAME, 0, null, 0, null));
                    methodNode.instructions.insert(instructions);
                    methodNode.maxStack = max(methodNode.maxStack, 1);
                }
            final ClassWriter writer = { 0 };
            node.accept(writer);
            return writer.toByteArray();
        }
        return null;
    }
    
    @Override
    public String target() = "jdk.internal.loader.BuiltinClassLoader";
    
}
