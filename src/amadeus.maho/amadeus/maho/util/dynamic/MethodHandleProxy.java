package amadeus.maho.util.dynamic;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldNode;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.ToString;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.ComputeType;
import amadeus.maho.util.runtime.ArrayHelper;
import amadeus.maho.util.runtime.UnsafeHelper;

import static org.objectweb.asm.Opcodes.ACC_PUBLIC;

@ToString
@AllArgsConstructor(AccessLevel.PROTECTED)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MethodHandleProxy<T> {
    
    @ToString
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class Builder<T> {
        
        T instance;
        
        @SneakyThrows
        public self method(final Method method, final MethodHandle handle) = instance.getClass().getDeclaredField(name(method.getName(), Type.getType(method))).set(instance, handle);
    
        public T build() = instance;
    
    }
    
    public static <T> MethodHandleProxy<T> proxy(final Class<T> target, final boolean insertThis = true, final Predicate<Method> predicate = method -> Modifier.isAbstract(method.getModifiers())) {
        final Wrapper<T> wrapper = { target, "MethodHandleProxy" };
        wrapper.inheritableUniqueSignatureMethods()
                .filter(predicate)
                .map(wrapper::wrap)
                .forEach(generator -> {
                    final FieldNode fieldNode = { ACC_PUBLIC, name(generator.name, Type.getMethodType(generator.desc)), ASMHelper.TYPE_METHOD_HANDLE.getDescriptor(), null, null };
                    wrapper.node().fields += fieldNode;
                    generator.loadThis();
                    generator.getField(wrapper.wrapperType(), fieldNode.name, ASMHelper.TYPE_METHOD_HANDLE);
                    if (insertThis)
                        generator.loadThis();
                    generator.loadArgs();
                    final Type argumentTypes[] = insertThis ? ArrayHelper.insert(generator.argumentTypes, wrapper.superType()) : generator.argumentTypes;
                    generator.invokeVirtual(ASMHelper.TYPE_METHOD_HANDLE, new org.objectweb.asm.commons.Method("invoke", Type.getMethodDescriptor(generator.returnType, argumentTypes)));
                    generator.returnValue();
                    generator.endMethod();
                });
        wrapper.context().markCompute(wrapper.node(), ComputeType.MAX);
        return { wrapper.defineHiddenWrapperClass() };
    }
    
    protected static String name(final String name, final Type methodType) = "$" + name + "_" + Stream.of(methodType.getArgumentTypes())
            .map(Type::getInternalName)
            .map(internalName -> {
                final int index = internalName.lastIndexOf('/');
                return index == -1 ? internalName : internalName.substring(index + 1);
            })
            .collect(Collectors.joining("_")).replace('[', 'A');
    
    Class<? extends T> proxyClass;
    
    public Builder<T> builder() = { UnsafeHelper.allocateInstanceOfType(proxyClass) };
    
}
