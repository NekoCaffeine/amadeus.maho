package amadeus.maho.intercept;

import java.lang.invoke.MethodType;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import amadeus.maho.lang.inspection.APIStatus;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.ClassTransformer;
import amadeus.maho.transform.TransformerManager;
import amadeus.maho.transform.mark.base.Experimental;
import amadeus.maho.transform.mark.base.Transformer;
import amadeus.maho.util.bytecode.Bytecodes;
import amadeus.maho.util.bytecode.context.TransformContext;
import amadeus.maho.util.bytecode.generator.MethodGenerator;
import amadeus.maho.util.tuple.Tuple;
import amadeus.maho.util.tuple.Tuple2;

import static amadeus.maho.util.bytecode.ASMHelper.*;
import static amadeus.maho.util.bytecode.FrameHelper.*;
import static org.objectweb.asm.Opcodes.*;

@Transformer
@Experimental
@APIStatus(design = APIStatus.Stage.α, implement = APIStatus.Stage.β)
public enum InterceptorManager implements ClassTransformer {
    
    @Getter
    instance;
    
    private static final Type TYPE_INTERCEPTOR_MANAGER = Type.getType(InterceptorManager.class);
    
    private static final Method
            ENTER = { "enter", Type.getMethodDescriptor(Type.VOID_TYPE, TYPE_CLASS, TYPE_STRING, TYPE_METHOD_TYPE, TYPE_OBJECT_ARRAY) },
            EXIT  = { "exit", VOID_METHOD_DESC };
    
    @Getter
    private final List<TransformInterceptor> handlers = new CopyOnWriteArrayList<>();
    
    public void addTransformInterceptor(final TransformInterceptor interceptor) {
        final List<TransformInterceptor> handlers = handlers();
        synchronized (handlers) {
            handlers += interceptor;
            TransformerManager.Patcher.patch(interceptor);
        }
    }
    
    public void removeTransformInterceptor(final TransformInterceptor interceptor) {
        final List<TransformInterceptor> handlers = handlers();
        synchronized (handlers) {
            handlers -= interceptor;
            TransformerManager.Patcher.patch(interceptor);
        }
    }
    
    public void addTransformInterceptors(final Collection<TransformInterceptor> interceptors) {
        final List<TransformInterceptor> handlers = handlers();
        synchronized (handlers) {
            final Collection<TransformInterceptor> ranges = interceptors.stream().filter(it -> !handlers.contains(it)).toList();
            handlers *= ranges;
            TransformerManager.Patcher.patch(ranges);
        }
    }
    
    public void removeTransformInterceptors(final Collection<TransformInterceptor> interceptors) {
        final List<TransformInterceptor> handlers = handlers();
        synchronized (handlers) {
            final Collection<TransformInterceptor> ranges = interceptors.stream().filter(handlers::contains).toList();
            handlers /= ranges;
            TransformerManager.Patcher.patch(ranges);
        }
    }
    
    public static void enter(final Class<?> clazz, final String name, final MethodType methodType, final Object... args) = instance().handlers().forEach(handler -> handler.enter(clazz, name, methodType, args));
    
    public static void exit() = instance().handlers().forEach(Interceptor::exit);
    
    @Override
    public ClassNode transform(final TransformContext context, final ClassNode node, final @Nullable ClassLoader loader, final @Nullable Class<?> clazz, final @Nullable ProtectionDomain domain) {
        final String name = sourceName(node.name);
        if (isTarget(loader, name))
            for (final MethodNode methodNode : node.methods)
                if (!methodNode.name.equals(_CLINIT_) && methodNode.instructions.size() != 0)
                    hookMethod(context, node, methodNode);
        return node;
    }
    
