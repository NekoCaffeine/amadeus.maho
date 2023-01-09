package amadeus.maho.simulation.dynamic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.stream.Stream;

import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import amadeus.maho.core.Maho;
import amadeus.maho.core.MahoExport;
import amadeus.maho.core.extension.MagicAccessor;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.simulation.lookup.LookupSimulator;
import amadeus.maho.simulation.reflection.ReflectionSimulator;
import amadeus.maho.transform.TransformerManager;
import amadeus.maho.transform.mark.base.InvisibleType;
import amadeus.maho.util.annotation.mark.WIP;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.ClassWriter;
import amadeus.maho.util.bytecode.ComputeType;
import amadeus.maho.util.bytecode.context.TransformContext;
import amadeus.maho.util.bytecode.generator.MethodGenerator;
import amadeus.maho.util.container.MapTable;
import amadeus.maho.util.dynamic.DynamicMethod;

import static org.objectweb.asm.Opcodes.*;

@WIP
public interface DynamicSimulator {
    
    static @Nullable ClassNode transform(final TransformContext context, final @Nullable ClassNode node, final @Nullable ClassLoader loader, final @Nullable Class<?> clazz, final @Nullable ProtectionDomain domain) {
        boolean change = false;
        if (clazz != null) {
            ReflectionSimulator.clearCache(clazz);
            final Field fields[] = ReflectionSimulator.getDeclaredFields(clazz);
            final Method methods[] = ReflectionSimulator.getDeclaredMethods(clazz);
            final Constructor<?> constructors[] = ReflectionSimulator.getDeclaredConstructors(clazz);
            change |= fixMiss(fields, node.fields, clazz);
            change |= fixMiss(methods, node.methods, clazz);
            change |= fixMiss(constructors, node.methods, clazz);
            final List<FieldNode> fieldNodes = node.fields.stream()
                    .filter(fieldNode -> checkRedirect(clazz, loader, node, fields, fieldNode))
                    .toList();
            final List<MethodNode> methodNodes = node.methods.stream()
                    .filter(methodNode -> !methodNode.name.equals(ASMHelper._INIT_))
                    .filter(methodNode -> checkRedirect(clazz, loader, node, methods, methodNode))
                    .toList();
            final List<MethodNode> initNodes = node.methods.stream()
                    .filter(methodNode -> methodNode.name.equals(ASMHelper._INIT_))
                    .filter(methodNode -> checkRedirect(clazz, loader, node, constructors, methodNode))
                    .toList();
            if (fieldNodes.size() != 0) {
                node.fields /= fieldNodes;
                change = true;
            }
            if (methodNodes.size() != 0) {
                node.methods /= methodNodes;
                change = true;
            }
            if (initNodes.size() != 0) {
                node.methods /= initNodes;
                change = true;
            }
        }
        change |= applyRedirect(node);
        if (change) {
            TransformerManager.transform("simulation", ASMHelper.sourceName(node.name));
            context.markModified();
        }
        return node;
    }
    
    String
            WEAK_MAP_NAME      = "$redirectMapping",
            GET_METHOD         = "$get",
            SET_METHOD         = "$set",
            STATIC_FIELD       = "$redirect",
            CONSTRUCTOR_METHOD = "$constructor";
    
    Type TYPE_WEAK_HASH_MAP = Type.getType(WeakHashMap.class);
    
    @Getter
    MapTable<String, String, String> redirectTable = MapTable.newHashMapTable();
    
    private static String[] mapTypes(final Class<?> classes[]) = Stream.of(classes).map(ASMHelper::className).toArray(String[]::new);
    
    static boolean fixMiss(final Field fields[], final List<FieldNode> fieldNodes, final Class<?> clazz) {
        boolean result = false;
        for (final Field field : fields)
            result |= fixMiss(fields, field, fieldNodes, clazz);
        return result;
    }
    
