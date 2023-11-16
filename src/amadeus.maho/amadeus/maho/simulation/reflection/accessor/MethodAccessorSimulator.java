package amadeus.maho.simulation.reflection.accessor;

import java.lang.invoke.MethodHandle;

import jdk.internal.reflect.MethodAccessor;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;

@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
public class MethodAccessorSimulator implements MethodAccessor {
    
    Class<?> owner;
    boolean isStatic;
    MethodHandle handle;
    
    public MethodHandle bindTo(final MethodHandle handle, final @Nullable Object reference) {
        if (isStatic)
            return handle;
        if (reference == null)
            throw new IllegalArgumentException(new NullPointerException("reference == null"));
        if (!owner.isAssignableFrom(reference.getClass()))
            throw new IllegalArgumentException(new ClassCastException(
                    reference.getClass().getName() + " can't cast to " + owner.getName()));
        return handle.bindTo(reference);
    }
    
    @Override
    @SneakyThrows
    public @Nullable Object invoke(final @Nullable Object reference, final Object[] objects, final @Nullable Class<?> caller = null) throws IllegalArgumentException {
        if (reference == null)
            throw new IllegalArgumentException(new NullPointerException("reference == null"));
        return bindTo(handle, reference).invokeWithArguments(objects);
    }
    
}
