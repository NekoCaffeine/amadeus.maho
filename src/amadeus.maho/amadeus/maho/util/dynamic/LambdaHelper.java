package amadeus.maho.util.dynamic;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldNode;

import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.util.annotation.mark.IndirectCaller;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.generator.MethodGenerator;
import amadeus.maho.util.runtime.MethodHandleHelper;
import amadeus.maho.util.runtime.ReflectionHelper;
import amadeus.maho.util.type.InferredGenericType;
import amadeus.maho.util.type.InferredParameterizedType;
import amadeus.maho.util.type.TypeInferer;

import static org.objectweb.asm.Opcodes.ACC_PUBLIC;

public interface LambdaHelper {
    
    static Method lookupFunctionalMethodWithInterface(final Class<?> lambdaType) {
        if (!lambdaType.isInterface())
            throw new IllegalArgumentException(STR."\{lambdaType} is not an interface");
        final Method methods[] = Stream.of(lambdaType.getMethods())
                .filter(ReflectionHelper.anyMatch(ReflectionHelper.ABSTRACT))
                .toArray(Method[]::new);
        if (methods.length != 1)
            throw new IllegalArgumentException(STR."\{lambdaType}.getMethods().length != 1");
        return methods[0];
    }
    
    static boolean isFunctionalInterface(final Class<?> interfaceType) = Stream.of(interfaceType.getMethods())
            .filter(ReflectionHelper.anyMatch(ReflectionHelper.ABSTRACT))
            .filter(ReflectionHelper.noneMatch(ReflectionHelper.BRIDGE))
            .count() == 1;
    
    static Class<?> lookupFunctionalInterface(final Class<?> lambdaType) = Stream.of(lambdaType.getInterfaces())
            .filter(LambdaHelper::isFunctionalInterface)
            .findFirst()
            .orElseThrow(IllegalArgumentException::new);
    
    // missing generics
    static Method lookupFunctionalMethodWithLambda(final Class<?> lambdaType) {
        final Method methods[] = Stream.of(lambdaType.getDeclaredMethods())
                .filter(ReflectionHelper.noneMatch(ReflectionHelper.STATIC))
                .filter(ReflectionHelper.noneMatch(ReflectionHelper.BRIDGE))
                .toArray(Method[]::new);
        if (methods.length != 1)
            throw new IllegalArgumentException(STR."\{lambdaType}.getMethods().length != 1");
        return methods[0];
    }
    
    @SneakyThrows
    static MethodHandle lookupFunctionalMethodHandle(final Object lambda) = MethodHandleHelper.lookup().unreflect(lookupFunctionalMethodWithLambda(lambda.getClass()));
    
    static MethodHandle lookupFunctionalMethodHandleAndBind(final Object lambda) = lookupFunctionalMethodHandle(lambda).bindTo(lambda);

    @SneakyThrows
    static <L> MethodHandle lookupFunctionalMethodHandleWithType(final L lambda) {
        final Method method = lookupFunctionalMethodWithLambda(lambda.getClass());
        final MethodHandle handle = MethodHandleHelper.lookup().unreflect(method);
        final Class<? super L> functionalInterfaceType = (Class<L>) lookupFunctionalInterface(lambda.getClass());
        final Method genericMethod = lookupFunctionalMethodWithInterface(functionalInterfaceType);
        final List<java.lang.reflect.Type> genericTypes = List.of(functionalInterfaceType.getTypeParameters());
        MethodType methodType = handle.type();
        if (TypeInferer.infer(functionalInterfaceType, lambda.getClass()) instanceof InferredParameterizedType parameterizedType) {
            final Class<?> resolveTypes[] = Stream.of(parameterizedType.actualTypeArguments()).map(InferredGenericType::erasedType).toArray(Class[]::new);
            {
                final int returnTypeIndex = genericTypes.indexOf(genericMethod.getGenericReturnType());
                if (returnTypeIndex != -1 && resolveTypes[returnTypeIndex] != Object.class)
                    methodType = methodType.changeReturnType(resolveTypes[returnTypeIndex]);
            }
            final java.lang.reflect.Type parameterTypes[] = genericMethod.getGenericParameterTypes();
            for (int i = 0; i < parameterTypes.length; i++) {
                final int parameterTypeIndex = genericTypes.indexOf(parameterTypes[i]);
                if (parameterTypeIndex != -1 && resolveTypes[parameterTypeIndex] != Object.class)
                    methodType = methodType.changeParameterType(i + 1, resolveTypes[parameterTypeIndex]);
            }
        }
        return handle.type() != methodType ? MethodHandles.explicitCastArguments(handle, methodType) : handle;
    }
    