    static boolean fixMiss(final Field fields[], final Field field, final List<FieldNode> fieldNodes, final Class<?> clazz) {
        if (fieldNodes.stream().noneMatch(fieldNode -> corresponding(field, fieldNode))) {
            // The order of the fields in HotSpot will be strictly checked
            fieldNodes.add(List.of(fields).indexOf(field), new FieldNode(field.getModifiers(), field.getName(), Type.getType(field.getType()).getDescriptor(), ReflectionSimulator.signature(field), null));
            ReflectionSimulator.addHiddenMember(clazz, field.getName());
            return true;
        }
        return false;
    }
    
    static boolean fixMiss(final Method methods[], final List<MethodNode> methodNodes, final Class<?> clazz) {
        boolean result = false;
        for (final Method method : methods)
            result |= fixMiss(methods, method, methodNodes, clazz);
        return result;
    }
    
    static boolean fixMiss(final Method methods[], final Method method, final List<MethodNode> methodNodes, final Class<?> clazz) {
        if (methodNodes.stream()
                .filter(methodNode -> !methodNode.name.equals(ASMHelper._INIT_))
                .noneMatch(methodNode -> corresponding(method, methodNode))) {
            final String desc = Type.getMethodDescriptor(method);
            final MethodNode inject = { method.getModifiers(), method.getName(), desc, ReflectionSimulator.signature(method), mapTypes(method.getExceptionTypes()) };
            final MethodGenerator generator = MethodGenerator.fromMethodNode(inject);
            generator.throwException(ASMHelper.TYPE_INCOMPATIBLE_CLASS_CHANGE_ERROR, "Removed method");
            generator.endMethod();
            methodNodes += inject;
            ReflectionSimulator.addHiddenMember(clazz, method.getName() + desc);
            return true;
        }
        return false;
    }
    
    static boolean fixMiss(final Constructor<?> constructors[], final List<MethodNode> methodNodes, final Class<?> clazz) {
        boolean result = false;
        for (final Constructor<?> constructor : constructors)
            result |= fixMiss(constructors, constructor, methodNodes, clazz);
        return result;
    }
    
    static boolean fixMiss(final Constructor<?> constructors[], final Constructor<?> constructor, final List<MethodNode> methodNodes, final Class<?> clazz) {
        if (methodNodes.stream()
                .filter(methodNode -> methodNode.name.equals(ASMHelper._INIT_))
                .noneMatch(methodNode -> corresponding(constructor, methodNode))) {
            final String desc = Type.getConstructorDescriptor(constructor);
            final MethodNode inject = { constructor.getModifiers(), ASMHelper._INIT_, desc, ReflectionSimulator.signature(constructor), mapTypes(constructor.getExceptionTypes()) };
            final MethodGenerator generator = MethodGenerator.fromMethodNode(inject);
            generator.loadThis();
            generator.invokeInit(ASMHelper.TYPE_OBJECT);
            generator.throwException(ASMHelper.TYPE_INCOMPATIBLE_CLASS_CHANGE_ERROR, "Removed constructor");
            generator.endMethod();
            methodNodes += inject;
            ReflectionSimulator.addHiddenMember(clazz, ASMHelper._INIT_ + desc);
            return true;
        }
        return false;
    }
    
