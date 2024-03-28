package amadeus.maho.util.runtime;

import jdk.internal.misc.Unsafe;

import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.util.annotation.mark.HiddenDanger;

public interface UnsafeHelper {
    
    int x86 = 32, x86_64 = x86 << 1, OBJECT_POINTER_SIZE = Unsafe.ARRAY_OBJECT_INDEX_SCALE * Byte.SIZE;
    
    static Unsafe unsafe() = Unsafe.getUnsafe();
    
    static <T extends Throwable, R> R sneakyThrow(final Throwable throwable) throws T {
        throw (T) throwable;
    }
    
    @SneakyThrows
    static <T> T allocateInstance(final Class<?> clazz) = (T) unsafe().allocateInstance(clazz);
    
    @SneakyThrows
    static <T> T allocateInstanceOfType(final Class<T> clazz) = (T) unsafe().allocateInstance(clazz);
    
    static long normalize(final int value) = value >= 0 ? value : ~0L >>> 32 & value;
    
    @HiddenDanger(HiddenDanger.GC)
    static long toAddress(final Object object) {
        final Object[] array = { object };
        return switch (OBJECT_POINTER_SIZE) {
            case x86    -> unsafe().getInt(array, Unsafe.ARRAY_OBJECT_BASE_OFFSET);
            case x86_64 -> unsafe().getLong(array, Unsafe.ARRAY_OBJECT_BASE_OFFSET);
            default     -> throw new UnsupportedOperationException(STR."OBJECT_POINTER_SIZE = \{OBJECT_POINTER_SIZE}");
        };
    }
    
    @HiddenDanger(HiddenDanger.GC)
    static <T> T fromAddress(final long address) {
        final Object[] array = { null };
        switch (OBJECT_POINTER_SIZE) {
            case x86    -> unsafe().putInt(array, Unsafe.ARRAY_OBJECT_BASE_OFFSET, (int) address);
            case x86_64 -> unsafe().putLong(array, Unsafe.ARRAY_OBJECT_BASE_OFFSET, address);
            default     -> throw new UnsupportedOperationException(STR."OBJECT_POINTER_SIZE = \{OBJECT_POINTER_SIZE}");
        }
        return (T) array[0];
    }
    
    static void nop() { }
    
}
