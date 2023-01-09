package amadeus.maho.util.reference;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import amadeus.maho.core.Maho;
import amadeus.maho.lang.Extension;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.Bytecodes;
import amadeus.maho.util.bytecode.ClassWriter;
import amadeus.maho.util.bytecode.ComputeType;
import amadeus.maho.util.bytecode.generator.MethodGenerator;
import amadeus.maho.util.bytecode.remap.ClassNameRemapHandler;
import amadeus.maho.util.bytecode.remap.RemapHandler;
import amadeus.maho.util.bytecode.traverser.MethodTraverser;
import amadeus.maho.util.bytecode.traverser.exception.ComputeException;
import amadeus.maho.util.bytecode.tree.DynamicVarInsnNode;
import amadeus.maho.util.control.ProcessChain;
import amadeus.maho.util.dynamic.ClassLocal;
import amadeus.maho.util.dynamic.LookupHelper;
import amadeus.maho.util.dynamic.Wrapper;
import amadeus.maho.util.runtime.MethodHandleHelper;
import amadeus.maho.util.runtime.StreamHelper;
import amadeus.maho.util.runtime.TypeHelper;
import amadeus.maho.util.runtime.UnsafeHelper;
import amadeus.maho.util.tuple.Tuple;
import amadeus.maho.util.tuple.Tuple2;
import amadeus.maho.util.tuple.Tuple4;

import static amadeus.maho.util.bytecode.ASMHelper.*;
import static org.objectweb.asm.Opcodes.*;

public interface Reference {
    
    @Extension
    interface Ext {
        
        static <T> T TILDE(final java.lang.ref.Reference<T> reference) = reference.get();
        
    }
    
    @Extension
    interface Cleaner {
        
        @Getter
        java.lang.ref.Cleaner instance = java.lang.ref.Cleaner.create();
        
        static <T> T cleaning(final T target, final Runnable action) = target.let(it -> instance().register(it, action));
        
    }
    
    interface Processor {
        
        Type
                TYPE_ANY                      = Type.getType(Any.class),
                TYPE_OVERWRITER_ANY           = Type.getType(Overwriter.$Any.class),
                TYPE_OBSERVABLE_ANY           = Type.getType(Observable.$Any.class),
                TYPE_OBSERVER_ANY             = Type.getType(Observer.$Any.class),
                TYPE_COPY_ON_WRITE_ARRAY_LIST = Type.getType(CopyOnWriteArrayList.class);
        
        String
                FIELD_VALUE_NAME             = "value",
                METHOD_GETTER_NAME           = LookupHelper.<Readable<Object>, Object>method1(Readable::get).getName(),
                METHOD_OVERWRITE_NAME        = LookupHelper.<Overwriter<Object>, Object, Object>method2(Overwriter::overwrite).getName(),
                METHOD_OVERWRITE_VALUE_NAME  = LookupHelper.method2(Overwritable.Template::overwriteValue).getName(),
                METHOD_SETTER_NAME           = LookupHelper.<Mutable<Object>, Object>methodV2(Mutable::set).getName(),
                METHOD_ON_CHANGED_NAME       = LookupHelper.<Mutable<Object>, Observable<Object>, Object, Object>methodV4(Observer::onChanged).getName(),
                METHOD_OVERWRITERS_NAME      = LookupHelper.<Overwritable<Object>, List<Overwriter<Object>>>method1(Overwritable::overwriters).getName(),
                METHOD_OBSERVERS_NAME        = LookupHelper.<Observable<Object>, List<Observer<Object>>>method1(Observable::observers).getName(),
                METHOD_NOTIFY_OBSERVERS_NAME = LookupHelper.methodV3(Observable.Template::notifyObservers).getName();
        