    static boolean checkRedirect(final Class<?> owner, final @Nullable ClassLoader loader, final ClassNode node, final Field fields[], final FieldNode fieldNode) {
        if (Stream.of(fields).anyMatch(field -> corresponding(field, fieldNode)))
            return false;
        if (owner == Throwable.class && fieldNode.name.equals("backtrace")) // hardcode jvm internal field
            return false;
        final String redirectTarget = lookupRedirectTarget(node, fieldNode);
        if (!redirectTable().containsValue(redirectTarget)) {
            final Type ownerType = Type.getObjectType(node.name), fieldType = Type.getType(fieldNode.desc);
            final ClassNode targetNode = { };
            final Type targetType = Type.getObjectType(redirectTarget);
            targetNode.name = redirectTarget;
            targetNode.superName = ASMHelper.OBJECT_NAME;
            targetNode.access = ACC_PUBLIC | ACC_SYNTHETIC | ACC_SUPER;
            targetNode.version = MahoExport.bytecodeVersion();
            final FieldNode mapField = { ACC_PUBLIC | ACC_STATIC | ACC_FINAL | ACC_SYNTHETIC, WEAK_MAP_NAME, TYPE_WEAK_HASH_MAP.getDescriptor(), null, null };
            targetNode.fields += mapField;
            final FieldNode staticField = { ACC_PUBLIC | ACC_STATIC | ACC_SYNTHETIC, STATIC_FIELD, fieldNode.desc, null, null };
            targetNode.fields += staticField;
            { // void <clinit>()
                final MethodGenerator generator = MethodGenerator.visitMethod(targetNode, ACC_STATIC | ACC_SYNTHETIC, ASMHelper._CLINIT_, ASMHelper.VOID_METHOD_DESC, null, null);
                generator.newInstance(TYPE_WEAK_HASH_MAP);
                generator.dup();
                generator.invokeConstructor(TYPE_WEAK_HASH_MAP, new org.objectweb.asm.commons.Method(
                        ASMHelper._INIT_, ASMHelper.VOID_METHOD_DESC));
                generator.putStatic(targetType, mapField.name, TYPE_WEAK_HASH_MAP);
                generator.returnValue();
                generator.endMethod();
            }
            { // T get(O)
                final MethodGenerator generator = MethodGenerator.visitMethod(targetNode, ACC_PUBLIC | ACC_STATIC | ACC_SYNTHETIC, GET_METHOD, Type.getMethodDescriptor(fieldType, ownerType), null, null);
                generator.getStatic(targetType, WEAK_MAP_NAME, TYPE_WEAK_HASH_MAP);
                generator.loadArg(0);
                generator.checkCast(ownerType);
                generator.invokeVirtual(TYPE_WEAK_HASH_MAP, new org.objectweb.asm.commons.Method("get", Type.getMethodDescriptor(ASMHelper.TYPE_OBJECT, ASMHelper.TYPE_OBJECT)));
                if (fieldType.getSort() == Type.OBJECT || fieldType.getSort() == Type.ARRAY)
                    generator.checkCast(fieldType);
                else {
                    final Label nonnull = generator.newLabel();
                    generator.dup();
                    generator.ifNonNull(nonnull);
                    generator.pop();
                    generator.pushDefaultLdc(fieldType);
                    generator.returnValue();
                    generator.mark(nonnull);
                    generator.unbox(fieldType);
                }
                generator.returnValue();
                generator.endMethod();
            }
            { // void set(O, T)
                final MethodGenerator generator = MethodGenerator.visitMethod(targetNode, ACC_PUBLIC | ACC_STATIC | ACC_SYNTHETIC, SET_METHOD, Type.getMethodDescriptor(Type.VOID_TYPE, ownerType, fieldType), null, null);
                generator.getStatic(targetType, WEAK_MAP_NAME, TYPE_WEAK_HASH_MAP);
                generator.loadArg(0);
                generator.checkCast(ownerType);
                generator.loadArg(1);
                if (fieldType.getSort() == Type.OBJECT || fieldType.getSort() == Type.ARRAY)
                    generator.checkCast(fieldType);
                else
                    generator.box(fieldType);
                generator.invokeVirtual(TYPE_WEAK_HASH_MAP, new org.objectweb.asm.commons.Method("put", Type.getMethodDescriptor(ASMHelper.TYPE_OBJECT, ASMHelper.TYPE_OBJECT, ASMHelper.TYPE_OBJECT)));
                generator.pop();
                generator.returnValue();
                generator.endMethod();
            }
            { // T get() & void set(T)
                ASMHelper.generateStaticProxy(node, redirectTarget, staticField, ACC_PUBLIC, GET_METHOD, SET_METHOD);
            }
            final @InvisibleType(DynamicMethod.Delegating.DelegatingClassLoader) ClassLoader delegatingLoader = DynamicMethod.Delegating.delegating(owner.getClassLoader());
            final ClassWriter writer = { delegatingLoader };
            final Class<?> simulatorClass = Maho.shareClass(ASMHelper.sourceName(targetNode.name), writer.toBytecode(targetNode, ComputeType.FRAME), delegatingLoader);
            // ClassLoaderExtender.extendedClasses().put(owner.getClassLoader(), simulatorClass.getName(), simulatorClass);
            redirectTable().put(node.name, fieldNode.name, targetNode.name);
        }
        ReflectionSimulator.addInjectField(owner, fieldNode);
        LookupSimulator.addInjectField(owner, fieldNode);
        return true;
    }
    