    static MethodHandle lookupFunctionalMethodHandleWithTypeAndBind(final Object lambda) = lookupFunctionalMethodHandleWithType(lambda).bindTo(lambda);
    
    @SneakyThrows
    @IndirectCaller
    static <T> T lambda(final MethodHandle handle, final Class<T> lambdaType) {
        if (handle.argsCount() == 1) {
            final Object bindArgLast = MethodHandleHelper.atLast(handle);
            if (lambdaType.isInstance(bindArgLast))
                return (T) bindArgLast;
        }
        final Method target = lookupFunctionalMethodWithInterface(lambdaType);
        final MethodType sourceType = handle.type();
        final MethodType methodType = MethodType.methodType(target.getReturnType(), target.getParameterTypes());
        handle.asType(methodType); // check target, make throw WMT
        try {
            return (T) LambdaMetafactory.metafactory(MethodHandleHelper.lookup(), target.getName(), MethodType.methodType(lambdaType), methodType.generic(), handle, methodType).getTarget().invokeExact();
        } catch (final Throwable ignored) { /* not DMH */ return lambda(handle, lambdaType, target, sourceType); }
    }
    
    @IndirectCaller
    static <T> T lambda(final MethodHandle handle, final Class<T> lambdaType, final Method target, final MethodType sourceType) throws Throwable {
        final List<java.lang.reflect.Type> genericTypes = List.of(lambdaType.getTypeParameters());
        final Class<?> resolveTypes[] = new Class<?>[genericTypes.size()];
        Arrays.fill(resolveTypes, Object.class);
        {
            final int returnTypeIndex = genericTypes.indexOf(target.getGenericReturnType());
            if (returnTypeIndex != -1)
                resolveTypes[returnTypeIndex] = sourceType.returnType();
        }
        final java.lang.reflect.Type parameterTypes[] = target.getGenericParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            final int parameterTypeIndex = genericTypes.indexOf(parameterTypes[i]);
            if (parameterTypeIndex != -1)
                resolveTypes[parameterTypeIndex] = sourceType.parameterType(i);
        }
        final DynamicMethod.Lambda<T> lambda = { CallerContext.caller().getClassLoader(), "Lambda", lambdaType, resolveTypes };
        final FieldNode $handle = { ACC_PUBLIC, "$handle", ASMHelper.classDesc(MethodHandle.class), null, null };
        lambda.closure() >> $handle;
        final MethodGenerator generator = lambda.generator();
        generator.loadThis();
        generator.getField(lambda.wrapperType(), $handle.name, Type.getType($handle.desc));
        generator.loadArgs();
        generator.invokeVirtual(Type.getType(MethodHandle.class), new org.objectweb.asm.commons.Method("invoke", Type.getMethodDescriptor(target)));
        generator.returnValue();
        generator.endMethod();
        final T instance = lambda.allocateInstance();
        lambda.setter($handle).invoke(instance, handle);
        return instance;
    }
    
    static boolean isLambdaClass(final Class<?> clazz) = clazz.getCanonicalName() == null && clazz.getName().contains("$Lambda$") && clazz.getName().indexOf('/') != -1;
    
}