        @Getter
        ProcessChain<Tuple4<Class<?>, Class<?>, Wrapper<?>, Integer>> processorChain = new ProcessChain<Tuple4<Class<?>, Class<?>, Wrapper<?>, Integer>>(StreamHelper.MatchType.ANY)
                .add(target -> target.filter(tuple -> Readable.class.isAssignableFrom(tuple.v1)).map(tuple -> {
                    final Type valueType = Type.getType(tuple.v2);
                    final FieldNode valueFieldNode = { ACC_PUBLIC | ACC_SYNTHETIC | tuple.v4, FIELD_VALUE_NAME, valueType.getDescriptor(), null, null };
                    final String getterDesc = Type.getMethodDescriptor(valueType);
                    final ClassNode node = tuple.v3.node();
                    if (node.fields.stream().noneMatch(field -> field.name.equals(valueFieldNode.name)))
                        node.fields += valueFieldNode;
                    generateGetter(node, node.name, valueFieldNode, ACC_PUBLIC | ACC_SYNTHETIC, METHOD_GETTER_NAME);
                    {
                        final MethodGenerator generator = MethodGenerator.visitMethod(node, ACC_PUBLIC | ACC_SYNTHETIC, METHOD_OVERWRITE_NAME, Type.getMethodDescriptor(valueType, valueType), null, null);
                        generator.loadThis();
                        generator.invokeVirtual(tuple.v3.wrapperType(), new Method(METHOD_GETTER_NAME, getterDesc));
                        generator.returnValue();
                        generator.endMethod();
                    }
                    return tuple.v1 == Readable.class;
                }).orElse(Boolean.FALSE))
                .add(target -> target.filter(tuple -> Mutable.class.isAssignableFrom(tuple.v1)).map(tuple -> {
                    final Type valueType = Type.getType(tuple.v2);
                    final FieldNode valueFieldNode = { ACC_PUBLIC | ACC_SYNTHETIC | tuple.v4, FIELD_VALUE_NAME, valueType.getDescriptor(), null, null };
                    final String setterDesc = Type.getMethodDescriptor(Type.VOID_TYPE, valueType);
                    final ClassNode node = tuple.v3.node();
                    if (node.fields.stream().noneMatch(field -> field.name.equals(valueFieldNode.name)))
                        node.fields += valueFieldNode;
                    generateSetter(node, node.name, valueFieldNode, ACC_PUBLIC | ACC_SYNTHETIC, METHOD_SETTER_NAME);
                    {
                        final MethodGenerator generator = MethodGenerator.visitMethod(tuple.v3.node(), ACC_PUBLIC | ACC_SYNTHETIC, METHOD_ON_CHANGED_NAME,
                                Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(Observable.class), valueType, valueType), null, null);
                        generator.loadThis();
                        generator.loadArg(2);
                        generator.invokeVirtual(tuple.v3.wrapperType(), new Method(METHOD_SETTER_NAME, setterDesc));
                        generator.returnValue();
                        generator.endMethod();
                    }
                    return tuple.v1 == Mutable.class;
                }).orElse(Boolean.FALSE))
                .add(target -> target.filter(tuple -> Overwritable.class.isAssignableFrom(tuple.v1)).map(tuple -> {
                    final Type valueType = Type.getType(tuple.v2);
                    final FieldNode overwritersFieldNode = { ACC_PUBLIC | ACC_FINAL | ACC_SYNTHETIC, METHOD_OVERWRITERS_NAME, TYPE_LIST.getDescriptor(), null, null };
                    final ClassNode node = tuple.v3.node();
                    node.fields += overwritersFieldNode;
                    final MethodNode initMethodNode = computeMethodNode(node, _INIT_, VOID_METHOD_DESC,
                            (name, desc) -> new MethodNode(ACC_PUBLIC | ACC_SYNTHETIC, name, desc, null, null).let(it -> {
                                final MethodGenerator generator = MethodGenerator.fromMethodNode(it);
                                generator.loadThis();
                                generator.invokeInit(tuple.v3.superType());
                                generator.returnValue();
                                generator.endMethod();
                            }));
                    injectInsnListByBytecodes(initMethodNode, Bytecodes::isReturn, true, generator -> {
                        generator.loadThis();
                        generator.newInstance(TYPE_COPY_ON_WRITE_ARRAY_LIST);
                        generator.dup(TYPE_COPY_ON_WRITE_ARRAY_LIST);
                        generator.invokeInit(TYPE_COPY_ON_WRITE_ARRAY_LIST);
                        generator.putField(tuple.v3.wrapperType(), overwritersFieldNode.name, TYPE_LIST);
                    });
                    generateGetter(node, node.name, overwritersFieldNode, ACC_PUBLIC | ACC_SYNTHETIC);
                    injectInsnListByBytecodes(lookupMethodNode(node, METHOD_GETTER_NAME, Type.getMethodDescriptor(valueType)).orElseThrow(), Bytecodes::isReturn, true, generator -> {
                        generator.loadThis();
                        generator.swap(valueType, TYPE_OBJECT);
                        generator.invokeVirtual(tuple.v3.wrapperType(), new Method(METHOD_OVERWRITE_VALUE_NAME, Type.getMethodType(valueType, valueType).getDescriptor()));
                    });
                    final String upper = tuple.v2.getName().upper(0);
                    applyTemplate(node, tuple.v3.writer(), Overwritable.Template.class, tuple.v2, Map.of(
                            TYPE_OVERWRITER_ANY.getInternalName(), TYPE_OVERWRITER_ANY.getInternalName().replace("$$Any", tuple.v2 == Object.class ? "" : "$" + upper)
                    ));
                    return tuple.v1 == Overwritable.class;
                }).orElse(Boolean.FALSE))
                .add(target -> target.filter(tuple -> Observable.class.isAssignableFrom(tuple.v1)).map(tuple -> {
                    final Type valueType = Type.getType(tuple.v2);
                    final FieldNode observersFieldNode = { ACC_PUBLIC | ACC_FINAL | ACC_SYNTHETIC, METHOD_OBSERVERS_NAME, TYPE_LIST.getDescriptor(), null, null };
                    final ClassNode node = tuple.v3.node();
                    node.fields += observersFieldNode;
                    final MethodNode initMethodNode = computeMethodNode(node, _INIT_, VOID_METHOD_DESC,
                            (name, desc) -> new MethodNode(ACC_PUBLIC | ACC_SYNTHETIC, name, desc, null, null).let(it -> {
                                final MethodGenerator generator = MethodGenerator.fromMethodNode(it);
                                generator.loadThis();
                                generator.invokeInit(tuple.v3.superType());
                                generator.returnValue();
                                generator.endMethod();
                            }));
                    injectInsnListByBytecodes(initMethodNode, Bytecodes::isReturn, true, generator -> {
                        generator.loadThis();
                        generator.newInstance(TYPE_COPY_ON_WRITE_ARRAY_LIST);
                        generator.dup(TYPE_COPY_ON_WRITE_ARRAY_LIST);
                        generator.invokeInit(TYPE_COPY_ON_WRITE_ARRAY_LIST);
                        generator.putField(tuple.v3.wrapperType(), observersFieldNode.name, TYPE_LIST);
                    });
                    generateGetter(node, node.name, observersFieldNode, ACC_PUBLIC | ACC_SYNTHETIC);
                    final int sourceValueIndex = 0;
                    final MethodNode setterMethodNode = lookupMethodNode(node, METHOD_SETTER_NAME, Type.getMethodDescriptor(Type.VOID_TYPE, valueType)).orElseThrow();
                    injectInsnList(setterMethodNode, generator -> {
                        generator.loadThis();
                        generator.getField(tuple.v3.wrapperType(), FIELD_VALUE_NAME, valueType);
                        generator.instructions().add(new DynamicVarInsnNode(valueType.getOpcode(ISTORE), sourceValueIndex));
                    });
                    injectInsnListByBytecodes(setterMethodNode, Bytecodes::isReturn, true, generator -> {
                        generator.loadThis();
                        generator.instructions().add(new DynamicVarInsnNode(valueType.getOpcode(ILOAD), sourceValueIndex));
                        generator.loadArg(0);
                        generator.invokeVirtual(tuple.v3.wrapperType(), new Method(METHOD_NOTIFY_OBSERVERS_NAME, Type.getMethodType(Type.VOID_TYPE, valueType, valueType).getDescriptor()));
                    });
                    DynamicVarInsnNode.normalizationInsnList(setterMethodNode.instructions);
                    final String upper = tuple.v2.getName().upper(0);
                    applyTemplate(node, tuple.v3.writer(), Observable.Template.class, tuple.v2, Map.of(
                            TYPE_OBSERVABLE_ANY.getInternalName(), TYPE_OBSERVABLE_ANY.getInternalName().replace("$$Any", tuple.v2 == Object.class ? "" : "$" + upper),
                            TYPE_OBSERVER_ANY.getInternalName(), TYPE_OBSERVER_ANY.getInternalName().replace("$$Any", tuple.v2 == Object.class ? "" : "$" + upper)
                    ));
                    return tuple.v1 == Observable.class;
                }).orElse(Boolean.FALSE))
                .add(target -> target.filter(tuple -> Puppet.class.isAssignableFrom(tuple.v1)).map(_ -> Boolean.TRUE).orElse(Boolean.FALSE));
        