    static String proxyMethodName(final String name) = name.replace('<', '_').replace('>', '_');
    
    static boolean checkRedirect(final Class<?> owner, final @Nullable ClassLoader loader, final ClassNode node, final Executable executables[], final MethodNode methodNode) {
        if (methodNode.name.equals(ASMHelper._CLINIT_))
            return false;
        if (Stream.of(executables).anyMatch(method -> corresponding(method, methodNode)))
            return false;
        final String redirectTarget = lookupRedirectTarget(node, methodNode);
        if (!redirectTable().containsValue(redirectTarget)) {
            final Type ownerType = Type.getObjectType(node.name);
            final ClassNode targetNode = { };
            targetNode.name = redirectTarget;
            targetNode.superName = MagicAccessor.Bridge;
            targetNode.access = ACC_PUBLIC | ACC_SYNTHETIC;
            targetNode.version = MahoExport.bytecodeVersion();
            final MethodNode proxy = { ASMHelper.changeAccess(methodNode.access, ACC_PUBLIC) | ACC_SYNTHETIC, proxyMethodName(methodNode.name), methodNode.desc, methodNode.signature, methodNode.exceptions.toArray(String[]::new) };
            if (ASMHelper.noneMatch(proxy.access, ACC_STATIC)) {
                proxy.access |= ACC_STATIC;
                proxy.desc = Type.getMethodDescriptor(Type.getReturnType(proxy.desc),
                        Stream.concat(Stream.of(ownerType), Stream.of(Type.getArgumentTypes(proxy.desc))).toArray(Type[]::new));
            }
            methodNode.accept(proxy);
            targetNode.methods += proxy;
            if (methodNode.name.equals(ASMHelper._INIT_)) {
                final MethodNode constructor = { ACC_PUBLIC | ACC_STATIC | ACC_SYNTHETIC, CONSTRUCTOR_METHOD, Type.getMethodDescriptor(ownerType, Type.getArgumentTypes(methodNode.desc)), null, methodNode.exceptions.toArray(String[]::new) };
                final MethodGenerator generator = MethodGenerator.fromMethodNode(constructor);
                generator.newInstance(ownerType);
                generator.dup();
                generator.invokeStatic(ownerType, new org.objectweb.asm.commons.Method(CONSTRUCTOR_METHOD, proxy.desc), false);
                generator.returnValue();
                generator.endMethod();
                targetNode.methods += constructor;
            }
            final @InvisibleType(DynamicMethod.Delegating.DelegatingClassLoader) ClassLoader delegatingLoader = DynamicMethod.Delegating.delegating(owner.getClassLoader());
            final ClassWriter writer = { delegatingLoader };
            final Class<?> simulatorClass = Maho.shareClass(ASMHelper.sourceName(targetNode.name), writer.toBytecode(targetNode, ComputeType.FRAME), delegatingLoader);
            // ClassLoaderExtender.extendedClasses().put(owner.getClassLoader(), simulatorClass.getName(), simulatorClass);
            redirectTable().put(node.name, methodNode.name + methodNode.desc, targetNode.name);
        }
        ReflectionSimulator.addInjectMethod(owner, methodNode);
        LookupSimulator.addInjectMethod(owner, methodNode);
        return true;
    }
    
