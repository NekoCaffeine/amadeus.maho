package amadeus.maho.simulation.reflection;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.stream.Stream;

import jdk.internal.reflect.ConstructorAccessor;
import jdk.internal.reflect.FieldAccessor;
import jdk.internal.reflect.MethodAccessor;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import amadeus.maho.core.extension.DynamicLinkingContext;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.simulation.dynamic.DynamicSimulator;
import amadeus.maho.simulation.reflection.accessor.ConstructorAccessorSimulator;
import amadeus.maho.simulation.reflection.accessor.FieldAccessorSimulator;
import amadeus.maho.simulation.reflection.accessor.MethodAccessorSimulator;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.Proxy;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.MethodDescriptor;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.function.FunctionHelper;
import amadeus.maho.util.runtime.DebugHelper;
import amadeus.maho.util.runtime.MethodHandleHelper;
import amadeus.maho.util.runtime.ReflectionHelper;
import amadeus.maho.util.runtime.TypeHelper;

import static org.objectweb.asm.Opcodes.*;

// @Preload(initialized = true)
// @TransformProvider
public interface ReflectionSimulator {
    
    @Getter
    WeakHashMap<Class<?>, List<String>> hiddenMembersMapping = { };
    
    @Getter
    WeakHashMap<Class<?>, List<Member>> injectMembersMapping = { };
    
    static <T extends Member> T[] assumeControl(final T result[], final Class<?> clazz, final Class<T> type, final boolean publicOnly) {
        Stream<T> stream = Stream.of(result);
        final List<String> hiddenMembers = hiddenMembersMapping().get(clazz);
        if (hiddenMembers != null)
            stream = stream.filter(member -> !hiddenMembers.contains(member.getName()));
        final List<? extends Member> injectMembers = injectMembersMapping().get(clazz);
        if (injectMembers != null)
            stream = Stream.concat(stream, injectMembers.stream().cast(type));
        if (publicOnly)
            stream = stream.filter(ReflectionHelper.anyMatch(ReflectionHelper.PUBLIC));
        return stream.toArray(TypeHelper.arrayConstructor(type));
    }
    
    static void clearCache(final Class<?> clazz) {
        hiddenMembersMapping().remove(clazz);
        injectMembersMapping().remove(clazz);
    }
    
    static void addHiddenMember(final Class<?> owner, final String memberName) = hiddenMembersMapping().computeIfAbsent(owner, FunctionHelper.abandon(ArrayList::new)) += memberName;
    
    static void addInjectMember(final Class<?> owner, final Member member) = injectMembersMapping().computeIfAbsent(owner, FunctionHelper.abandon(ArrayList::new)) += member;
    
    @SneakyThrows
    static void addInjectField(final Class<?> owner, final FieldNode fieldNode) {
        final Field inject = newField(owner, fieldNode.name.intern(), ASMHelper.loadType(Type.getType(fieldNode.desc), false, owner.getClassLoader()), fieldNode.access, -1, fieldNode.signature, null);
        final String targetName = DynamicSimulator.redirectTable().get(ASMHelper.className(owner), fieldNode.name);
        final Class<?> targetClass = Class.forName(targetName.replace('/', '.'), true, owner.getClassLoader());
        final boolean isStatic = ReflectionHelper.anyMatch(inject, ReflectionHelper.STATIC);
        final MethodHandles.Lookup lookup = MethodHandleHelper.lookup();
        final FieldAccessorSimulator accessor = {
                owner,
                inject.getType(),
                isStatic,
                isStatic ? lookup.findStaticGetter(targetClass, DynamicSimulator.STATIC_FIELD, inject.getType()) : lookup.findStatic(targetClass, DynamicSimulator.GET_METHOD, MethodType.methodType(inject.getType(), owner)),
                isStatic ? lookup.findStaticSetter(targetClass, DynamicSimulator.STATIC_FIELD, inject.getType()) : lookup.findStatic(targetClass, DynamicSimulator.SET_METHOD, MethodType.methodType(void.class, owner, inject.getType()))
        };
        final Map<Class<? extends Annotation>, Annotation> annotationMap = ASMHelper.findAnnotations(fieldNode.visibleAnnotations, owner.getClassLoader());
        fieldAccessor(inject, accessor);
        overrideFieldAccessor(inject, accessor);
        declaredAnnotations(inject, annotationMap);
        addInjectMember(owner, inject);
    }
    