        static void applyTemplate(final ClassNode node, final ClassWriter writer, final Class<?> template, final Class<?> type, final Map<String, String> localRemapMapping) {
            final Map<String, String> remapMapping = new HashMap<>();
            remapMapping.putAll(Map.of(
                    TYPE_ANY.getInternalName(), type == Object.class ? OBJECT_NAME : RemapHandler.PRIMITIVE_TYPE_PREFIX + Type.getType(type).getInternalName(),
                    className(template), node.name
            ));
            remapMapping.putAll(localRemapMapping);
            final RemapHandler remapHandler = ClassNameRemapHandler.of(remapMapping);
            Maho.getClassNodeFromClassNonNull(template).methods.stream()
                    .filter(methodNode -> noneMatch(methodNode.access, ACC_ABSTRACT | ACC_BRIDGE))
                    .map(methodNode -> generalization(writer, methodNode, remapHandler, type))
                    .peek(methodNode -> node.methods.removeIf(repeatNode -> repeatNode.name.equals(methodNode.name) && repeatNode.desc.equals(methodNode.desc)))
                    .forEach(node.methods::add);
        }
        
        MethodTraverser generalization = () -> (frame, exception) -> {
            if (exception instanceof ComputeException.TypeMismatch mismatch) {
                final AbstractInsnNode insn = frame.insn();
                final int baseType = Bytecodes.baseType(insn.getOpcode());
                switch (baseType) {
                    case ILOAD,
                            ISTORE -> {
                        final int opcode = mismatch.owner().type().getOpcode(baseType);
                        if (insn.getOpcode() != opcode)
                            insn.opcode(opcode);
                    }
                    case DUP,
                            DUP_X1,
                            DUP_X2 -> insn.opcode(DUP2 + baseType - DUP);
                }
            } else
                throw exception;
        };
        
