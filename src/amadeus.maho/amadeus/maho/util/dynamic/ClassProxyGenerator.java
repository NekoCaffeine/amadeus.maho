package amadeus.maho.util.dynamic;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.runtime.TypeHelper;
import amadeus.maho.util.runtime.UnsafeHelper;

@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ClassProxyGenerator {
    
    String suffix;
    
    @Nullable BiFunction<Class<?>, ClassNode, Stream<FieldNode>> fields;
    
    @Nullable BiFunction<Class<?>, ClassNode, MethodNode> initializer;
    
    @Nullable BiFunction<Class<?>, ClassNode, Stream<MethodNode>> methods;
    
    @Nullable BiConsumer<Class<?>, Wrapper<?>> processor;
    
    @SneakyThrows
    public <T> Supplier<T> apply(final Class<T> target) {
        final Wrapper<?> wrapper = { target, suffix };
        Optional.ofNullable(processor()).ifPresent(it -> it.accept(target, wrapper));
        Optional.ofNullable(fields()).ifPresent(it -> it.apply(target, wrapper.node()).forEach(wrapper.node().fields::add));
        Optional.ofNullable(initializer()).ifPresent(it -> wrapper.node().methods += it.apply(target, wrapper.node()));
        Optional.ofNullable(methods()).ifPresent(it -> it.apply(target, wrapper.node()).forEach(wrapper.node().methods::add));
        final Class<T> proxyClass = (Class<T>) wrapper.defineHiddenWrapperClass();
        return initializer() != null ? TypeHelper.noArgConstructor(proxyClass) : () -> UnsafeHelper.allocateInstanceOfType(proxyClass);
    }
    
}
