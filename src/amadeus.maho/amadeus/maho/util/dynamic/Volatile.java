package amadeus.maho.util.dynamic;

import java.lang.reflect.Field;
import java.util.function.Predicate;
import java.util.stream.Stream;

import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.runtime.ReflectionHelper;

import static org.objectweb.asm.Opcodes.ACC_VOLATILE;

public interface Volatile {
    
    static <R, T extends R> Class<? extends R> derived(final Class<T> target) = derived(target, ReflectionHelper.noneMatch(ReflectionHelper.STATIC | ReflectionHelper.FINAL));
    
    static <R, T extends R> Class<? extends R> derived(final Class<T> target, final Predicate<Field> predicate) = derived(target, Stream.of(target.getDeclaredFields()).filter(predicate).toArray(Field[]::new));
    
    static <R, T extends R> Class<? extends R> derived(final Class<T> target, final Field... fields) = Wrapper.ofAnonymousReplace(target, "VolatileMark").let(it -> it.node().fields.stream()
                    .filter(fieldNode -> Stream.of(fields).anyMatch(field -> ASMHelper.corresponding(fieldNode, field)))
                    .forEach(fieldNode -> fieldNode.access |= ACC_VOLATILE))
            .defineHiddenWrapperClass();
    
}