        @SneakyThrows
        static MethodNode generalization(final ClassWriter writer, final MethodNode methodNode, final RemapHandler remapHandler, final Class<?> type) {
            final int size = Type.getType(type).getSize();
            final String owner = writer.name();
            if (size > 1) {
                final long mark[] = { 0L };
                final Type args[] = Type.getArgumentTypes(methodNode.desc);
                final boolean isStatic = anyMatch(methodNode.access, ACC_STATIC);
                for (int i = 0, offset = isStatic ? 0 : 1; i < args.length; i++) {
                    final Type arg = args[i];
                    if (arg.equals(TYPE_ANY))
                        mark[0] |= 1L << i + offset;
                    offset += arg.getSize() - 1;
                }
                MethodTraverser.instance().compute(methodNode, writer, frame -> {
                    if (frame.insn().getOpcode() == ASTORE) {
                        final int var = ((VarInsnNode) frame.insn()).var;
                        if (frame.load(var).type().equals(TYPE_ANY))
                            mark[0] |= 1L << var;
                    }
                });
                methodNode.instructions.forEach(insn -> {
                    if (insn instanceof VarInsnNode varInsn && varInsn.var > 0)
                        varInsn.var += Long.bitCount(mark[0] & ~0L >>> Long.SIZE - varInsn.var);
                    else if (insn instanceof IincInsnNode iincInsn && iincInsn.var > 0)
                        iincInsn.var += Long.bitCount(mark[0] & ~0L >>> Long.SIZE - iincInsn.var);
                });
            }
            final MethodNode result = remapHandler.mapMethodNode(owner, methodNode);
            result.instructions.forEach(insn -> {
                if (insn instanceof MethodInsnNode methodInsnNode) {
                    if (methodInsnNode.owner.equals(owner)) {
                        if (methodInsnNode.getOpcode() == INVOKEINTERFACE)
                            methodInsnNode.opcode(INVOKEVIRTUAL);
                        if (methodInsnNode.itf)
                            methodInsnNode.itf = false;
                    }
                } else if (insn instanceof InvokeDynamicInsnNode invokeDynamicInsnNode)
                    for (int i = 0, len = invokeDynamicInsnNode.bsmArgs.length; i < len; i++)
                        if (invokeDynamicInsnNode.bsmArgs[i] instanceof Handle handle)
                            if (handle.getOwner().equals(owner) && handle.isInterface())
                                invokeDynamicInsnNode.bsmArgs[i] = new Handle(handle.getTag() == H_INVOKEINTERFACE ? H_INVOKEVIRTUAL : handle.getTag(), handle.getOwner(), handle.getName(), handle.getDesc(), false);
            });
            final int returnOpcode = Type.getReturnType(result.desc).getOpcode(IRETURN);
            StreamHelper.fromIterable(result.instructions)
                    .filter(insn -> Bytecodes.isReturn(insn.getOpcode()))
                    .forEach(insn -> insn.opcode(returnOpcode));
            generalization.compute(result, writer, ComputeType.MAX, ComputeType.FRAME);
            result.instructions.resetLabels();
            return result;
        }
        
