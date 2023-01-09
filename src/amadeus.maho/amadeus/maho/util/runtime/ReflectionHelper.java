package amadeus.maho.util.runtime;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;

public interface ReflectionHelper {
    
    int
            PUBLIC       = 0x00000001,
            PRIVATE      = 0x00000002,
            PROTECTED    = 0x00000004,
            STATIC       = 0x00000008,
            FINAL        = 0x00000010,
            SYNCHRONIZED = 0x00000020,
            VOLATILE     = 0x00000040,
            TRANSIENT    = 0x00000080,
            NATIVE       = 0x00000100,
            INTERFACE    = 0x00000200,
            ABSTRACT     = 0x00000400,
            STRICT       = 0x00000800,
            BRIDGE       = 0x00000040,
            VARARGS      = 0x00000080,
            SYNTHETIC    = 0x00001000,
            ANNOTATION   = 0x00002000,
            ENUM         = 0x00004000,
            MANDATED     = 0x00008000;
    
    static boolean is(final int mod, final int target) = (mod & target) != 0;
    
    static boolean anyMatch(final Member member, final int mask) = (member.getModifiers() & mask) != 0;
    
    static boolean allMatch(final Member member, final int mask) = (member.getModifiers() & mask) == mask;
    
    static boolean noneMatch(final Member member, final int mask) = (member.getModifiers() & mask) == 0;
    
    static boolean anyMatch(final Class<?> clazz, final int mask) = (clazz.getModifiers() & mask) != 0;
    
    static boolean allMatch(final Class<?> clazz, final int mask) = (clazz.getModifiers() & mask) == mask;
    
    static boolean noneMatch(final Class<?> clazz, final int mask) = (clazz.getModifiers() & mask) == 0;
    
    static <T extends Member> Predicate<T> anyMatch(final int mask) = member -> (member.getModifiers() & mask) != 0;
    
    static <T extends Member> Predicate<T> allMatch(final int mask) = member -> (member.getModifiers() & mask) == mask;
    
    static <T extends Member> Predicate<T> noneMatch(final int mask) = member -> (member.getModifiers() & mask) == 0;
    
    static <T extends AccessibleObject> T setAccessible(final T accessible) {
        accessible.setAccessible(true);
        return accessible;
    }
    
    static void initReflectionData(final Class<?> clazz) = clazz.getAnnotations();
    
    @SneakyThrows
    static Field lookupField(final Class<?> owner, final String name) {
        Class<?> clazz = owner;
        while (clazz != null)
            try { return setAccessible(clazz.getDeclaredField(name)); } catch (final NoSuchFieldException e) { clazz = clazz.getSuperclass(); }
        throw new NoSuchFieldException(owner.getName() + "." + name);
    }
    
    @SneakyThrows
    static void set(final Field field, final @Nullable Object instance, final Object val) = field.set(instance, val);
    
    static void set(final Field field, final Object val) = set(field, null, val);
    
    @SneakyThrows
    static <T> @Nullable T get(final Field field, final @Nullable Object instance) = (T) field.get(instance);
    
    static <T> @Nullable T get(final Field field) = get(field, null);
    
    @SneakyThrows
    static Method lookupMethod(final Class<?> owner, final String name, final Class<?>... args) {
        Class<?> clazz = owner;
        while (clazz != null)
            try { return setAccessible(clazz.getDeclaredMethod(name, args)); } catch (final NoSuchMethodException e) { clazz = clazz.getSuperclass(); }
        throw new NoSuchMethodException("%s::%s(%s)".formatted(owner.getName(), name, Stream.of(args).map(String::valueOf).reduce((a, b) -> a + ", " + b).orElse("")));
    }
    
    @SneakyThrows
    static <R> @Nullable R invoke(final Method method, final Object instance, final Object... args) = (R) method.invoke(instance, args);
    
    static List<Method> allMethods(final Class<?> target) {
        @Nullable Class<?> clazz = target;
        final ArrayList<Method> result = { };
        while (clazz != null) {
            result *= List.of(clazz.getDeclaredMethods());
            Stream.of(clazz.getInterfaces())
                    .map(Class::getDeclaredMethods)
                    .map(List::of)
                    .forEach(result::addAll);
            clazz = clazz.getSuperclass();
        }
        return result;
    }
    
    static List<Field> allFields(final Class<?> target) {
        @Nullable Class<?> clazz = target;
        final ArrayList<Field> result = { };
        while (clazz != null) {
            result *= List.of(clazz.getDeclaredFields());
            Stream.of(clazz.getInterfaces())
                    .map(Class::getDeclaredFields)
                    .map(List::of)
                    .forEach(result::addAll);
            clazz = clazz.getSuperclass();
        }
        return result;
    }
    
}
