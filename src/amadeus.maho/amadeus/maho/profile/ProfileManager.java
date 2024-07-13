package amadeus.maho.profile;

import java.lang.invoke.MethodType;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

import amadeus.maho.lang.Getter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.APIStatus;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.ClassTransformer;
import amadeus.maho.transform.TransformRange;
import amadeus.maho.transform.TransformerManager;
import amadeus.maho.transform.mark.base.Experimental;
import amadeus.maho.transform.mark.base.Transformer;
import amadeus.maho.util.bytecode.Bytecodes;
import amadeus.maho.util.bytecode.context.TransformContext;
import amadeus.maho.util.bytecode.generator.MethodGenerator;
import amadeus.maho.util.misc.Environment;

import static amadeus.maho.util.bytecode.ASMHelper.*;
import static amadeus.maho.util.bytecode.FrameHelper.*;
import static org.objectweb.asm.Opcodes.*;

@Transformer
@Experimental
@APIStatus(design = APIStatus.Stage.α, implement = APIStatus.Stage.β)
public enum ProfileManager implements ClassTransformer {
    
    @Getter
    instance;
    
    private static final Type TYPE_INTERCEPTOR_MANAGER = Type.getType(ProfileManager.class);
    
    private static final Method ENTER = { "enter", Type.getMethodDescriptor(Type.VOID_TYPE, TYPE_CLASS, TYPE_STRING, TYPE_METHOD_TYPE) }, EXIT = { "exit", VOID_METHOD_DESC };
    
    @Getter
    AtomicBoolean shouldProfiling = { Environment.local().lookup("amadeus.maho.profiling", false) };
    
    @Getter
    TransformRange.ObservationSet observationSet = { };
    
    @Getter
    CopyOnWriteArrayList<Profiler> profilers = { };
    
    public static void beforeProfiling(final Profiler profiler) = instance().profilers() += profiler;
    
    public static void afterProfiling(final Profiler profiler) = instance().profilers() -= profiler;
    
    public static AutoCloseable profiling(final Profiler profiler) {
        beforeProfiling(profiler);
        return () -> afterProfiling(profiler);
    }
    
    public static AutoCloseable profiling(final Supplier<Profiler> supplier, final Consumer<Profiler> consumer) {
        final Profiler profiler = ~supplier;
        beforeProfiling(profiler);
        return () -> {
            afterProfiling(profiler);
            consumer[profiler];
        };
    }
    
    @SneakyThrows
    public static void profilingIfNeeded(final Runnable runnable, final Supplier<Profiler> supplier, final Consumer<Profiler> consumer) {
        if (instance().shouldProfiling().get())
            try (final AutoCloseable _ = profiling(supplier, consumer)) { ~runnable; }
        else
            ~runnable;
    }
    
    public static void enter(final Class<?> clazz, final String name, final MethodType methodType) = instance().profilers().forEach(handler -> handler.enter(clazz, name, methodType));
    
    public static void exit() = instance().profilers().forEach(Profiler::exit);
    
    @Override
    public ClassNode transform(final TransformContext context, final ClassNode node, final @Nullable ClassLoader loader, final @Nullable Class<?> clazz, final @Nullable ProtectionDomain domain) {
        final String name = sourceName(node.name);
        if (isTarget(loader, name))
            for (final MethodNode methodNode : node.methods)
                if (!methodNode.name.equals(_CLINIT_) && methodNode.instructions.size() != 0)
                    hookMethod(context, node, methodNode);
        return node;
    }
    
    public void hookMethod(final TransformContext context, final ClassNode node, final MethodNode methodNode) {
        TransformerManager.transform("interceptor", STR."\{sourceName(node.name)}#\{methodNode.name}\{methodNode.desc}");
        context.markModified();
        final @Nullable AbstractInsnNode superCall = isInit(methodNode) ? findSuperCall(methodNode, node.superName) : null;
        if (superCall != null)
            methodNode.instructions.insert(superCall, hookHead(node, methodNode));
        else
            methodNode.instructions.insert(hookHead(node, methodNode));
        final ArrayList<AbstractInsnNode> tailNodes = { };
        for (final AbstractInsnNode insn : methodNode.instructions)
            if (Bytecodes.isReturn(insn.getOpcode()))
                tailNodes += insn;
        tailNodes.forEach(tailInsnNode -> methodNode.instructions.insertBefore(tailInsnNode, hookTail(node, methodNode)));
        final LabelNode start = { }, end = { };
        final TryCatchBlockNode tryCatchBlock = { start, end, end, null };
        methodNode.tryCatchBlocks += tryCatchBlock;
        if (superCall != null)
            methodNode.instructions.insert(superCall, start);
        else
            methodNode.instructions.insert(start);
        methodNode.instructions.add(end); // Throwable
        final Object locals[] = empty(), stack[] = array("java/lang/Throwable"); // TODO test empty locals
        methodNode.instructions.add(new FrameNode(F_FULL, locals.length, locals, 1, stack));
        methodNode.instructions.add(hookTail(node, methodNode)); // Throwable
        methodNode.instructions.add(new InsnNode(ATHROW)); // <EMPTY>
        methodNode.maxStack = Math.max(methodNode.maxStack, 3); // Class<?>, String, MethodType
    }
    
    public InsnList hookHead(final ClassNode node, final MethodNode methodNode) {
        final InsnList result = { };
        final MethodGenerator generator = MethodGenerator.fromShadowMethodNode(methodNode, result);
        generator.push(Type.getObjectType(node.name)); // Class<?>
        generator.push(methodNode.name); // Class<?>, String
        generator.push(Type.getMethodType(methodNode.desc)); // Class<?>, String, MethodType
        generator.invokeStatic(TYPE_INTERCEPTOR_MANAGER, ENTER, false); // <EMPTY>
        return result;
    }
    
    public InsnList hookTail(final ClassNode node, final MethodNode methodNode) {
        final InsnList result = { };
        final MethodGenerator generator = MethodGenerator.fromShadowMethodNode(methodNode, result);
        generator.invokeStatic(TYPE_INTERCEPTOR_MANAGER, EXIT, false);
        return result;
    }
    
    @Override
    public boolean isTarget(final @Nullable ClassLoader loader, final String name) = observationSet().isTarget(loader, name);
    
    @Override
    public boolean isTarget(final Class<?> clazz) = observationSet().isTarget(clazz);
    
}