    @SneakyThrows
    static void addInjectMethod(final Class<?> owner, final MethodNode methodNode) {
        if (methodNode.name.equals(ASMHelper._INIT_)) {
            final Constructor<?> inject = newConstructor(owner,
                    ASMHelper.loadTypes(Stream.of(Type.getArgumentTypes(methodNode.desc)), false, owner.getClassLoader()),
                    ASMHelper.loadTypes(methodNode.exceptions.stream().map(Type::getObjectType), false, owner.getClassLoader()),
                    methodNode.access, -1, methodNode.signature, null, null);
            final String targetName = DynamicSimulator.redirectTable().get(ASMHelper.className(owner), methodNode.name + methodNode.desc);
            final Class<?> targetClass = Class.forName(targetName.replace('/', '.'), true, owner.getClassLoader());
            final ConstructorAccessorSimulator accessor = { MethodHandleHelper.lookup().findStatic(targetClass, DynamicSimulator.CONSTRUCTOR_METHOD, MethodType.methodType(owner, inject.getParameterTypes())) };
            final Map<Class<? extends Annotation>, Annotation> annotationMap = ASMHelper.findAnnotations(methodNode.visibleAnnotations, owner.getClassLoader());
            constructorAccessor(inject, accessor);
            declaredAnnotations(inject, annotationMap);
            addInjectMember(owner, inject);
        } else {
            final Method inject = newMethod(owner, methodNode.name.intern(),
                    ASMHelper.loadTypes(Stream.of(Type.getArgumentTypes(methodNode.desc)), false, owner.getClassLoader()),
                    ASMHelper.loadType(Type.getReturnType(methodNode.desc), false, owner.getClassLoader()),
                    ASMHelper.loadTypes(methodNode.exceptions.stream().map(Type::getObjectType), false, owner.getClassLoader()),
                    methodNode.access, -1, methodNode.signature, null, null, null);
            final String targetName = DynamicSimulator.redirectTable().get(ASMHelper.className(owner),
                    methodNode.name + methodNode.desc);
            final Class<?> targetClass = Class.forName(targetName.replace('/', '.'), true, owner.getClassLoader());
            final boolean isStatic = ReflectionHelper.anyMatch(inject, ReflectionHelper.STATIC);
            final MethodAccessorSimulator accessor = {
                    owner,
                    isStatic,
                    MethodHandleHelper.lookup().findStatic(targetClass, methodNode.name, MethodType.methodType(inject.getReturnType(),
                            isStatic ? inject.getParameterTypes() : Stream.concat(Stream.of(owner), Stream.of(inject.getParameterTypes())).toArray(Class<?>[]::new)))
            };
            final Map<Class<? extends Annotation>, Annotation> annotationMap = ASMHelper.findAnnotations(methodNode.visibleAnnotations, owner.getClassLoader());
            methodAccessor(inject, accessor);
            declaredAnnotations(inject, annotationMap);
            addInjectMember(owner, inject);
        }
    }
    
    @Proxy(GETFIELD)
    static String signature(final Field $this) = DebugHelper.breakpointThenError();
    
    @Proxy(GETFIELD)
    static String signature(final Method $this) = DebugHelper.breakpointThenError();
    
    @Proxy(GETFIELD)
    static String signature(final Constructor<?> $this) = DebugHelper.breakpointThenError();
    
    static Field[] getDeclaredFields(final Class<?> $this) = getDeclaredFields0($this, false);
    
    @Proxy(INVOKEVIRTUAL)
    private static Field[] getDeclaredFields0(final Class<?> $this, final boolean publicOnly) = DebugHelper.breakpointThenError();
    
    static Method[] getDeclaredMethods(final Class<?> $this) = getDeclaredMethods0($this, false);
    
