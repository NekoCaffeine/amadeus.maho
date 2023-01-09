package amadeus.maho.core.bootstrap;

import java.security.ProtectionDomain;

import amadeus.maho.core.Maho;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.inspection.Nullable;

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

import static amadeus.maho.util.math.MathHelper.*;
import static org.objectweb.asm.Opcodes.*;

public enum ReflectionInjector implements Injector {
    
    @Getter
    instance;
    
    @Override
    public @Nullable byte[] transform(final @Nullable Module module, final @Nullable ClassLoader loader, final @Nullable String className,
            final @Nullable Class<?> classBeingRedefined, final @Nullable ProtectionDomain protectionDomain, final @Nullable byte[] bytecode) {
        if (classBeingRedefined != null && classBeingRedefined.getName().equals("jdk.internal.reflect.Reflection")) {
            Maho.debug("ReflectionInjector -> jdk.internal.reflect.Reflection");
            final ClassReader reader = { bytecode };
            final ClassNode node = { };
            reader.accept(node, 0);
            for (final MethodNode methodNode : node.methods)
                if (methodNode.name.equals("verifyMemberAccess") && methodNode.desc.equals("(Ljava/lang/Class;Ljava/lang/Class;Ljava/lang/Class;I)Z")) {
                    Maho.debug("ReflectionInjector -> jdk.internal.reflect.Reflection::verifyMemberAccess");
                    final InsnList instructions = { };
                    instructions.add(new VarInsnNode(ALOAD, 0));
                    instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;"));
                    instructions.add(new LdcInsnNode("amadeus."));
                    instructions.add(new MethodInsnNode(INVOKEVIRTUAL, "java/lang/String", "startsWith", "(Ljava/lang/String;)Z"));
                    final LabelNode label = { };
                    instructions.add(new JumpInsnNode(IFEQ, label));
                    instructions.add(new InsnNode(ICONST_1));
                    instructions.add(new InsnNode(IRETURN));
                    instructions.add(label);
                    instructions.add(new FrameNode(F_SAME, 0, null, 0, null));
                    methodNode.instructions.insert(instructions);
                    methodNode.maxStack = max(methodNode.maxStack, 2);
                }
            final ClassWriter writer = { 0 };
            node.accept(writer);
            return writer.toByteArray();
        }
        return null;
    }
    
    @Override
    public String target() = "jdk.internal.reflect.Reflection";
    
}