    static boolean applyRedirect(final ClassNode node) {
        boolean result = false;
        for (final MethodNode method : node.methods) {
            final Map<AbstractInsnNode, AbstractInsnNode> mapping = new HashMap<>();
            for (final AbstractInsnNode insn : method.instructions) {
                if (insn instanceof FieldInsnNode fieldInsnNode) {
                    final String redirect = redirectTable().get(fieldInsnNode.owner, fieldInsnNode.name);
                    if (redirect != null) {
                        final Type ownerType = Type.getObjectType(fieldInsnNode.owner), fieldType = Type.getType(fieldInsnNode.desc);
                        mapping.put(fieldInsnNode, switch (fieldInsnNode.getOpcode()) {
                            case GETFIELD  -> new MethodInsnNode(INVOKESTATIC, redirect, GET_METHOD, Type.getMethodDescriptor(fieldType, ownerType), false);
                            case PUTFIELD  -> new MethodInsnNode(INVOKESTATIC, redirect, SET_METHOD, Type.getMethodDescriptor(Type.VOID_TYPE, ownerType, fieldType), false);
                            case GETSTATIC -> new FieldInsnNode(GETSTATIC, redirect, STATIC_FIELD, fieldInsnNode.desc);
                            case PUTSTATIC -> new FieldInsnNode(PUTSTATIC, redirect, STATIC_FIELD, fieldInsnNode.desc);
                            default        -> throw new UnsupportedOperationException("Unsupported opcode: " + fieldInsnNode.getOpcode() + "/" + ASMHelper.OPCODE_LOOKUP.lookupFieldName(fieldInsnNode.getOpcode()));
                        });
                    }
                }
                if (insn instanceof MethodInsnNode methodInsnNode)
                    if (methodInsnNode.getOpcode() != INVOKEDYNAMIC) {
                        final String redirect = redirectTable().get(methodInsnNode.owner, methodInsnNode.name + methodInsnNode.desc);
                        if (redirect != null) {
                            final Type ownerType = Type.getObjectType(methodInsnNode.owner);
                            mapping.put(methodInsnNode, switch (methodInsnNode.getOpcode()) {
                                case INVOKESTATIC    -> new MethodInsnNode(INVOKESTATIC, redirect, proxyMethodName(methodInsnNode.name), methodInsnNode.desc, false);
                                case INVOKEVIRTUAL,
                                        INVOKESPECIAL,
                                        INVOKEINTERFACE -> new MethodInsnNode(INVOKESTATIC, redirect, proxyMethodName(methodInsnNode.name), Type.getMethodDescriptor(
                                        Type.getReturnType(methodInsnNode.desc), Stream.concat(Stream.of(ownerType), Stream.of(Type.getArgumentTypes(methodInsnNode.desc))).toArray(Type[]::new)), false);
                                default              -> throw new UnsupportedOperationException("Unsupported opcode: " + methodInsnNode.getOpcode() + "/" + ASMHelper.OPCODE_LOOKUP.lookupFieldName(methodInsnNode.getOpcode()));
                            });
                        }
                    }
            }
            if (!mapping.isEmpty()) {
                result = true;
                mapping.forEach((source, redirect) -> method.instructions.set(source, redirect));
            }
        }
        return result;
    }
    
    static String lookupRedirectTarget(final ClassNode node, final FieldNode fieldNode) = node.name + "$F$" + ASMHelper.sourceName(fieldNode.desc).replace('.', '_') + "$" + fieldNode.name;
    
    static boolean corresponding(final Field field, final FieldNode fieldNode) = field.getName().equals(fieldNode.name) && Type.getType(field.getType()).equals(Type.getType(fieldNode.desc));
    
    static String lookupRedirectTarget(final ClassNode node, final MethodNode methodNode) = node.name + "$M$" + methodNode.desc.hashCode() + "$" + proxyMethodName(methodNode.name);
    
    static boolean corresponding(final Executable executable, final MethodNode methodNode)
            = executable instanceof Method method ? corresponding(method, methodNode) : executable instanceof Constructor<?> constructor && corresponding(constructor, methodNode);
    
    static boolean corresponding(final Method method, final MethodNode methodNode) = method.getName().equals(methodNode.name) && Type.getMethodDescriptor(method).equals(methodNode.desc);
    
    static boolean corresponding(final Constructor<?> constructor, final MethodNode methodNode) = ASMHelper.isInit(methodNode) && Type.getConstructorDescriptor(constructor).equals(methodNode.desc);
    
}
