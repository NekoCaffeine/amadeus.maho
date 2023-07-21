package amadeus.maho.util.dynamic;

import java.lang.reflect.Field;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.annotation.mark.WIP;
import amadeus.maho.util.runtime.UnsafeHelper;

@WIP
public interface FinalFieldHelper {
    
    static void set(final @Nullable Object instance, final Field field, final @Nullable Object value) {
        if (instance == null)
            setStatic(field, value);
        else
            UnsafeHelper.unsafe().putReference(instance, UnsafeHelper.unsafe().objectFieldOffset(field), value);
    }
    
    static void setStatic(final Field field, final @Nullable Object value) {
        UnsafeHelper.unsafe().ensureClassInitialized(field.getDeclaringClass());
        UnsafeHelper.unsafe().putReference(UnsafeHelper.unsafe().staticFieldBase(field), UnsafeHelper.unsafe().staticFieldOffset(field), value);
    }
    
    static <T> T get(final @Nullable Object instance, final Field field) {
        if (instance == null)
            return getStatic(field);
        else
            return (T) UnsafeHelper.unsafe().getReference(instance, UnsafeHelper.unsafe().objectFieldOffset(field));
    }
    
    static <T> T getStatic(final Field field) {
        UnsafeHelper.unsafe().ensureClassInitialized(field.getDeclaringClass());
        return (T) UnsafeHelper.unsafe().getReference(UnsafeHelper.unsafe().staticFieldBase(field), UnsafeHelper.unsafe().staticFieldOffset(field));
    }
    
}