    // public void hookMethod(final TransformContext context, final ClassNode node, final MethodNode methodNode) {
    //     TransformerManager.transform("interceptor", sourceName(node.name) + "#" + methodNode.name + methodNode.desc);
    //     context.markModified();
    //     final boolean isInit = isInit(methodNode);
    //     // AbstractInsnNode superCall = null;
    //     // if (isInit)
    //     //     superCall = findSuperCall(methodNode, node.superName);
    //     // if (superCall != null)
    //     //     methodNode.instructions.insert(superCall, hookHead(node, methodNode));
    //     // else
    //         methodNode.instructions.insert(hookHead(node, methodNode));
    //     final ArrayList<AbstractInsnNode> tailNodes = { };
    //     for (final AbstractInsnNode insn : methodNode.instructions)
    //         if (Bytecodes.isReturn(insn.getOpcode()))
    //             tailNodes += insn;
    //     tailNodes.forEach(tailInsnNode -> methodNode.instructions.insertBefore(tailInsnNode, hookTail(node, methodNode)));
    //     final LabelNode start = { }, end = { };
    //     final TryCatchBlockNode tryCatchBlock = { start, end, end, null };
    //     methodNode.tryCatchBlocks += tryCatchBlock;
    //     // if (superCall != null)
    //     //     methodNode.instructions.insert(superCall, start);
    //     // else
    //         methodNode.instructions.insert(start);
    //     methodNode.instructions.add(end); // Throwable
    //     final boolean isStatic = anyMatch(methodNode.access, ACC_STATIC);
    //     final Object locals[] = isInit ? stack(UNINITIALIZED_THIS) : empty(), stack[] = stack("java/lang/Throwable");
    //     methodNode.instructions.add(new FrameNode(F_FULL, locals.length, locals, 1, stack("java/lang/Throwable")));
    //     methodNode.instructions.add(hookTail(node, methodNode)); // Throwable
    //     methodNode.instructions.add(new InsnNode(ATHROW)); // <EMPTY>
    //     methodNode.maxStack = Math.max(methodNode.maxStack, 6 + Stream.of(Type.getArgumentTypes(methodNode.desc)).mapToInt(Type::getSize).reduce(isStatic ? -2 : 1, Math::max));
    //     // Class<?>, String, MethodType, Object[], (Object[], I, ?)
    // }
    public void hookMethod(final TransformContext context, final ClassNode node, final MethodNode methodNode) {
        final boolean isInit = isInit(methodNode);
        if (isInit)
            return;
        TransformerManager.transform("interceptor", "%s#%s%s".formatted(sourceName(node.name), methodNode.name, methodNode.desc));
        context.markModified();
        AbstractInsnNode superCall = null;
        if (isInit)
            superCall = findSuperCall(methodNode, node.superName);
        // if (superCall != null)
        //     methodNode.instructions.insert(superCall, hookHead(node, methodNode));
        // else
        methodNode.instructions.insert(hookHead(node, methodNode));
        final ArrayList<AbstractInsnNode> tailNodes = { };
        for (final AbstractInsnNode insn : methodNode.instructions)
            if (Bytecodes.isReturn(insn.getOpcode()))
                tailNodes += insn;
        tailNodes.forEach(tailInsnNode -> methodNode.instructions.insertBefore(tailInsnNode, hookTail(node, methodNode)));
        final LabelNode start = { }, end = { };
        final TryCatchBlockNode tryCatchBlock = { start, end, end, null };
        methodNode.tryCatchBlocks += tryCatchBlock;
        if (superCall != null) {
            methodNode.instructions.insert(superCall, start);
            methodNode.instructions.insert(superCall, new InsnNode(NOP));
        } else
            methodNode.instructions.insert(start);
        methodNode.instructions.add(end); // Throwable
        final boolean isStatic = anyMatch(methodNode.access, ACC_STATIC);
        final Object locals[] = empty(), stack[] = array("java/lang/Throwable");
        methodNode.instructions.add(new FrameNode(F_FULL, locals.length, locals, 1, stack));
        methodNode.instructions.add(hookTail(node, methodNode)); // Throwable
        methodNode.instructions.add(new InsnNode(ATHROW)); // <EMPTY>
        if (superCall != null) {
            {
                final LabelNode startU = { }, endU = { }, handler = { };
                final TryCatchBlockNode tryCatchBlockU = { startU, endU, handler, null };
                methodNode.tryCatchBlocks += tryCatchBlockU;
                methodNode.instructions.insert(startU);
                methodNode.instructions.insertBefore(superCall, endU);
                methodNode.instructions.insertBefore(superCall, new InsnNode(NOP));
                methodNode.instructions.add(handler); // Throwable
                methodNode.instructions.add(new FrameNode(F_FULL, 1, array(UNINITIALIZED_THIS), 1, stack));
                methodNode.instructions.add(hookTail(node, methodNode)); // Throwable
                methodNode.instructions.add(new InsnNode(ATHROW)); // <EMPTY>
            }
            {
                final LabelNode startU = { }, endU = { }, handler = { };
                final TryCatchBlockNode tryCatchBlockU = { startU, endU, handler, null };
                methodNode.tryCatchBlocks += tryCatchBlockU;
                // methodNode.instructions.insertBefore(superCall, new InsnNode(NOP));
                methodNode.instructions.insertBefore(superCall, startU);
                methodNode.instructions.insert(superCall, endU);
                methodNode.instructions.add(handler); // Throwable
                methodNode.instructions.add(new FrameNode(F_FULL, 1, array(OBJECT_NAME), 1, stack));
                methodNode.instructions.add(hookTail(node, methodNode)); // Throwable
                methodNode.instructions.add(new InsnNode(ATHROW)); // <EMPTY>
            }
        }
        methodNode.maxStack = Math.max(methodNode.maxStack, 6 + Stream.of(Type.getArgumentTypes(methodNode.desc)).mapToInt(Type::getSize).reduce(isStatic ? -2 : 1, Math::max)); // Class<?>, String, MethodType, Object[], (Object[], I, ?)
    }
    
