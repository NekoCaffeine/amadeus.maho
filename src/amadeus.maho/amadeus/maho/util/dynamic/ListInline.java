package amadeus.maho.util.dynamic;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.BiPredicate;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.util.bytecode.generator.MethodGenerator;
import amadeus.maho.util.runtime.MethodHandleHelper;
import amadeus.maho.util.runtime.UnsafeHelper;

import static org.objectweb.asm.Opcodes.*;

public interface ListInline {
    
    @SneakyThrows
    static <I, E extends I> I inline(final String debugName = "_list_inline_", final Class<I> itf, final List<E> elements, final BiPredicate<E, Method> predicate = (_, _) -> true) {
        final Wrapper<I> wrapper = { itf.getClassLoader(), itf, debugName };
        final ClassNode node = wrapper.node();
        node.access = ACC_PUBLIC | ACC_FINAL | ACC_SYNTHETIC;
        final int p_index[] = { 0 };
        final IdentityHashMap<E, FieldNode> localElements = { };
        final Type wrapperType = wrapper.wrapperType(), interfaceType = Type.getType(itf);
        final String interfaceDesc = Type.getDescriptor(itf);
        final MethodHandles.Lookup lookup = MethodHandleHelper.lookup();
        wrapper.inheritableUniqueSignatureMethods().forEach(method -> {
            final String name = method.getName();
            final MethodType methodType = MethodType.methodType(method.getReturnType(), method.getParameterTypes());
            final Type returnType = Type.getType(methodType.returnType());
            final org.objectweb.asm.commons.Method asmMethod = org.objectweb.asm.commons.Method.getMethod(method);
            final IdentityHashMap<Class<?>, Boolean> override = { };
            final MethodGenerator generator = wrapper.wrap(method);
            elements.stream()
                    .filter(element -> override.computeIfAbsent(element.getClass(), clazz -> {
                        final Class<?> declaringClass = lookup.revealDirect(lookup.findVirtual(clazz, name, methodType)).getDeclaringClass();
                        return declaringClass != itf && itf.isAssignableFrom(declaringClass);
                    }))
                    .filter(element -> predicate.test(element, method))
                    .forEach(element -> {
                        final FieldNode localField = localElements.computeIfAbsent(element, it -> new FieldNode(ACC_PUBLIC | ACC_STATIC | ACC_FINAL | ACC_SYNTHETIC, "element_%d".formatted(p_index[0]++), interfaceDesc, null, null));
                        generator.getStatic(wrapperType, localField.name, interfaceType);
                        generator.loadArgs();
                        generator.invokeInterface(interfaceType, asmMethod);
                        generator.pop(returnType);
                    });
            generator.pushDefaultLdc(returnType);
            generator.returnValue();
            generator.endMethod();
        });
        elements.stream().map(localElements::get).forEach(node.fields::add);
        final Class<? extends I> wrapperClass = wrapper.defineHiddenWrapperClass();
        localElements.forEach((element, field) -> wrapperClass.setter(field).invoke(element));
        return UnsafeHelper.allocateInstanceOfType(wrapperClass);
    }
    
}
