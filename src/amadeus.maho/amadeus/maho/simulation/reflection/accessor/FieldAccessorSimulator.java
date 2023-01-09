package amadeus.maho.simulation.reflection.accessor;

import java.lang.invoke.MethodHandle;

import jdk.internal.reflect.FieldAccessor;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.runtime.TypeHelper;

@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
public class FieldAccessorSimulator implements FieldAccessor {
    
    Class<?> owner, type;
    boolean isStatic;
    MethodHandle getterHandle, setterHandle;
    
    public MethodHandle bindTo(final MethodHandle handle, final @Nullable Object reference) {
        if (isStatic)
            return handle;
        if (reference == null)
            throw new IllegalArgumentException(new NullPointerException("reference == null"));
        if (!owner.isAssignableFrom(reference.getClass()))
            throw new IllegalArgumentException(new ClassCastException(reference.getClass().getName() + " can't cast to " + owner.getName()));
        return handle.bindTo(reference);
    }
    
    public void checkType(final Class<?> type) {
        if (TypeHelper.isInstance(this.type, type))
            throw new IllegalArgumentException(new ClassCastException(this.type.getName() + " can't cast to " + type.getName()));
    }
    
    @Override
    @SneakyThrows
    public @Nullable Object get(final @Nullable Object reference) throws IllegalArgumentException = bindTo(getterHandle, reference).invoke();
    
    @Override
    @SneakyThrows
    public boolean getBoolean(final @Nullable Object reference) throws IllegalArgumentException {
        checkType(boolean.class);
        return (boolean) bindTo(getterHandle, reference).invoke();
    }
    
    @Override
    @SneakyThrows
    public byte getByte(final @Nullable Object reference) throws IllegalArgumentException {
        checkType(byte.class);
        return (byte) bindTo(getterHandle, reference).invoke();
    }
    
    @Override
    @SneakyThrows
    public char getChar(final @Nullable Object reference) throws IllegalArgumentException {
        checkType(char.class);
        return (char) bindTo(getterHandle, reference).invoke();
    }
    
    @Override
    @SneakyThrows
    public short getShort(final @Nullable Object reference) throws IllegalArgumentException {
        checkType(short.class);
        return (short) bindTo(getterHandle, reference).invoke();
    }
    
    @Override
    @SneakyThrows
    public int getInt(final @Nullable Object reference) throws IllegalArgumentException {
        checkType(int.class);
        return (int) bindTo(getterHandle, reference).invoke();
    }
    
    @Override
    @SneakyThrows
    public long getLong(final @Nullable Object reference) throws IllegalArgumentException {
        checkType(long.class);
        return (long) bindTo(getterHandle, reference).invoke();
    }
    
    @Override
    @SneakyThrows
    public float getFloat(final @Nullable Object reference) throws IllegalArgumentException {
        checkType(float.class);
        return (float) bindTo(getterHandle, reference).invoke();
    }
    
    @Override
    @SneakyThrows
    public double getDouble(final @Nullable Object reference) throws IllegalArgumentException {
        checkType(double.class);
        return (double) bindTo(getterHandle, reference).invoke();
    }
    
    @Override
    @SneakyThrows
    public void set(final @Nullable Object reference, final @Nullable Object value) throws IllegalArgumentException {
        if (type.isPrimitive() && value == null)
            throw new NullPointerException("value == null(target = " + type + ")");
        if (value != null)
            checkType(value.getClass());
        bindTo(setterHandle, reference).invoke(value);
    }
    
    @Override
    @SneakyThrows
    public void setBoolean(final @Nullable Object reference, final boolean value) throws IllegalArgumentException {
        checkType(boolean.class);
        bindTo(setterHandle, reference).invoke(value);
    }
    
    @Override
    @SneakyThrows
    public void setByte(final @Nullable Object reference, final byte value) throws IllegalArgumentException {
        checkType(byte.class);
        bindTo(setterHandle, reference).invoke(value);
    }
    
    @Override
    @SneakyThrows
    public void setChar(final @Nullable Object reference, final char value) throws IllegalArgumentException {
        checkType(char.class);
        bindTo(setterHandle, reference).invoke(value);
    }
    
    @Override
    @SneakyThrows
    public void setShort(final @Nullable Object reference, final short value) throws IllegalArgumentException {
        checkType(short.class);
        bindTo(setterHandle, reference).invoke(value);
    }
    
    @Override
    @SneakyThrows
    public void setInt(final @Nullable Object reference, final int value) throws IllegalArgumentException {
        checkType(int.class);
        bindTo(setterHandle, reference).invoke(value);
    }
    
    @Override
    @SneakyThrows
    public void setLong(final @Nullable Object reference, final long value) throws IllegalArgumentException {
        checkType(long.class);
        bindTo(setterHandle, reference).invoke(value);
    }
    
    @Override
    @SneakyThrows
    public void setFloat(final @Nullable Object reference, final float value) throws IllegalArgumentException {
        checkType(float.class);
        bindTo(setterHandle, reference).invoke(value);
    }
    
    @Override
    @SneakyThrows
    public void setDouble(final @Nullable Object reference, final double value) throws IllegalArgumentException {
        checkType(double.class);
        bindTo(setterHandle, reference).invoke(value);
    }
    
}