    public InsnList hookHead(final ClassNode node, final MethodNode methodNode) {
        final InsnList result = { };
        final MethodGenerator generator = MethodGenerator.fromShadowMethodNode(methodNode, result);
        generator.push(Type.getObjectType(node.name)); // Class<?>
        generator.push(methodNode.name); // Class<?>, String
        generator.push(Type.getMethodType(methodNode.desc)); // Class<?>, String, MethodType
        final Type args[] = Type.getArgumentTypes(methodNode.desc);
        final int offset = anyMatch(methodNode.access, ACC_STATIC) ? 0 : 1, argsLength = args.length + offset;
        generator.push(argsLength); // Class<?>, String, MethodType, I
        generator.newArray(TYPE_OBJECT); // Class<?>, String, MethodType, Object[]
        if (offset > 0 && !isInit(methodNode)) {
            generator.dup(TYPE_OBJECT); // Class<?>, String, MethodType, Object[], Object[]
            generator.push(0); // Class<?>, String, MethodType, Object[], Object[], I
            generator.loadThis(); // Class<?>, String, MethodType, Object[], Object[], I, Object
            generator.arrayStore(TYPE_OBJECT); // Class<?>, String, MethodType, Object[]
        }
        for (int i = offset; i < argsLength; i++) {
            generator.dup(TYPE_OBJECT); // Class<?>, String, MethodType, Object[], Object[]
            generator.push(i); // Class<?>, String, MethodType, Object[], Object[], I
            generator.loadArg(i - offset); // Class<?>, String, MethodType, Object[], Object[], I, ?
            generator.broadCast(args[i - offset], TYPE_OBJECT); // Class<?>, String, MethodType, Object[], Object[], I, Object
            generator.arrayStore(TYPE_OBJECT); // Class<?>, String, MethodType, Object[]
        }
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
    public boolean isTarget(final @Nullable ClassLoader loader, final String name) = handlers().stream().anyMatch(handler -> handler.isTarget(loader, name));
    
    public <I extends Interceptor> TransformInterceptor install(final Supplier<I> supplier, final BiPredicate<ClassLoader, String> predicate)
            = new TransformInterceptor.Base(predicate).let(this::addTransformInterceptor).let(it -> it.supplier(supplier));
    
    public Collection<TransformInterceptor> install(final Map<Supplier<? extends Interceptor>, BiPredicate<ClassLoader, String>> map) {
        final ArrayList<Runnable> deferred = { map.size() };
        final List<TransformInterceptor> result = map.entrySet().stream()
                .map(entry -> Tuple.tuple(new TransformInterceptor.Base<>(entry.getValue()), entry.getKey()))
                .peek(tuple -> deferred += () -> tuple.v1.supplier((Supplier<Interceptor>) tuple.v2))
                .map(Tuple2::v1)
                .collect(Collectors.toCollection(ArrayList::new));
        addTransformInterceptors(result);
        deferred.forEach(Runnable::run);
        return result;
    }
    
}