    @Proxy(INVOKEVIRTUAL)
    private static Method[] getDeclaredMethods0(final Class<?> $this, final boolean publicOnly) = DebugHelper.breakpointThenError();
    
    static Constructor<?>[] getDeclaredConstructors(final Class<?> $this) = getDeclaredConstructors0($this, false);
    
    @Proxy(INVOKEVIRTUAL)
    private static Constructor<?>[] getDeclaredConstructors0(final Class<?> $this, final boolean publicOnly) = DebugHelper.breakpointThenError();
    
    @Proxy(NEW)
    static Field newField(final Class<?> declaringClass, final String name, final Class<?> type, final int modifiers, final int slot, final String signature, final byte[] annotations) = DebugHelper.breakpointThenError();
    
    @Proxy(PUTFIELD)
    static void fieldAccessor(final Field $this, final FieldAccessor fieldAccessor) = DebugHelper.breakpointThenError();
    
    @Proxy(PUTFIELD)
    static void overrideFieldAccessor(final Field $this, final FieldAccessor overrideFieldAccessor) = DebugHelper.breakpointThenError();
    
    @Proxy(PUTFIELD)
    static void declaredAnnotations(final Field $this, final Map<Class<? extends Annotation>, Annotation> declaredAnnotations) = DebugHelper.breakpointThenError();
    
    @Proxy(NEW)
    static Method newMethod(final Class<?> declaringClass, final String name, final Class<?> parameterTypes[], final Class<?> returnType,
            final Class<?>[] checkedExceptions, final int modifiers, final int slot, final String signature, final byte annotations[], final byte parameterAnnotations[], final byte annotationDefault[]) = DebugHelper.breakpointThenError();
    
    @Proxy(PUTFIELD)
    static void methodAccessor(final Method $this, final MethodAccessor methodAccessor) = DebugHelper.breakpointThenError();
    
    @Proxy(NEW)
    static <T> Constructor<T> newConstructor(final Class<T> declaringClass, final Class<?> parameterTypes[], final Class<?> checkedExceptions[], final int modifiers, final int slot, final String signature,
            final byte[] annotations, final byte[] parameterAnnotations) = DebugHelper.breakpointThenError();
    
    @Proxy(PUTFIELD)
    static void constructorAccessor(final Constructor<?> $this, final ConstructorAccessor constructorAccessor) = DebugHelper.breakpointThenError();
    
    @Proxy(PUTFIELD)
    static void declaredAnnotations(final Executable $this, final Map<Class<? extends Annotation>, Annotation> declaredAnnotations) = DebugHelper.breakpointThenError();
    
    private static <T extends Member> T[] privateGetDeclaredMember(final T result[], final Class<?> $this, final Class<T> type, final boolean publicOnly)
            = DynamicLinkingContext.shouldAvoidRecursion() ? result : assumeControl(result, $this, type, publicOnly);
    
    @Hook(at = @At(method = @At.MethodInsn(descriptor = @MethodDescriptor(value = Field[].class, parameters = boolean.class))), before = false, capture = true, avoidRecursion = true)
    static Field[] privateGetDeclaredFields(final Field result[], final Class<?> $this, final boolean publicOnly) = privateGetDeclaredMember(result, $this, Field.class, publicOnly);
    
    @Hook(at = @At(method = @At.MethodInsn(descriptor = @MethodDescriptor(value = Method[].class, parameters = boolean.class))), before = false, capture = true, avoidRecursion = true)
    static Method[] privateGetDeclaredMethods(final Method result[], final Class<?> $this, final boolean publicOnly) = privateGetDeclaredMember(result, $this, Method.class, publicOnly);
    
    @Hook(at = @At(method = @At.MethodInsn(descriptor = @MethodDescriptor(value = Constructor[].class, parameters = boolean.class))), before = false, capture = true, avoidRecursion = true)
    static Constructor<?>[] privateGetDeclaredConstructors(final Constructor<?> result[], final Class<?> $this, final boolean publicOnly) = privateGetDeclaredMember(result, $this, Constructor.class, publicOnly);
    
}
