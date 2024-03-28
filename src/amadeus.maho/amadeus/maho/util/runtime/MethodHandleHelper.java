package amadeus.maho.util.runtime;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.stream.Stream;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import amadeus.maho.lang.Extension;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.dynamic.CallerContext;

import static org.objectweb.asm.Opcodes.*;

@Extension
@SneakyThrows
public interface MethodHandleHelper {
    
    @Getter
    MethodHandles.Lookup lookup = (MethodHandles.Lookup) MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP").get(null);
    
    static MethodHandles.Lookup lookupIn(final Class<?> context) = lookup().in(context);
    
    static MethodHandles.Lookup lookupInCaller() = lookup().in(CallerContext.caller());
    
    @SneakyThrows
    static MethodHandle lookup(final int opcode, final Class<?> owner, final String name, final Object type) = switch (opcode) {
        case INVOKESTATIC    -> lookup().findStatic(owner, name, (MethodType) type);
        case INVOKEVIRTUAL   -> lookup().findVirtual(owner, name, (MethodType) type);
        case INVOKEINTERFACE -> lookup().findVirtual(owner, name, (MethodType) type);
        case INVOKESPECIAL   -> lookup().findSpecial(owner, name, (MethodType) type, owner);
        case NEW             -> lookup().findConstructor(owner, (MethodType) type);
        case GETSTATIC       -> lookup().findStaticGetter(owner, name, (Class<?>) type);
        case PUTSTATIC       -> lookup().findStaticSetter(owner, name, (Class<?>) type);
        case GETFIELD        -> lookup().findGetter(owner, name, (Class<?>) type);
        case PUTFIELD        -> lookup().findSetter(owner, name, (Class<?>) type);
        default              -> throw new RuntimeException(STR."Unsupported set: \{opcode}/\{ASMHelper.OPCODE_LOOKUP.lookupFieldName(opcode)}");
    };
    
    static int argsCount(final MethodHandle handle) = (int) Stream.of(handle.getClass().getDeclaredFields())
            .filter(ReflectionHelper.noneMatch(ReflectionHelper.STATIC))
            .filter(field -> field.getName().startsWith("arg"))
            .count();
    
    @SneakyThrows
    static <T> @Nullable T at(final MethodHandle handle, final int at) {
        if (at < 0)
            return null;
        try {
            final String atString = String.valueOf(at);
            final Field argN = Stream.of(handle.getClass().getDeclaredFields())
                    .filter(ReflectionHelper.noneMatch(ReflectionHelper.STATIC))
                    .filter(field -> field.getName().startsWith("arg"))
                    .filter(field -> field.getName().endsWith(atString))
                    .findFirst()
                    .orElseThrow(NoSuchFieldException::new);
            return (T) lookup().unreflectGetter(argN).invoke(handle);
        } catch (final ReflectiveOperationException e) { return null; }
    }
    
    static <T> @Nullable T atLast(final MethodHandle handle) {
        final int index = handle.argsCount() - 1;
        return index == -1 ? null : handle.at(index);
    }
    
    @SneakyThrows
    static Object[] args(final MethodHandle handle) = Stream.of(handle.getClass().getDeclaredFields())
            .filter(ReflectionHelper.noneMatch(ReflectionHelper.STATIC))
            .filter(field -> field.getName().startsWith("arg"))
            .map(lookup()::unreflectGetter)
            .map(getter -> getter.invoke(handle))
            .toArray();
    
    static MethodHandle constructor(final Class<?> wrapperClass, final MethodNode methodNode) = constructor(wrapperClass, methodNode.desc);
    
    @SneakyThrows
    static MethodHandle constructor(final Class<?> wrapperClass, final String desc) {
        final MethodType methodType = ASMHelper.loadMethodType(desc, wrapperClass.getClassLoader());
        return lookup().findConstructor(wrapperClass, methodType);
    }
    
    static MethodHandle method(final Class<?> wrapperClass, final MethodNode methodNode) = method(wrapperClass, methodNode.name, methodNode.desc);
    
    @SneakyThrows
    static MethodHandle method(final Class<?> wrapperClass, final String name, final String desc) {
        final MethodType methodType = ASMHelper.loadMethodType(desc, wrapperClass.getClassLoader());
        return lookup().findVirtual(wrapperClass, name, methodType);
    }
    
    static MethodHandle getter(final Class<?> wrapperClass, final FieldNode fieldNode) = getter(wrapperClass, fieldNode.name, fieldNode.desc, ASMHelper.anyMatch(fieldNode.access, ACC_STATIC));
    
    @SneakyThrows
    static MethodHandle getter(final Class<?> wrapperClass, final String name, final String desc, final boolean isStatic) {
        final Class<?> type = ASMHelper.loadType(Type.getType(desc), false, wrapperClass.getClassLoader());
        return isStatic ? lookup().findStaticGetter(wrapperClass, name, type) : lookup().findGetter(wrapperClass, name, type);
    }
    
    static MethodHandle setter(final Class<?> wrapperClass, final FieldNode fieldNode) = setter(wrapperClass, fieldNode.name, fieldNode.desc, ASMHelper.anyMatch(fieldNode.access, ACC_STATIC));
    
    @SneakyThrows
    static MethodHandle setter(final Class<?> wrapperClass, final String name, final String desc, final boolean isStatic) {
        final Class<?> type = ASMHelper.loadType(Type.getType(desc), false, wrapperClass.getClassLoader());
        return isStatic ? lookup().findStaticSetter(wrapperClass, name, type) : lookup().findSetter(wrapperClass, name, type);
    }
    
}