        @Getter
        MethodHandle allocateInstance = LookupHelper.<Class<?>, Object>methodHandle1(UnsafeHelper::allocateInstance);
        
        @SneakyThrows
        static ClassLocal<Tuple2<MethodHandle, MethodHandle>> instantiator(final String debugName, final int modifier) = {
                target -> {
                    final Wrapper<?> wrapper = { target, debugName };
                    final int index = target.getName().indexOf("$");
                    final String name = index == -1 ? target.getName() : target.getName().substring(0, index);
                    final Class<?> outerClass = index == -1 ? target : Class.forName(name, false, target.getClassLoader()), genericType = index == -1 ? Object.class :
                            Stream.of(TypeHelper.Wrapper.values())
                                    .map(TypeHelper.Wrapper::primitiveType)
                                    .filter(type -> type.getName().equalsIgnoreCase(target.getName().substring(index + 1)))
                                    .findAny()
                                    .orElseThrow(() -> illegalArgument(target));
                    if (!processorChain().process(Tuple.tuple(outerClass, genericType, wrapper, modifier)))
                        throw illegalArgument(target);
                    wrapper.context().markCompute(wrapper.node(), ComputeType.MAX, ComputeType.FRAME);
                    final Class<?> proxyClass = wrapper.defineWrapperClass();
                    final MethodHandle noArg = wrapper.node().methods.stream().anyMatch(ASMHelper::isInit) ?
                            MethodHandleHelper.lookup().findConstructor(proxyClass, MethodType.methodType(void.class)) : allocateInstance().bindTo(proxyClass).asType(MethodType.methodType(proxyClass));
                    final MethodHandle oneArg = MethodHandles.collectArguments(
                            MethodHandles.foldArguments(
                                    MethodHandles.dropArguments(MethodHandles.identity(noArg.type().returnType()), 1, genericType),
                                    MethodHandleHelper.lookup().findSetter(proxyClass, FIELD_VALUE_NAME, genericType)
                            ), 0, noArg);
                    return Tuple.tuple(noArg, oneArg);
                }
        };
        
        private static IllegalArgumentException illegalArgument(final Class<?> target) = { target.toString() };
        
        @Getter
        ClassLocal<Tuple2<MethodHandle, MethodHandle>> defaultInstantiator = instantiator("Default", 0), volatileInstantiator = instantiator("Volatile", ACC_VOLATILE);
        
    }
    
    @SneakyThrows
    static <T> T of(final Class<T> target) = (T) Processor.defaultInstantiator()[target].v1.invoke();
    
    @SneakyThrows
    static <T> T ofVolatile(final Class<T> target) = (T) Processor.volatileInstantiator()[target].v1.invoke();
    
    static <T> MethodHandle ofHandle(final Class<T> target) = Processor.defaultInstantiator()[target].v2;
    
    static <T> MethodHandle ofHandleVolatile(final Class<T> target) = Processor.volatileInstantiator()[target].v2;
    
}
