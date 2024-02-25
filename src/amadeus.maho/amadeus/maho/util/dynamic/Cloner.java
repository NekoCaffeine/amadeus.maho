package amadeus.maho.util.dynamic;

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import jdk.internal.misc.Unsafe;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.FieldNode;

import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.Extension;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.ToString;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.generator.MethodGenerator;
import amadeus.maho.util.misc.Environment;
import amadeus.maho.util.runtime.ReflectionHelper;
import amadeus.maho.util.runtime.TypeHelper;
import amadeus.maho.util.runtime.UnsafeHelper;

import static org.objectweb.asm.Opcodes.*;

@Extension
public interface Cloner {
    
    @ToString
    @EqualsAndHashCode
    record Accessor(Method getter, Method setter) { }
    
    boolean useUnalignedMethod = Environment.local().lookup("amadeus.maho.cloner.unaligned", false);
    
    Map<Class<?>, Accessor> handlers = makeHandlers();
    
    @SneakyThrows
    private static Map<Class<?>, Accessor> makeHandlers() {
        final HashMap<Class<?>, Accessor> map = { };
        TypeHelper.primitiveTypeNames.forEach((clazz, name) -> {
            final boolean unaligned = useUnalignedMethod && TypeHelper.Wrapper.findPrimitiveType(clazz).bitWidth() > 8;
            final String post = unaligned ? "Unaligned" : "";
            map[clazz] = {
                    Method.getMethod(Unsafe.class.getDeclaredMethod(STR."get\{name}\{post}", Object.class, long.class)),
                    Method.getMethod(Unsafe.class.getDeclaredMethod(STR."put\{name}\{post}", Object.class, long.class, clazz))
            };
        });
        map[Object.class] = {
                Method.getMethod(Unsafe.class.getDeclaredMethod("getReference", Object.class, long.class)),
                Method.getMethod(Unsafe.class.getDeclaredMethod("putReference", Object.class, long.class, Object.class))
        };
        return Map.copyOf(map);
    }
    
    ClassLocal<BiConsumer<Object, Object>> duplicatorLocal = { Cloner::generateDuplicator };
    
    @SneakyThrows
    private static BiConsumer<Object, Object> generateDuplicator(final Class<?> clazz) {
        final Unsafe unsafe = UnsafeHelper.unsafe();
        final DynamicMethod.Lambda<BiConsumer<Object, Object>> lambda = { clazz.getClassLoader(), STR."Duplicator$\{clazz.asDebugName()}", BiConsumer.class, Object.class, Object.class };
        final FieldNode $unsafe = { ACC_PRIVATE | ACC_STATIC | ACC_FINAL | ACC_SYNTHETIC, "$unsafe", ASMHelper.TYPE_UNSAFE.getDescriptor(), null, null };
        lambda.addClosure($unsafe);
        final Type owner = lambda.wrapperType();
        final MethodGenerator generator = lambda.generator();
        ReflectionHelper.allFields(clazz).stream()
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .forEach(field -> {
                    final long offset = unsafe.objectFieldOffset(field);
                    final Class<?> fieldType = field.getType(), copyType = fieldType.isPrimitive() ? fieldType : Object.class;
                    final Accessor accessor = handlers[copyType];
                    generator.getStatic(owner, $unsafe);
                    generator.loadArg(1);
                    generator.push(offset);
                    generator.getStatic(owner, $unsafe);
                    generator.loadArg(0);
                    generator.push(offset);
                    generator.invokeUnsafe(accessor.getter());
                    generator.invokeUnsafe(accessor.setter());
                });
        generator.returnValue();
        generator.endMethod();
        final BiConsumer<Object, Object> instance = lambda.allocateInstance();
        lambda.setter($unsafe).invoke(unsafe);
        return instance;
    }
    
    static <T> T copyFields(final Class<T> type, final Object target) {
        if (type == target.getClass())
            return (T) target;
        final T result = UnsafeHelper.allocateInstanceOfType(type);
        duplicatorLocal[type].accept(target, result);
        return result;
    }
    
    static <T> T shallowClone(final T target) {
        final Class<T> type = (Class<T>) target.getClass();
        final T result = UnsafeHelper.allocateInstanceOfType(type);
        duplicatorLocal[type].accept(target, result);
        return result;
    }
    
}
