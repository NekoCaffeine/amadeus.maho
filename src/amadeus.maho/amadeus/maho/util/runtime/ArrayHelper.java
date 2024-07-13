package amadeus.maho.util.runtime;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.random.RandomGenerator;

import amadeus.maho.lang.Extension;
import amadeus.maho.lang.Include;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.dynamic.ClassLocal;

import static amadeus.maho.util.math.MathHelper.*;

// @Include({ Array.class, Arrays.class })
@Include(Arrays.class)
public interface ArrayHelper {
    
    @Extension
    interface Ext {
        
        static int length(final Object $this[]) = $this.length;
        
        static int length(final char $this[]) = $this.length;
        
        static int length(final int $this[]) = $this.length;
        
        static int length(final double $this[]) = $this.length;
        
        static int length(final float $this[]) = $this.length;
        
        static int length(final long $this[]) = $this.length;
        
        static int length(final short $this[]) = $this.length;
        
        static int length(final byte $this[]) = $this.length;
        
        static int length(final boolean $this[]) = $this.length;
        
    }
    
    static <T> T deepClone(final T array) {
        if (!array.getClass().isArray())
            throw new IllegalArgumentException(toString(array));
        return switch (array) {
            case Object[] objects   -> {
                final Object result[] = objects.clone();
                for (int i = 0; i < result.length; i++) {
                    final Object object = result[i];
                    if (object != null && object.getClass().isArray())
                        result[i] = deepClone(object);
                }
                yield (T) result;
            }
            case byte[] bytes       -> (T) bytes.clone();
            case short[] shorts     -> (T) shorts.clone();
            case int[] ints         -> (T) ints.clone();
            case long[] longs       -> (T) longs.clone();
            case char[] chars       -> (T) chars.clone();
            case float[] floats     -> (T) floats.clone();
            case double[] doubles   -> (T) doubles.clone();
            case boolean[] booleans -> (T) booleans.clone();
            default                 -> throw new AssertionError(toString(array));
        };
    }
    
    static int deepHashCode(final @Nullable Object obj) = switch (obj) {
        case Object[] objects   -> Arrays.deepHashCode(objects);
        case byte[] bytes       -> Arrays.hashCode(bytes);
        case short[] shorts     -> Arrays.hashCode(shorts);
        case int[] ints         -> Arrays.hashCode(ints);
        case long[] longs       -> Arrays.hashCode(longs);
        case char[] chars       -> Arrays.hashCode(chars);
        case float[] floats     -> Arrays.hashCode(floats);
        case double[] doubles   -> Arrays.hashCode(doubles);
        case boolean[] booleans -> Arrays.hashCode(booleans);
        case null               -> 0;
        default                 -> ObjectHelper.hashCode(obj);
    };
    
    static boolean deepEquals(final @Nullable Object a, final @Nullable Object b) {
        if (a == b)
            return true;
        if (a == null || b == null)
            return false;
        if (a.getClass().isArray() && b.getClass().isArray())
            return deepArrayEquals(a, b);
        return a.equals(b);
    }
    
    static boolean deepArrayEquals(final Object a, final Object b) {
        if (a instanceof Object[] && b instanceof Object[])
            return Arrays.deepEquals((Object[]) a, (Object[]) b);
        if (a instanceof byte[] && b instanceof byte[])
            return Arrays.equals((byte[]) a, (byte[]) b);
        if (a instanceof short[] && b instanceof short[])
            return Arrays.equals((short[]) a, (short[]) b);
        if (a instanceof int[] && b instanceof int[])
            return Arrays.equals((int[]) a, (int[]) b);
        if (a instanceof long[] && b instanceof long[])
            return Arrays.equals((long[]) a, (long[]) b);
        if (a instanceof char[] && b instanceof char[])
            return Arrays.equals((char[]) a, (char[]) b);
        if (a instanceof float[] && b instanceof float[])
            return Arrays.equals((float[]) a, (float[]) b);
        if (a instanceof double[] && b instanceof double[])
            return Arrays.equals((double[]) a, (double[]) b);
        if (a instanceof boolean[] && b instanceof boolean[])
            return Arrays.equals((boolean[]) a, (boolean[]) b);
        return ObjectHelper.equals(a, b);
    }
    
    static String toString(final @Nullable Object obj) = switch (obj) {
        case byte[] bytes       -> Arrays.toString(bytes);
        case short[] shorts     -> Arrays.toString(shorts);
        case int[] ints         -> Arrays.toString(ints);
        case long[] longs       -> Arrays.toString(longs);
        case char[] chars       -> Arrays.toString(chars);
        case float[] floats     -> Arrays.toString(floats);
        case double[] doubles   -> Arrays.toString(doubles);
        case boolean[] booleans -> Arrays.toString(booleans);
        case Object[] objects   -> Arrays.deepToString(objects);
        case null               -> "null";
        default                 -> obj.toString();
    };
    
    static void checkArrayLength(final Object array, final int length) {
        if (Array.getLength(array) < length)
            throw new ArrayIndexOutOfBoundsException(length);
    }
    
    Object EMPTY_OBJECT_ARRAY[] = new Object[0];
    
    Class<?> EMPTY_CLASS_ARRAY[] = new Class[0];
    
    String EMPTY_STRING_ARRAY[] = new String[0];
    
    long EMPTY_LONG_ARRAY[] = new long[0];
    
    Long EMPTY_LONG_OBJECT_ARRAY[] = new Long[0];
    
    int EMPTY_INT_ARRAY[] = new int[0];
    
    Integer EMPTY_INTEGER_OBJECT_ARRAY[] = new Integer[0];
    
    short EMPTY_SHORT_ARRAY[] = new short[0];
    
    Short EMPTY_SHORT_OBJECT_ARRAY[] = new Short[0];
    
    byte EMPTY_BYTE_ARRAY[] = new byte[0];
    
    Byte EMPTY_BYTE_OBJECT_ARRAY[] = new Byte[0];
    
    double EMPTY_DOUBLE_ARRAY[] = new double[0];
    
    Double EMPTY_DOUBLE_OBJECT_ARRAY[] = new Double[0];
    
    float EMPTY_FLOAT_ARRAY[] = new float[0];
    
    Float EMPTY_FLOAT_OBJECT_ARRAY[] = new Float[0];
    
    boolean EMPTY_BOOLEAN_ARRAY[] = new boolean[0];
    
    Boolean EMPTY_BOOLEAN_OBJECT_ARRAY[] = new Boolean[0];
    
    char EMPTY_CHAR_ARRAY[] = new char[0];
    
    Character EMPTY_CHARACTER_OBJECT_ARRAY[] = new Character[0];
    
    ClassLocal<Object[]> emptyArrayCache = { type -> (Object[]) Array.newInstance(type, 0) };
    
    static <T> T[] emptyArray(final Class<T> type) = (T[]) emptyArrayCache[type];
    
    int INDEX_NOT_FOUND = -1;
    
    // nullToEmpty
    static <T> T[] nullToEmpty(final @Nullable T array[], final Class<T[]> type) {
        if (type == null)
            throw new IllegalArgumentException("The type must not be null");
        if (array == null)
            return type.cast(Array.newInstance(type.getComponentType(), 0));
        return array;
    }
    
    // Sub arrays
    static @Nullable <T> T[] sub(final @Nullable T array[], int startIndexInclusive, int endIndexExclusive = array.length) {
        if (array == null)
            return null;
        if (startIndexInclusive < 0)
            startIndexInclusive = 0;
        if (endIndexExclusive > array.length)
            endIndexExclusive = array.length;
        final int newSize = endIndexExclusive - startIndexInclusive;
        final Class<?> type = array.getClass().getComponentType();
        if (newSize <= 0)
            return (T[]) Array.newInstance(type, 0);
        final T sub[] = (T[]) Array.newInstance(type, newSize);
        System.arraycopy(array, startIndexInclusive, sub, 0, newSize);
        return sub;
    }
    
    static @Nullable long[] sub(final @Nullable long array[], int startIndexInclusive, int endIndexExclusive = array.length) {
        if (array == null)
            return null;
        if (startIndexInclusive < 0)
            startIndexInclusive = 0;
        if (endIndexExclusive > array.length)
            endIndexExclusive = array.length;
        final int newSize = endIndexExclusive - startIndexInclusive;
        if (newSize <= 0)
            return EMPTY_LONG_ARRAY;
        final long sub[] = new long[newSize];
        System.arraycopy(array, startIndexInclusive, sub, 0, newSize);
        return sub;
    }
    
    static @Nullable int[] sub(final @Nullable int array[], int startIndexInclusive, int endIndexExclusive = array.length) {
        if (array == null)
            return null;
        if (startIndexInclusive < 0)
            startIndexInclusive = 0;
        if (endIndexExclusive > array.length)
            endIndexExclusive = array.length;
        final int newSize = endIndexExclusive - startIndexInclusive;
        if (newSize <= 0)
            return EMPTY_INT_ARRAY;
        final int sub[] = new int[newSize];
        System.arraycopy(array, startIndexInclusive, sub, 0, newSize);
        return sub;
    }
    
    static @Nullable short[] sub(final @Nullable short array[], int startIndexInclusive, int endIndexExclusive = array.length) {
        if (array == null)
            return null;
        if (startIndexInclusive < 0)
            startIndexInclusive = 0;
        if (endIndexExclusive > array.length)
            endIndexExclusive = array.length;
        final int newSize = endIndexExclusive - startIndexInclusive;
        if (newSize <= 0)
            return EMPTY_SHORT_ARRAY;
        final short sub[] = new short[newSize];
        System.arraycopy(array, startIndexInclusive, sub, 0, newSize);
        return sub;
    }
    
    static @Nullable char[] sub(final @Nullable char array[], int startIndexInclusive, int endIndexExclusive = array.length) {
        if (array == null)
            return null;
        if (startIndexInclusive < 0)
            startIndexInclusive = 0;
        if (endIndexExclusive > array.length)
            endIndexExclusive = array.length;
        final int newSize = endIndexExclusive - startIndexInclusive;
        if (newSize <= 0)
            return EMPTY_CHAR_ARRAY;
        final char sub[] = new char[newSize];
        System.arraycopy(array, startIndexInclusive, sub, 0, newSize);
        return sub;
    }
    
    static @Nullable byte[] sub(final @Nullable byte array[], int startIndexInclusive, int endIndexExclusive = array.length) {
        if (array == null)
            return null;
        if (startIndexInclusive < 0)
            startIndexInclusive = 0;
        if (endIndexExclusive > array.length)
            endIndexExclusive = array.length;
        final int newSize = endIndexExclusive - startIndexInclusive;
        if (newSize <= 0)
            return EMPTY_BYTE_ARRAY;
        final byte sub[] = new byte[newSize];
        System.arraycopy(array, startIndexInclusive, sub, 0, newSize);
        return sub;
    }
    
    static @Nullable double[] sub(final @Nullable double array[], int startIndexInclusive, int endIndexExclusive = array.length) {
        if (array == null)
            return null;
        if (startIndexInclusive < 0)
            startIndexInclusive = 0;
        if (endIndexExclusive > array.length)
            endIndexExclusive = array.length;
        final int newSize = endIndexExclusive - startIndexInclusive;
        if (newSize <= 0)
            return EMPTY_DOUBLE_ARRAY;
        final double sub[] = new double[newSize];
        System.arraycopy(array, startIndexInclusive, sub, 0, newSize);
        return sub;
    }
    
    static @Nullable float[] sub(final @Nullable float array[], int startIndexInclusive, int endIndexExclusive = array.length) {
        if (array == null)
            return null;
        if (startIndexInclusive < 0)
            startIndexInclusive = 0;
        if (endIndexExclusive > array.length)
            endIndexExclusive = array.length;
        final int newSize = endIndexExclusive - startIndexInclusive;
        if (newSize <= 0)
            return EMPTY_FLOAT_ARRAY;
        final float sub[] = new float[newSize];
        System.arraycopy(array, startIndexInclusive, sub, 0, newSize);
        return sub;
    }
    
    static @Nullable boolean[] sub(final @Nullable boolean array[], int startIndexInclusive, int endIndexExclusive = array.length) {
        if (array == null)
            return null;
        if (startIndexInclusive < 0)
            startIndexInclusive = 0;
        if (endIndexExclusive > array.length)
            endIndexExclusive = array.length;
        final int newSize = endIndexExclusive - startIndexInclusive;
        if (newSize <= 0)
            return EMPTY_BOOLEAN_ARRAY;
        final boolean sub[] = new boolean[newSize];
        System.arraycopy(array, startIndexInclusive, sub, 0, newSize);
        return sub;
    }
    
    // Is same length
    static boolean isSameLength(final Object array1[], final Object array2[]) = getLength(array1) == getLength(array2);
    
    static boolean isSameLength(final long array1[], final long array2[]) = getLength(array1) == getLength(array2);
    
    static boolean isSameLength(final int array1[], final int array2[]) = getLength(array1) == getLength(array2);
    
    static boolean isSameLength(final short array1[], final short array2[]) = getLength(array1) == getLength(array2);
    
    static boolean isSameLength(final char array1[], final char array2[]) = getLength(array1) == getLength(array2);
    
    static boolean isSameLength(final byte array1[], final byte array2[]) = getLength(array1) == getLength(array2);
    
    static boolean isSameLength(final double array1[], final double array2[]) = getLength(array1) == getLength(array2);
    
    static boolean isSameLength(final float array1[], final float array2[]) = getLength(array1) == getLength(array2);
    
    static boolean isSameLength(final boolean array1[], final boolean array2[]) = getLength(array1) == getLength(array2);
    
    static int getLength(final Object array) = array == null ? 0 : Array.getLength(array);
    
    static boolean isSameType(final Object array1, final Object array2) {
        if (array1 == null || array2 == null)
            throw new IllegalArgumentException("The Array must not be null");
        return array1.getClass().getName().equals(array2.getClass().getName());
    }
    
    // Reverse
    static void reverse(final Object array[]) {
        if (array == null)
            return;
        reverse(array, 0, array.length);
    }
    
    static void reverse(final long array[]) {
        if (array == null)
            return;
        reverse(array, 0, array.length);
    }
    
    static void reverse(final int array[]) {
        if (array == null)
            return;
        reverse(array, 0, array.length);
    }
    
    static void reverse(final short array[]) {
        if (array == null)
            return;
        reverse(array, 0, array.length);
    }
    
    static void reverse(final char array[]) {
        if (array == null)
            return;
        reverse(array, 0, array.length);
    }
    
    static void reverse(final byte array[]) {
        if (array == null)
            return;
        reverse(array, 0, array.length);
    }
    
    static void reverse(final double array[]) {
        if (array == null)
            return;
        reverse(array, 0, array.length);
    }
    
    static void reverse(final float array[]) {
        if (array == null)
            return;
        reverse(array, 0, array.length);
    }
    
    static void reverse(final boolean array[]) {
        if (array == null)
            return;
        reverse(array, 0, array.length);
    }
    
    static void reverse(final boolean array[], final int startIndexInclusive, final int endIndexExclusive) {
        if (array == null)
            return;
        int i = max(startIndexInclusive, 0);
        int j = min(array.length, endIndexExclusive) - 1;
        boolean tmp;
        while (j > i) {
            tmp = array[j];
            array[j] = array[i];
            array[i] = tmp;
            j--;
            i++;
        }
    }
    
    static void reverse(final byte array[], final int startIndexInclusive, final int endIndexExclusive) {
        if (array == null)
            return;
        int i = max(startIndexInclusive, 0);
        int j = min(array.length, endIndexExclusive) - 1;
        byte tmp;
        while (j > i) {
            tmp = array[j];
            array[j] = array[i];
            array[i] = tmp;
            j--;
            i++;
        }
    }
    
    static void reverse(final char array[], final int startIndexInclusive, final int endIndexExclusive) {
        if (array == null)
            return;
        int i = max(startIndexInclusive, 0);
        int j = min(array.length, endIndexExclusive) - 1;
        char tmp;
        while (j > i) {
            tmp = array[j];
            array[j] = array[i];
            array[i] = tmp;
            j--;
            i++;
        }
    }
    
    static void reverse(final double array[], final int startIndexInclusive, final int endIndexExclusive) {
        if (array == null)
            return;
        int i = max(startIndexInclusive, 0);
        int j = min(array.length, endIndexExclusive) - 1;
        double tmp;
        while (j > i) {
            tmp = array[j];
            array[j] = array[i];
            array[i] = tmp;
            j--;
            i++;
        }
    }
    
    static void reverse(final float array[], final int startIndexInclusive, final int endIndexExclusive) {
        if (array == null)
            return;
        int i = max(startIndexInclusive, 0);
        int j = min(array.length, endIndexExclusive) - 1;
        float tmp;
        while (j > i) {
            tmp = array[j];
            array[j] = array[i];
            array[i] = tmp;
            j--;
            i++;
        }
    }
    
    static void reverse(final int array[], final int startIndexInclusive, final int endIndexExclusive) {
        if (array == null)
            return;
        int i = max(startIndexInclusive, 0);
        int j = min(array.length, endIndexExclusive) - 1;
        int tmp;
        while (j > i) {
            tmp = array[j];
            array[j] = array[i];
            array[i] = tmp;
            j--;
            i++;
        }
    }
    
    static void reverse(final long array[], final int startIndexInclusive, final int endIndexExclusive) {
        if (array == null)
            return;
        int i = max(startIndexInclusive, 0);
        int j = min(array.length, endIndexExclusive) - 1;
        long tmp;
        while (j > i) {
            tmp = array[j];
            array[j] = array[i];
            array[i] = tmp;
            j--;
            i++;
        }
    }
    
    static void reverse(final Object array[], final int startIndexInclusive, final int endIndexExclusive) {
        if (array == null)
            return;
        int i = max(startIndexInclusive, 0);
        int j = min(array.length, endIndexExclusive) - 1;
        Object tmp;
        while (j > i) {
            tmp = array[j];
            array[j] = array[i];
            array[i] = tmp;
            j--;
            i++;
        }
    }
    
    static void reverse(final short array[], final int startIndexInclusive, final int endIndexExclusive) {
        if (array == null)
            return;
        int i = max(startIndexInclusive, 0);
        int j = min(array.length, endIndexExclusive) - 1;
        short tmp;
        while (j > i) {
            tmp = array[j];
            array[j] = array[i];
            array[i] = tmp;
            j--;
            i++;
        }
    }
    
    // Swap
    static void swap(final Object array[], final int offset1, final int offset2) {
        if (isEmpty(array))
            return;
        swap(array, offset1, offset2, 1);
    }
    
    static void swap(final long array[], final int offset1, final int offset2) {
        if (isEmpty(array))
            return;
        swap(array, offset1, offset2, 1);
    }
    
    static void swap(final int array[], final int offset1, final int offset2) {
        if (isEmpty(array))
            return;
        swap(array, offset1, offset2, 1);
    }
    
    static void swap(final short array[], final int offset1, final int offset2) {
        if (isEmpty(array))
            return;
        swap(array, offset1, offset2, 1);
    }
    
    static void swap(final char array[], final int offset1, final int offset2) {
        if (isEmpty(array))
            return;
        swap(array, offset1, offset2, 1);
    }
    
    static void swap(final byte array[], final int offset1, final int offset2) {
        if (isEmpty(array))
            return;
        swap(array, offset1, offset2, 1);
    }
    
    static void swap(final double array[], final int offset1, final int offset2) {
        if (isEmpty(array))
            return;
        swap(array, offset1, offset2, 1);
    }
    
    static void swap(final float array[], final int offset1, final int offset2) {
        if (isEmpty(array))
            return;
        swap(array, offset1, offset2, 1);
    }
    
    static void swap(final boolean array[], final int offset1, final int offset2) {
        if (isEmpty(array))
            return;
        swap(array, offset1, offset2, 1);
    }
    
    static void swap(final boolean array[], int offset1, int offset2, int len) {
        if (isEmpty(array) || offset1 >= array.length || offset2 >= array.length)
            return;
        if (offset1 < 0)
            offset1 = 0;
        if (offset2 < 0)
            offset2 = 0;
        len = min(min(len, array.length - offset1), array.length - offset2);
        for (int i = 0; i < len; i++, offset1++, offset2++) {
            final boolean aux = array[offset1];
            array[offset1] = array[offset2];
            array[offset2] = aux;
        }
    }
    
    static void swap(final byte array[], int offset1, int offset2, int len) {
        if (isEmpty(array) || offset1 >= array.length || offset2 >= array.length)
            return;
        if (offset1 < 0)
            offset1 = 0;
        if (offset2 < 0)
            offset2 = 0;
        len = min(min(len, array.length - offset1), array.length - offset2);
        for (int i = 0; i < len; i++, offset1++, offset2++) {
            final byte aux = array[offset1];
            array[offset1] = array[offset2];
            array[offset2] = aux;
        }
    }
    
    static void swap(final char array[], int offset1, int offset2, int len) {
        if (isEmpty(array) || offset1 >= array.length || offset2 >= array.length)
            return;
        if (offset1 < 0)
            offset1 = 0;
        if (offset2 < 0)
            offset2 = 0;
        len = min(min(len, array.length - offset1), array.length - offset2);
        for (int i = 0; i < len; i++, offset1++, offset2++) {
            final char aux = array[offset1];
            array[offset1] = array[offset2];
            array[offset2] = aux;
        }
    }
    
    static void swap(final double array[], int offset1, int offset2, int len) {
        if (isEmpty(array) || offset1 >= array.length || offset2 >= array.length)
            return;
        if (offset1 < 0)
            offset1 = 0;
        if (offset2 < 0)
            offset2 = 0;
        len = min(min(len, array.length - offset1), array.length - offset2);
        for (int i = 0; i < len; i++, offset1++, offset2++) {
            final double aux = array[offset1];
            array[offset1] = array[offset2];
            array[offset2] = aux;
        }
    }
    
    static void swap(final float array[], int offset1, int offset2, int len) {
        if (isEmpty(array) || offset1 >= array.length || offset2 >= array.length)
            return;
        if (offset1 < 0)
            offset1 = 0;
        if (offset2 < 0)
            offset2 = 0;
        len = min(min(len, array.length - offset1), array.length - offset2);
        for (int i = 0; i < len; i++, offset1++, offset2++) {
            final float aux = array[offset1];
            array[offset1] = array[offset2];
            array[offset2] = aux;
        }
    }
    
    static void swap(final int array[], int offset1, int offset2, int len) {
        if (isEmpty(array) || offset1 >= array.length || offset2 >= array.length)
            return;
        if (offset1 < 0)
            offset1 = 0;
        if (offset2 < 0)
            offset2 = 0;
        len = min(min(len, array.length - offset1), array.length - offset2);
        for (int i = 0; i < len; i++, offset1++, offset2++) {
            final int aux = array[offset1];
            array[offset1] = array[offset2];
            array[offset2] = aux;
        }
    }
    
    static void swap(final long array[], int offset1, int offset2, int len) {
        if (isEmpty(array) || offset1 >= array.length || offset2 >= array.length)
            return;
        if (offset1 < 0)
            offset1 = 0;
        if (offset2 < 0)
            offset2 = 0;
        len = min(min(len, array.length - offset1), array.length - offset2);
        for (int i = 0; i < len; i++, offset1++, offset2++) {
            final long aux = array[offset1];
            array[offset1] = array[offset2];
            array[offset2] = aux;
        }
    }
    
    static void swap(final Object array[], int offset1, int offset2, int len) {
        if (isEmpty(array) || offset1 >= array.length || offset2 >= array.length)
            return;
        if (offset1 < 0)
            offset1 = 0;
        if (offset2 < 0)
            offset2 = 0;
        len = min(min(len, array.length - offset1), array.length - offset2);
        for (int i = 0; i < len; i++, offset1++, offset2++) {
            final Object aux = array[offset1];
            array[offset1] = array[offset2];
            array[offset2] = aux;
        }
    }
    
    static void swap(final short array[], int offset1, int offset2, int len) {
        if (isEmpty(array) || offset1 >= array.length || offset2 >= array.length)
            return;
        if (offset1 < 0)
            offset1 = 0;
        if (offset2 < 0)
            offset2 = 0;
        if (offset1 == offset2)
            return;
        len = min(min(len, array.length - offset1), array.length - offset2);
        for (int i = 0; i < len; i++, offset1++, offset2++) {
            final short aux = array[offset1];
            array[offset1] = array[offset2];
            array[offset2] = aux;
        }
    }
    
    // Shift
    static void shift(final Object array[], final int offset) {
        if (array == null)
            return;
        shift(array, 0, array.length, offset);
    }
    
    static void shift(final long array[], final int offset) {
        if (array == null)
            return;
        shift(array, 0, array.length, offset);
    }
    
    static void shift(final int array[], final int offset) {
        if (array == null)
            return;
        shift(array, 0, array.length, offset);
    }
    
    static void shift(final short array[], final int offset) {
        if (array == null)
            return;
        shift(array, 0, array.length, offset);
    }
    
    static void shift(final char array[], final int offset) {
        if (array == null)
            return;
        shift(array, 0, array.length, offset);
    }
    
    static void shift(final byte array[], final int offset) {
        if (array == null)
            return;
        shift(array, 0, array.length, offset);
    }
    
    static void shift(final double array[], final int offset) {
        if (array == null)
            return;
        shift(array, 0, array.length, offset);
    }
    
    static void shift(final float array[], final int offset) {
        if (array == null)
            return;
        shift(array, 0, array.length, offset);
    }
    
    static void shift(final boolean array[], final int offset) {
        if (array == null)
            return;
        shift(array, 0, array.length, offset);
    }
    
    // For algorithm explanations and proof of O(n) time complexity and O(1) space complexity
    // see https://beradrian.wordpress.com/2015/04/07/shift-an-array-in-on-in-place/
    
    static void shift(final boolean array[], int startIndexInclusive, int endIndexExclusive, int offset) {
        if (array == null)
            return;
        if (startIndexInclusive >= array.length - 1 || endIndexExclusive <= 0)
            return;
        if (startIndexInclusive < 0)
            startIndexInclusive = 0;
        if (endIndexExclusive >= array.length)
            endIndexExclusive = array.length;
        int n = endIndexExclusive - startIndexInclusive;
        if (n <= 1)
            return;
        offset %= n;
        if (offset < 0)
            offset += n;
        while (n > 1 && offset > 0) {
            final int n_offset = n - offset;
            if (offset > n_offset) {
                swap(array, startIndexInclusive, startIndexInclusive + n - n_offset, n_offset);
                n = offset;
                offset -= n_offset;
            } else if (offset < n_offset) {
                swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
                startIndexInclusive += offset;
                n = n_offset;
            } else {
                swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
                break;
            }
        }
    }
    
    static void shift(final byte array[], int startIndexInclusive, int endIndexExclusive, int offset) {
        if (array == null)
            return;
        if (startIndexInclusive >= array.length - 1 || endIndexExclusive <= 0)
            return;
        if (startIndexInclusive < 0)
            startIndexInclusive = 0;
        if (endIndexExclusive >= array.length)
            endIndexExclusive = array.length;
        int n = endIndexExclusive - startIndexInclusive;
        if (n <= 1)
            return;
        offset %= n;
        if (offset < 0)
            offset += n;
        while (n > 1 && offset > 0) {
            final int n_offset = n - offset;
            if (offset > n_offset) {
                swap(array, startIndexInclusive, startIndexInclusive + n - n_offset, n_offset);
                n = offset;
                offset -= n_offset;
            } else if (offset < n_offset) {
                swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
                startIndexInclusive += offset;
                n = n_offset;
            } else {
                swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
                break;
            }
        }
    }
    
    static void shift(final char array[], int startIndexInclusive, int endIndexExclusive, int offset) {
        if (array == null)
            return;
        if (startIndexInclusive >= array.length - 1 || endIndexExclusive <= 0)
            return;
        if (startIndexInclusive < 0)
            startIndexInclusive = 0;
        if (endIndexExclusive >= array.length)
            endIndexExclusive = array.length;
        int n = endIndexExclusive - startIndexInclusive;
        if (n <= 1)
            return;
        offset %= n;
        if (offset < 0)
            offset += n;
        while (n > 1 && offset > 0) {
            final int n_offset = n - offset;
            if (offset > n_offset) {
                swap(array, startIndexInclusive, startIndexInclusive + n - n_offset, n_offset);
                n = offset;
                offset -= n_offset;
            } else if (offset < n_offset) {
                swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
                startIndexInclusive += offset;
                n = n_offset;
            } else {
                swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
                break;
            }
        }
    }
    
    static void shift(final double array[], int startIndexInclusive, int endIndexExclusive, int offset) {
        if (array == null)
            return;
        if (startIndexInclusive >= array.length - 1 || endIndexExclusive <= 0)
            return;
        if (startIndexInclusive < 0)
            startIndexInclusive = 0;
        if (endIndexExclusive >= array.length)
            endIndexExclusive = array.length;
        int n = endIndexExclusive - startIndexInclusive;
        if (n <= 1)
            return;
        offset %= n;
        if (offset < 0)
            offset += n;
        while (n > 1 && offset > 0) {
            final int n_offset = n - offset;
            if (offset > n_offset) {
                swap(array, startIndexInclusive, startIndexInclusive + n - n_offset, n_offset);
                n = offset;
                offset -= n_offset;
            } else if (offset < n_offset) {
                swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
                startIndexInclusive += offset;
                n = n_offset;
            } else {
                swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
                break;
            }
        }
    }
    
    static void shift(final float array[], int startIndexInclusive, int endIndexExclusive, int offset) {
        if (array == null)
            return;
        if (startIndexInclusive >= array.length - 1 || endIndexExclusive <= 0)
            return;
        if (startIndexInclusive < 0)
            startIndexInclusive = 0;
        if (endIndexExclusive >= array.length)
            endIndexExclusive = array.length;
        int n = endIndexExclusive - startIndexInclusive;
        if (n <= 1)
            return;
        offset %= n;
        if (offset < 0)
            offset += n;
        while (n > 1 && offset > 0) {
            final int n_offset = n - offset;
            if (offset > n_offset) {
                swap(array, startIndexInclusive, startIndexInclusive + n - n_offset, n_offset);
                n = offset;
                offset -= n_offset;
            } else if (offset < n_offset) {
                swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
                startIndexInclusive += offset;
                n = n_offset;
            } else {
                swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
                break;
            }
        }
    }
    
    static void shift(final int array[], int startIndexInclusive, int endIndexExclusive, int offset) {
        if (array == null)
            return;
        if (startIndexInclusive >= array.length - 1 || endIndexExclusive <= 0)
            return;
        if (startIndexInclusive < 0)
            startIndexInclusive = 0;
        if (endIndexExclusive >= array.length)
            endIndexExclusive = array.length;
        int n = endIndexExclusive - startIndexInclusive;
        if (n <= 1)
            return;
        offset %= n;
        if (offset < 0)
            offset += n;
        while (n > 1 && offset > 0) {
            final int n_offset = n - offset;
            if (offset > n_offset) {
                swap(array, startIndexInclusive, startIndexInclusive + n - n_offset, n_offset);
                n = offset;
                offset -= n_offset;
            } else if (offset < n_offset) {
                swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
                startIndexInclusive += offset;
                n = n_offset;
            } else {
                swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
                break;
            }
        }
    }
    
    static void shift(final long array[], int startIndexInclusive, int endIndexExclusive, int offset) {
        if (array == null)
            return;
        if (startIndexInclusive >= array.length - 1 || endIndexExclusive <= 0)
            return;
        if (startIndexInclusive < 0)
            startIndexInclusive = 0;
        if (endIndexExclusive >= array.length)
            endIndexExclusive = array.length;
        int n = endIndexExclusive - startIndexInclusive;
        if (n <= 1)
            return;
        offset %= n;
        if (offset < 0)
            offset += n;
        while (n > 1 && offset > 0) {
            final int n_offset = n - offset;
            if (offset > n_offset) {
                swap(array, startIndexInclusive, startIndexInclusive + n - n_offset, n_offset);
                n = offset;
                offset -= n_offset;
            } else if (offset < n_offset) {
                swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
                startIndexInclusive += offset;
                n = n_offset;
            } else {
                swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
                break;
            }
        }
    }
    
    static void shift(final Object array[], int startIndexInclusive, int endIndexExclusive, int offset) {
        if (array == null)
            return;
        if (startIndexInclusive >= array.length - 1 || endIndexExclusive <= 0)
            return;
        if (startIndexInclusive < 0)
            startIndexInclusive = 0;
        if (endIndexExclusive >= array.length)
            endIndexExclusive = array.length;
        int n = endIndexExclusive - startIndexInclusive;
        if (n <= 1)
            return;
        offset %= n;
        if (offset < 0)
            offset += n;
        while (n > 1 && offset > 0) {
            final int n_offset = n - offset;
            if (offset > n_offset) {
                swap(array, startIndexInclusive, startIndexInclusive + n - n_offset, n_offset);
                n = offset;
                offset -= n_offset;
            } else if (offset < n_offset) {
                swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
                startIndexInclusive += offset;
                n = n_offset;
            } else {
                swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
                break;
            }
        }
    }
    
    static void shift(final short array[], int startIndexInclusive, int endIndexExclusive, int offset) {
        if (array == null)
            return;
        if (startIndexInclusive >= array.length - 1 || endIndexExclusive <= 0)
            return;
        if (startIndexInclusive < 0)
            startIndexInclusive = 0;
        if (endIndexExclusive >= array.length)
            endIndexExclusive = array.length;
        int n = endIndexExclusive - startIndexInclusive;
        if (n <= 1)
            return;
        offset %= n;
        if (offset < 0)
            offset += n;
        while (n > 1 && offset > 0) {
            final int n_offset = n - offset;
            if (offset > n_offset) {
                swap(array, startIndexInclusive, startIndexInclusive + n - n_offset, n_offset);
                n = offset;
                offset -= n_offset;
            } else if (offset < n_offset) {
                swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
                startIndexInclusive += offset;
                n = n_offset;
            } else {
                swap(array, startIndexInclusive, startIndexInclusive + n_offset, offset);
                break;
            }
        }
    }
    
    // IndexOf search
    static int indexOf(final Object array[], final Object objectToFind, int startIndex = 0) {
        if (array == null)
            return INDEX_NOT_FOUND;
        if (startIndex < 0)
            startIndex = 0;
        if (objectToFind == null) {
            for (int i = startIndex; i < array.length; i++)
                if (array[i] == null)
                    return i;
        } else {
            for (int i = startIndex; i < array.length; i++)
                if (objectToFind.equals(array[i]))
                    return i;
        }
        return INDEX_NOT_FOUND;
    }
    
    static int lastIndexOf(final Object array[], final Object objectToFind, int startIndex = Integer.MAX_VALUE) {
        if (array == null)
            return INDEX_NOT_FOUND;
        if (startIndex < 0)
            return INDEX_NOT_FOUND;
        else if (startIndex >= array.length)
            startIndex = array.length - 1;
        if (objectToFind == null) {
            for (int i = startIndex; i >= 0; i--)
                if (array[i] == null)
                    return i;
        } else if (array.getClass().getComponentType().isInstance(objectToFind)) {
            for (int i = startIndex; i >= 0; i--)
                if (objectToFind.equals(array[i]))
                    return i;
        }
        return INDEX_NOT_FOUND;
    }
    
    static int indexOfRef(final Object array[], final Object objectToFind, int startIndex = 0) {
        if (array == null)
            return INDEX_NOT_FOUND;
        if (startIndex < 0)
            startIndex = 0;
        if (objectToFind == null) {
            for (int i = startIndex; i < array.length; i++)
                if (array[i] == null)
                    return i;
        } else {
            for (int i = startIndex; i < array.length; i++)
                if (objectToFind == array[i])
                    return i;
        }
        return INDEX_NOT_FOUND;
    }
    
    static int lastIndexOfRef(final Object array[], final Object objectToFind, int startIndex = Integer.MAX_VALUE) {
        if (array == null)
            return INDEX_NOT_FOUND;
        if (startIndex < 0)
            return INDEX_NOT_FOUND;
        else if (startIndex >= array.length)
            startIndex = array.length - 1;
        if (objectToFind == null) {
            for (int i = startIndex; i >= 0; i--)
                if (array[i] == null)
                    return i;
        } else if (array.getClass().getComponentType().isInstance(objectToFind)) {
            for (int i = startIndex; i >= 0; i--)
                if (objectToFind == array[i])
                    return i;
        }
        return INDEX_NOT_FOUND;
    }
    
    static boolean contains(final Object array[], final Object objectToFind) = indexOf(array, objectToFind) != INDEX_NOT_FOUND;
    
    static int indexOf(final long array[], final long valueToFind, int startIndex = 0) {
        if (array == null)
            return INDEX_NOT_FOUND;
        if (startIndex < 0)
            startIndex = 0;
        for (int i = startIndex; i < array.length; i++)
            if (valueToFind == array[i])
                return i;
        return INDEX_NOT_FOUND;
    }
    
    static int lastIndexOf(final long array[], final long valueToFind, int startIndex = Integer.MAX_VALUE) {
        if (array == null)
            return INDEX_NOT_FOUND;
        if (startIndex < 0)
            return INDEX_NOT_FOUND;
        else if (startIndex >= array.length)
            startIndex = array.length - 1;
        for (int i = startIndex; i >= 0; i--)
            if (valueToFind == array[i])
                return i;
        return INDEX_NOT_FOUND;
    }
    
    static boolean contains(final long array[], final long valueToFind) = indexOf(array, valueToFind) != INDEX_NOT_FOUND;
    
    static int indexOf(final int array[], final int valueToFind, int startIndex = 0) {
        if (array == null)
            return INDEX_NOT_FOUND;
        if (startIndex < 0)
            startIndex = 0;
        for (int i = startIndex; i < array.length; i++)
            if (valueToFind == array[i])
                return i;
        return INDEX_NOT_FOUND;
    }
    
    static int lastIndexOf(final int array[], final int valueToFind, int startIndex = Integer.MAX_VALUE) {
        if (array == null)
            return INDEX_NOT_FOUND;
        if (startIndex < 0)
            return INDEX_NOT_FOUND;
        else if (startIndex >= array.length)
            startIndex = array.length - 1;
        for (int i = startIndex; i >= 0; i--)
            if (valueToFind == array[i])
                return i;
        return INDEX_NOT_FOUND;
    }
    
    static boolean contains(final int array[], final int valueToFind) = indexOf(array, valueToFind) != INDEX_NOT_FOUND;
    
    static int indexOf(final short array[], final short valueToFind, int startIndex = 0) {
        if (array == null)
            return INDEX_NOT_FOUND;
        if (startIndex < 0)
            startIndex = 0;
        for (int i = startIndex; i < array.length; i++)
            if (valueToFind == array[i])
                return i;
        return INDEX_NOT_FOUND;
    }
    
    static int lastIndexOf(final short array[], final short valueToFind, int startIndex = Integer.MAX_VALUE) {
        if (array == null)
            return INDEX_NOT_FOUND;
        if (startIndex < 0)
            return INDEX_NOT_FOUND;
        else if (startIndex >= array.length)
            startIndex = array.length - 1;
        for (int i = startIndex; i >= 0; i--)
            if (valueToFind == array[i])
                return i;
        return INDEX_NOT_FOUND;
    }
    
    static boolean contains(final short array[], final short valueToFind) = indexOf(array, valueToFind) != INDEX_NOT_FOUND;
    
    static int indexOf(final char array[], final char valueToFind, int startIndex = 0) {
        if (array == null)
            return INDEX_NOT_FOUND;
        if (startIndex < 0)
            startIndex = 0;
        for (int i = startIndex; i < array.length; i++)
            if (valueToFind == array[i])
                return i;
        return INDEX_NOT_FOUND;
    }
    
    static int lastIndexOf(final char array[], final char valueToFind, int startIndex = Integer.MAX_VALUE) {
        if (array == null)
            return INDEX_NOT_FOUND;
        if (startIndex < 0)
            return INDEX_NOT_FOUND;
        else if (startIndex >= array.length)
            startIndex = array.length - 1;
        for (int i = startIndex; i >= 0; i--)
            if (valueToFind == array[i])
                return i;
        return INDEX_NOT_FOUND;
    }
    
    static boolean contains(final char array[], final char valueToFind) = indexOf(array, valueToFind) != INDEX_NOT_FOUND;
    
    static int indexOf(final byte array[], final byte valueToFind, int startIndex = 0) {
        if (array == null)
            return INDEX_NOT_FOUND;
        if (startIndex < 0)
            startIndex = 0;
        for (int i = startIndex; i < array.length; i++)
            if (valueToFind == array[i])
                return i;
        return INDEX_NOT_FOUND;
    }
    
    static int lastIndexOf(final byte array[], final byte valueToFind, int startIndex = Integer.MAX_VALUE) {
        if (array == null)
            return INDEX_NOT_FOUND;
        if (startIndex < 0)
            return INDEX_NOT_FOUND;
        else if (startIndex >= array.length)
            startIndex = array.length - 1;
        for (int i = startIndex; i >= 0; i--)
            if (valueToFind == array[i])
                return i;
        return INDEX_NOT_FOUND;
    }
    
    static boolean contains(final byte array[], final byte valueToFind) = indexOf(array, valueToFind) != INDEX_NOT_FOUND;
    
    static int indexOf(final double array[], final double valueToFind, int startIndex = 0) {
        if (isEmpty(array))
            return INDEX_NOT_FOUND;
        if (startIndex < 0)
            startIndex = 0;
        for (int i = startIndex; i < array.length; i++)
            if (valueToFind == array[i])
                return i;
        return INDEX_NOT_FOUND;
    }
    
    static int indexOf(final double array[], final double valueToFind, int startIndex = 0, final double tolerance) {
        if (isEmpty(array))
            return INDEX_NOT_FOUND;
        if (startIndex < 0)
            startIndex = 0;
        final double min = valueToFind - tolerance;
        final double max = valueToFind + tolerance;
        for (int i = startIndex; i < array.length; i++)
            if (array[i] >= min && array[i] <= max)
                return i;
        return INDEX_NOT_FOUND;
    }
    
    static int lastIndexOf(final double array[], final double valueToFind, int startIndex = Integer.MAX_VALUE) {
        if (isEmpty(array))
            return INDEX_NOT_FOUND;
        if (startIndex < 0)
            return INDEX_NOT_FOUND;
        else if (startIndex >= array.length)
            startIndex = array.length - 1;
        for (int i = startIndex; i >= 0; i--)
            if (valueToFind == array[i])
                return i;
        return INDEX_NOT_FOUND;
    }
    
    static int lastIndexOf(final double array[], final double valueToFind, int startIndex = Integer.MAX_VALUE, final double tolerance) {
        if (isEmpty(array))
            return INDEX_NOT_FOUND;
        if (startIndex < 0)
            return INDEX_NOT_FOUND;
        else if (startIndex >= array.length)
            startIndex = array.length - 1;
        final double min = valueToFind - tolerance;
        final double max = valueToFind + tolerance;
        for (int i = startIndex; i >= 0; i--)
            if (array[i] >= min && array[i] <= max)
                return i;
        return INDEX_NOT_FOUND;
    }
    
    static boolean contains(final double array[], final double valueToFind) = indexOf(array, valueToFind) != INDEX_NOT_FOUND;
    
    static boolean contains(final double array[], final double valueToFind, final double tolerance) = indexOf(array, valueToFind, tolerance) != INDEX_NOT_FOUND;
    
    static int indexOf(final float array[], final float valueToFind, int startIndex = 0) {
        if (isEmpty(array))
            return INDEX_NOT_FOUND;
        if (startIndex < 0)
            startIndex = 0;
        for (int i = startIndex; i < array.length; i++)
            if (valueToFind == array[i])
                return i;
        return INDEX_NOT_FOUND;
    }
    
    static int lastIndexOf(final float array[], final float valueToFind, int startIndex = Integer.MAX_VALUE) {
        if (isEmpty(array))
            return INDEX_NOT_FOUND;
        if (startIndex < 0)
            return INDEX_NOT_FOUND;
        else if (startIndex >= array.length)
            startIndex = array.length - 1;
        for (int i = startIndex; i >= 0; i--)
            if (valueToFind == array[i])
                return i;
        return INDEX_NOT_FOUND;
    }
    
    static boolean contains(final float array[], final float valueToFind) = indexOf(array, valueToFind) != INDEX_NOT_FOUND;
    
    static int indexOf(final boolean array[], final boolean valueToFind, int startIndex = 0) {
        if (isEmpty(array))
            return INDEX_NOT_FOUND;
        if (startIndex < 0)
            startIndex = 0;
        for (int i = startIndex; i < array.length; i++)
            if (valueToFind == array[i])
                return i;
        return INDEX_NOT_FOUND;
    }
    
    static int lastIndexOf(final boolean array[], final boolean valueToFind, int startIndex = Integer.MAX_VALUE) {
        if (isEmpty(array))
            return INDEX_NOT_FOUND;
        if (startIndex < 0)
            return INDEX_NOT_FOUND;
        else if (startIndex >= array.length)
            startIndex = array.length - 1;
        for (int i = startIndex; i >= 0; i--)
            if (valueToFind == array[i])
                return i;
        return INDEX_NOT_FOUND;
    }
    
    static boolean contains(final boolean array[], final boolean valueToFind) = indexOf(array, valueToFind) != INDEX_NOT_FOUND;
    
    // startsWith
    static boolean startsWith(final boolean array1[], final boolean array2[], final int prefixLength = array2.length) {
        if (array1 == null || array2 == null || prefixLength < 0)
            return false;
        if (array1.length < prefixLength || array2.length < prefixLength)
            return false;
        for (int i = 0; i < prefixLength; i++)
            if (array1[i] != array2[i])
                return false;
        return true;
    }
    
    static boolean startsWith(final byte array1[], final byte array2[], final int prefixLength = array2.length) {
        if (array1 == null || array2 == null || prefixLength < 0)
            return false;
        if (array1.length < prefixLength || array2.length < prefixLength)
            return false;
        for (int i = 0; i < prefixLength; i++)
            if (array1[i] != array2[i])
                return false;
        return true;
    }
    
    static boolean startsWith(final char array1[], final char array2[], final int prefixLength = array2.length) {
        if (array1 == null || array2 == null || prefixLength < 0)
            return false;
        if (array1.length < prefixLength || array2.length < prefixLength)
            return false;
        for (int i = 0; i < prefixLength; i++)
            if (array1[i] != array2[i])
                return false;
        return true;
    }
    
    static boolean startsWith(final double array1[], final double array2[], final int prefixLength = array2.length) {
        if (array1 == null || array2 == null || prefixLength < 0)
            return false;
        if (array1.length < prefixLength || array2.length < prefixLength)
            return false;
        for (int i = 0; i < prefixLength; i++)
            if (array1[i] != array2[i])
                return false;
        return true;
    }
    
    static boolean startsWith(final float array1[], final float array2[], final int prefixLength = array2.length) {
        if (array1 == null || array2 == null || prefixLength < 0)
            return false;
        if (array1.length < prefixLength || array2.length < prefixLength)
            return false;
        for (int i = 0; i < prefixLength; i++)
            if (array1[i] != array2[i])
                return false;
        return true;
    }
    
    static boolean startsWith(final int array1[], final int array2[], final int prefixLength = array2.length) {
        if (array1 == null || array2 == null || prefixLength < 0)
            return false;
        if (array1.length < prefixLength || array2.length < prefixLength)
            return false;
        for (int i = 0; i < prefixLength; i++)
            if (array1[i] != array2[i])
                return false;
        return true;
    }
    
    static boolean startsWith(final long array1[], final long array2[], final int prefixLength = array2.length) {
        if (array1 == null || array2 == null || prefixLength < 0)
            return false;
        if (array1.length < prefixLength || array2.length < prefixLength)
            return false;
        for (int i = 0; i < prefixLength; i++)
            if (array1[i] != array2[i])
                return false;
        return true;
    }
    
    static boolean startsWith(final short array1[], final short array2[], final int prefixLength = array2.length) {
        if (array1 == null || array2 == null || prefixLength < 0)
            return false;
        if (array1.length < prefixLength || array2.length < prefixLength)
            return false;
        for (int i = 0; i < prefixLength; i++)
            if (array1[i] != array2[i])
                return false;
        return true;
    }
    
    static <T> boolean startsWith(final T array1[], final T array2[], final int prefixLength = array2.length) {
        if (array1 == null || array2 == null || prefixLength < 0)
            return false;
        if (array1.length < prefixLength || array2.length < prefixLength)
            return false;
        for (int i = 0; i < prefixLength; i++)
            if (!ObjectHelper.equals(array1[i], array2[i]))
                return false;
        return true;
    }
    
    // endsWith
    static boolean endsWith(final boolean array1[], final boolean array2[], final int suffixLength = array2.length) {
        if (array1 == null || array2 == null || suffixLength < 0)
            return false;
        if (array1.length < suffixLength || array2.length < suffixLength)
            return false;
        final int len1 = array1.length, len2 = array2.length;
        for (int i = 0; i < suffixLength; i++)
            if (array1[len1 - i - 1] != array2[len2 - i - 1])
                return false;
        return true;
    }
    
    static boolean endsWith(final byte array1[], final byte array2[], final int suffixLength = array2.length) {
        if (array1 == null || array2 == null || suffixLength < 0)
            return false;
        if (array1.length < suffixLength || array2.length < suffixLength)
            return false;
        final int len1 = array1.length, len2 = array2.length;
        for (int i = 0; i < suffixLength; i++)
            if (array1[len1 - i - 1] != array2[len2 - i - 1])
                return false;
        return true;
    }
    
    static boolean endsWith(final char array1[], final char array2[], final int suffixLength = array2.length) {
        if (array1 == null || array2 == null || suffixLength < 0)
            return false;
        if (array1.length < suffixLength || array2.length < suffixLength)
            return false;
        final int len1 = array1.length, len2 = array2.length;
        for (int i = 0; i < suffixLength; i++)
            if (array1[len1 - i - 1] != array2[len2 - i - 1])
                return false;
        return true;
    }
    
    static boolean endsWith(final double array1[], final double array2[], final int suffixLength = array2.length) {
        if (array1 == null || array2 == null || suffixLength < 0)
            return false;
        if (array1.length < suffixLength || array2.length < suffixLength)
            return false;
        final int len1 = array1.length, len2 = array2.length;
        for (int i = 0; i < suffixLength; i++)
            if (array1[len1 - i - 1] != array2[len2 - i - 1])
                return false;
        return true;
    }
    
    static boolean endsWith(final float array1[], final float array2[], final int suffixLength = array2.length) {
        if (array1 == null || array2 == null || suffixLength < 0)
            return false;
        if (array1.length < suffixLength || array2.length < suffixLength)
            return false;
        final int len1 = array1.length, len2 = array2.length;
        for (int i = 0; i < suffixLength; i++)
            if (array1[len1 - i - 1] != array2[len2 - i - 1])
                return false;
        return true;
    }
    
    static boolean endsWith(final int array1[], final int array2[], final int suffixLength = array2.length) {
        if (array1 == null || array2 == null || suffixLength < 0)
            return false;
        if (array1.length < suffixLength || array2.length < suffixLength)
            return false;
        final int len1 = array1.length, len2 = array2.length;
        for (int i = 0; i < suffixLength; i++)
            if (array1[len1 - i - 1] != array2[len2 - i - 1])
                return false;
        return true;
    }
    
    static boolean endsWith(final long array1[], final long array2[], final int suffixLength = array2.length) {
        if (array1 == null || array2 == null || suffixLength < 0)
            return false;
        if (array1.length < suffixLength || array2.length < suffixLength)
            return false;
        final int len1 = array1.length, len2 = array2.length;
        for (int i = 0; i < suffixLength; i++)
            if (array1[len1 - i - 1] != array2[len2 - i - 1])
                return false;
        return true;
    }
    
    static boolean endsWith(final short array1[], final short array2[], final int suffixLength = array2.length) {
        if (array1 == null || array2 == null || suffixLength < 0)
            return false;
        if (array1.length < suffixLength || array2.length < suffixLength)
            return false;
        final int len1 = array1.length, len2 = array2.length;
        for (int i = 0; i < suffixLength; i++)
            if (array1[len1 - i - 1] != array2[len2 - i - 1])
                return false;
        return true;
    }
    
    static <T> boolean endsWith(final T array1[], final T array2[], final int suffixLength = array2.length) {
        if (array1 == null || array2 == null || suffixLength < 0)
            return false;
        if (array1.length < suffixLength || array2.length < suffixLength)
            return false;
        final int len1 = array1.length, len2 = array2.length;
        for (int i = 0; i < suffixLength; i++)
            if (!ObjectHelper.equals(array1[len1 - i - 1], array2[len2 - i - 1]))
                return false;
        return true;
    }
    
    // compareTo
    static int compareTo(final boolean array1[], final boolean array2[]) {
        if (array1 == null)
            return array2 == null ? 0 : -1;
        if (array2 == null)
            return 1;
        final int minLength = Math.min(array1.length, array2.length);
        for (int i = 0; i < minLength; i++) {
            final int cmp = Boolean.compare(array1[i], array2[i]);
            if (cmp != 0)
                return cmp;
        }
        return Integer.compare(array1.length, array2.length);
    }
    
    static int compareTo(final byte array1[], final byte array2[]) {
        if (array1 == null)
            return array2 == null ? 0 : -1;
        if (array2 == null)
            return 1;
        final int minLength = Math.min(array1.length, array2.length);
        for (int i = 0; i < minLength; i++) {
            final int cmp = Byte.compare(array1[i], array2[i]);
            if (cmp != 0)
                return cmp;
        }
        return Integer.compare(array1.length, array2.length);
    }
    
    static int compareTo(final char array1[], final char array2[]) {
        if (array1 == null)
            return array2 == null ? 0 : -1;
        if (array2 == null)
            return 1;
        final int minLength = Math.min(array1.length, array2.length);
        for (int i = 0; i < minLength; i++) {
            final int cmp = Character.compare(array1[i], array2[i]);
            if (cmp != 0)
                return cmp;
        }
        return Integer.compare(array1.length, array2.length);
    }
    
    static int compareTo(final double array1[], final double array2[]) {
        if (array1 == null)
            return array2 == null ? 0 : -1;
        if (array2 == null)
            return 1;
        final int minLength = Math.min(array1.length, array2.length);
        for (int i = 0; i < minLength; i++) {
            final int cmp = Double.compare(array1[i], array2[i]);
            if (cmp != 0)
                return cmp;
        }
        return Integer.compare(array1.length, array2.length);
    }
    
    static int compareTo(final float array1[], final float array2[]) {
        if (array1 == null)
            return array2 == null ? 0 : -1;
        if (array2 == null)
            return 1;
        final int minLength = Math.min(array1.length, array2.length);
        for (int i = 0; i < minLength; i++) {
            final int cmp = Float.compare(array1[i], array2[i]);
            if (cmp != 0)
                return cmp;
        }
        return Integer.compare(array1.length, array2.length);
    }
    
    static int compareTo(final int array1[], final int array2[]) {
        if (array1 == null)
            return array2 == null ? 0 : -1;
        if (array2 == null)
            return 1;
        final int minLength = Math.min(array1.length, array2.length);
        for (int i = 0; i < minLength; i++) {
            final int cmp = Integer.compare(array1[i], array2[i]);
            if (cmp != 0)
                return cmp;
        }
        return Integer.compare(array1.length, array2.length);
    }
    
    static int compareTo(final long array1[], final long array2[]) {
        if (array1 == null)
            return array2 == null ? 0 : -1;
        if (array2 == null)
            return 1;
        final int minLength = Math.min(array1.length, array2.length);
        for (int i = 0; i < minLength; i++) {
            final int cmp = Long.compare(array1[i], array2[i]);
            if (cmp != 0)
                return cmp;
        }
        return Integer.compare(array1.length, array2.length);
    }
    
    static int compareTo(final short array1[], final short array2[]) {
        if (array1 == null)
            return array2 == null ? 0 : -1;
        if (array2 == null)
            return 1;
        final int minLength = Math.min(array1.length, array2.length);
        for (int i = 0; i < minLength; i++) {
            final int cmp = Short.compare(array1[i], array2[i]);
            if (cmp != 0)
                return cmp;
        }
        return Integer.compare(array1.length, array2.length);
    }
    
    static <T extends Comparable<T>> int compareTo(final @Nullable T array1[], final @Nullable T array2[]) {
        if (array1 == null)
            return array2 == null ? 0 : -1;
        if (array2 == null)
            return 1;
        final int minLength = Math.min(array1.length, array2.length);
        for (int i = 0; i < minLength; i++) {
            final int cmp = array1[i].compareTo(array2[i]);
            if (cmp != 0)
                return cmp;
        }
        return Integer.compare(array1.length, array2.length);
    }
    
    // Primitive/Object array converters
    static @Nullable char[] toPrimitive(final @Nullable Character array[]) {
        if (array == null)
            return null;
        else if (array.length == 0)
            return EMPTY_CHAR_ARRAY;
        final char result[] = new char[array.length];
        for (int i = 0; i < array.length; i++)
            result[i] = array[i];
        return result;
    }
    
    static @Nullable char[] toPrimitive(final @Nullable Character array[], final char valueForNull) {
        if (array == null)
            return null;
        else if (array.length == 0)
            return EMPTY_CHAR_ARRAY;
        final char result[] = new char[array.length];
        for (int i = 0; i < array.length; i++) {
            final Character b = array[i];
            result[i] = b == null ? valueForNull : b;
        }
        return result;
    }
    
    static @Nullable Character[] toObject(final @Nullable char array[]) {
        if (array == null)
            return null;
        else if (array.length == 0)
            return EMPTY_CHARACTER_OBJECT_ARRAY;
        final Character result[] = new Character[array.length];
        for (int i = 0; i < array.length; i++)
            result[i] = array[i];
        return result;
    }
    
    static @Nullable long[] toPrimitive(final @Nullable Long array[]) {
        if (array == null)
            return null;
        else if (array.length == 0)
            return EMPTY_LONG_ARRAY;
        final long result[] = new long[array.length];
        for (int i = 0; i < array.length; i++)
            result[i] = array[i];
        return result;
    }
    
    static @Nullable long[] toPrimitive(final @Nullable Long array[], final long valueForNull) {
        if (array == null)
            return null;
        else if (array.length == 0)
            return EMPTY_LONG_ARRAY;
        final long result[] = new long[array.length];
        for (int i = 0; i < array.length; i++) {
            final Long b = array[i];
            result[i] = b == null ? valueForNull : b;
        }
        return result;
    }
    
    static @Nullable Long[] toObject(final @Nullable long array[]) {
        if (array == null)
            return null;
        else if (array.length == 0)
            return EMPTY_LONG_OBJECT_ARRAY;
        final Long result[] = new Long[array.length];
        for (int i = 0; i < array.length; i++)
            result[i] = array[i];
        return result;
    }
    
    static @Nullable int[] toPrimitive(final @Nullable Integer array[]) {
        if (array == null)
            return null;
        else if (array.length == 0)
            return EMPTY_INT_ARRAY;
        final int result[] = new int[array.length];
        for (int i = 0; i < array.length; i++)
            result[i] = array[i];
        return result;
    }
    
    static @Nullable int[] toPrimitive(final @Nullable Integer array[], final int valueForNull) {
        if (array == null)
            return null;
        else if (array.length == 0)
            return EMPTY_INT_ARRAY;
        final int result[] = new int[array.length];
        for (int i = 0; i < array.length; i++) {
            final Integer b = array[i];
            result[i] = b == null ? valueForNull : b;
        }
        return result;
    }
    
    static @Nullable Integer[] toObject(final @Nullable int array[]) {
        if (array == null)
            return null;
        else if (array.length == 0)
            return EMPTY_INTEGER_OBJECT_ARRAY;
        final Integer result[] = new Integer[array.length];
        for (int i = 0; i < array.length; i++)
            result[i] = array[i];
        return result;
    }
    
    static @Nullable short[] toPrimitive(final @Nullable Short array[]) {
        if (array == null)
            return null;
        else if (array.length == 0)
            return EMPTY_SHORT_ARRAY;
        final short result[] = new short[array.length];
        for (int i = 0; i < array.length; i++)
            result[i] = array[i];
        return result;
    }
    
    static @Nullable short[] toPrimitive(final @Nullable Short array[], final short valueForNull) {
        if (array == null)
            return null;
        else if (array.length == 0)
            return EMPTY_SHORT_ARRAY;
        final short result[] = new short[array.length];
        for (int i = 0; i < array.length; i++) {
            final Short b = array[i];
            result[i] = b == null ? valueForNull : b;
        }
        return result;
    }
    
    static @Nullable Short[] toObject(final @Nullable short array[]) {
        if (array == null)
            return null;
        else if (array.length == 0)
            return EMPTY_SHORT_OBJECT_ARRAY;
        final Short result[] = new Short[array.length];
        for (int i = 0; i < array.length; i++)
            result[i] = array[i];
        return result;
    }
    
    static @Nullable byte[] toPrimitive(final @Nullable Byte array[]) {
        if (array == null)
            return null;
        else if (array.length == 0)
            return EMPTY_BYTE_ARRAY;
        final byte result[] = new byte[array.length];
        for (int i = 0; i < array.length; i++)
            result[i] = array[i];
        return result;
    }
    
    static @Nullable byte[] toPrimitive(final @Nullable Byte array[], final byte valueForNull) {
        if (array == null)
            return null;
        else if (array.length == 0)
            return EMPTY_BYTE_ARRAY;
        final byte result[] = new byte[array.length];
        for (int i = 0; i < array.length; i++) {
            final Byte b = array[i];
            result[i] = b == null ? valueForNull : b;
        }
        return result;
    }
    
    static @Nullable Byte[] toObject(final @Nullable byte array[]) {
        if (array == null)
            return null;
        else if (array.length == 0)
            return EMPTY_BYTE_OBJECT_ARRAY;
        final Byte result[] = new Byte[array.length];
        for (int i = 0; i < array.length; i++)
            result[i] = array[i];
        return result;
    }
    
    static @Nullable double[] toPrimitive(final @Nullable Double array[]) {
        if (array == null)
            return null;
        else if (array.length == 0)
            return EMPTY_DOUBLE_ARRAY;
        final double result[] = new double[array.length];
        for (int i = 0; i < array.length; i++)
            result[i] = array[i];
        return result;
    }
    
    static @Nullable double[] toPrimitive(final @Nullable Double array[], final double valueForNull) {
        if (array == null)
            return null;
        else if (array.length == 0)
            return EMPTY_DOUBLE_ARRAY;
        final double result[] = new double[array.length];
        for (int i = 0; i < array.length; i++) {
            final Double b = array[i];
            result[i] = b == null ? valueForNull : b;
        }
        return result;
    }
    
    static @Nullable Double[] toObject(final @Nullable double array[]) {
        if (array == null)
            return null;
        else if (array.length == 0)
            return EMPTY_DOUBLE_OBJECT_ARRAY;
        final Double result[] = new Double[array.length];
        for (int i = 0; i < array.length; i++)
            result[i] = array[i];
        return result;
    }
    
    static @Nullable float[] toPrimitive(final @Nullable Float array[]) {
        if (array == null)
            return null;
        else if (array.length == 0)
            return EMPTY_FLOAT_ARRAY;
        final float result[] = new float[array.length];
        for (int i = 0; i < array.length; i++)
            result[i] = array[i];
        return result;
    }
    
    static @Nullable float[] toPrimitive(final @Nullable Float array[], final float valueForNull) {
        if (array == null)
            return null;
        else if (array.length == 0)
            return EMPTY_FLOAT_ARRAY;
        final float result[] = new float[array.length];
        for (int i = 0; i < array.length; i++) {
            final Float b = array[i];
            result[i] = b == null ? valueForNull : b;
        }
        return result;
    }
    
    static @Nullable Float[] toObject(final @Nullable float array[]) {
        if (array == null)
            return null;
        else if (array.length == 0)
            return EMPTY_FLOAT_OBJECT_ARRAY;
        final Float result[] = new Float[array.length];
        for (int i = 0; i < array.length; i++)
            result[i] = array[i];
        return result;
    }
    
    static @Nullable boolean[] toPrimitive(final @Nullable Boolean array[]) {
        if (array == null)
            return null;
        else if (array.length == 0)
            return EMPTY_BOOLEAN_ARRAY;
        final boolean result[] = new boolean[array.length];
        for (int i = 0; i < array.length; i++)
            result[i] = array[i];
        return result;
    }
    
    static @Nullable boolean[] toPrimitive(final @Nullable Boolean array[], final boolean valueForNull) {
        if (array == null)
            return null;
        else if (array.length == 0)
            return EMPTY_BOOLEAN_ARRAY;
        final boolean result[] = new boolean[array.length];
        for (int i = 0; i < array.length; i++) {
            final Boolean b = array[i];
            result[i] = b == null ? valueForNull : b;
        }
        return result;
    }
    
    static @Nullable Boolean[] toObject(final @Nullable boolean array[]) {
        if (array == null)
            return null;
        else if (array.length == 0)
            return EMPTY_BOOLEAN_OBJECT_ARRAY;
        final Boolean result[] = new Boolean[array.length];
        for (int i = 0; i < array.length; i++)
            result[i] = array[i] ? Boolean.TRUE : Boolean.FALSE;
        return result;
    }
    
    static @Nullable Object toPrimitive(final @Nullable Object array) {
        if (array == null)
            return null;
        final Class<?> type = TypeHelper.unboxType(array.getClass().getComponentType());
        if (byte.class.equals(type))
            return toPrimitive((Byte[]) array);
        if (int.class.equals(type))
            return toPrimitive((Integer[]) array);
        if (long.class.equals(type))
            return toPrimitive((Long[]) array);
        if (short.class.equals(type))
            return toPrimitive((Short[]) array);
        if (double.class.equals(type))
            return toPrimitive((Double[]) array);
        if (float.class.equals(type))
            return toPrimitive((Float[]) array);
        if (char.class.equals(type))
            return toPrimitive((Character[]) array);
        if (boolean.class.equals(type))
            return toPrimitive((Boolean[]) array);
        return array;
    }
    
    static boolean isEmpty(final Object array[]) = getLength(array) == 0;
    
    static boolean isEmpty(final long array[]) = getLength(array) == 0;
    
    static boolean isEmpty(final int array[]) = getLength(array) == 0;
    
    static boolean isEmpty(final short array[]) = getLength(array) == 0;
    
    static boolean isEmpty(final char array[]) = getLength(array) == 0;
    
    static boolean isEmpty(final byte array[]) = getLength(array) == 0;
    
    static boolean isEmpty(final double array[]) = getLength(array) == 0;
    
    static boolean isEmpty(final float array[]) = getLength(array) == 0;
    
    static boolean isEmpty(final boolean array[]) = getLength(array) == 0;
    
    static <T> boolean isNotEmpty(final T array[]) = !isEmpty(array);
    
    static boolean isNotEmpty(final long array[]) = !isEmpty(array);
    
    static boolean isNotEmpty(final int array[]) = !isEmpty(array);
    
    static boolean isNotEmpty(final short array[]) = !isEmpty(array);
    
    static boolean isNotEmpty(final char array[]) = !isEmpty(array);
    
    static boolean isNotEmpty(final byte array[]) = !isEmpty(array);
    
    static boolean isNotEmpty(final double array[]) = !isEmpty(array);
    
    static boolean isNotEmpty(final float array[]) = !isEmpty(array);
    
    static boolean isNotEmpty(final boolean array[]) = !isEmpty(array);
    
    @SafeVarargs
    static <T> T[] addAll(final T array1[], final T... array2) {
        if (array1 == null)
            return array2.clone();
        else if (array2 == null)
            return array1.clone();
        final Class<?> type1 = array1.getClass().getComponentType();
        final T joinedArray[] = (T[]) Array.newInstance(type1, array1.length + array2.length);
        System.arraycopy(array1, 0, joinedArray, 0, array1.length);
        try {
            System.arraycopy(array2, 0, joinedArray, array1.length, array2.length);
        } catch (final ArrayStoreException ase) {
            final Class<?> type2 = array2.getClass().getComponentType();
            if (!type1.isAssignableFrom(type2))
                throw new IllegalArgumentException(STR."Cannot store \{type2.getName()} in an array of \{type1.getName()}", ase);
            throw ase; // No, so rethrow original
        }
        return joinedArray;
    }
    
    static boolean[] addAll(final boolean array1[], final boolean... array2) {
        if (array1 == null)
            return array2.clone();
        else if (array2 == null)
            return array1.clone();
        final boolean joinedArray[] = new boolean[array1.length + array2.length];
        System.arraycopy(array1, 0, joinedArray, 0, array1.length);
        System.arraycopy(array2, 0, joinedArray, array1.length, array2.length);
        return joinedArray;
    }
    
    static char[] addAll(final char array1[], final char... array2) {
        if (array1 == null)
            return array2.clone();
        else if (array2 == null)
            return array1.clone();
        final char joinedArray[] = new char[array1.length + array2.length];
        System.arraycopy(array1, 0, joinedArray, 0, array1.length);
        System.arraycopy(array2, 0, joinedArray, array1.length, array2.length);
        return joinedArray;
    }
    
    static byte[] addAll(final byte array1[], final byte... array2) {
        if (array1 == null)
            return array2.clone();
        else if (array2 == null)
            return array1.clone();
        final byte joinedArray[] = new byte[array1.length + array2.length];
        System.arraycopy(array1, 0, joinedArray, 0, array1.length);
        System.arraycopy(array2, 0, joinedArray, array1.length, array2.length);
        return joinedArray;
    }
    
    static short[] addAll(final short array1[], final short... array2) {
        if (array1 == null)
            return array2.clone();
        else if (array2 == null)
            return array1.clone();
        final short joinedArray[] = new short[array1.length + array2.length];
        System.arraycopy(array1, 0, joinedArray, 0, array1.length);
        System.arraycopy(array2, 0, joinedArray, array1.length, array2.length);
        return joinedArray;
    }
    
    static int[] addAll(final int array1[], final int... array2) {
        if (array1 == null)
            return array2.clone();
        else if (array2 == null)
            return array1.clone();
        final int joinedArray[] = new int[array1.length + array2.length];
        System.arraycopy(array1, 0, joinedArray, 0, array1.length);
        System.arraycopy(array2, 0, joinedArray, array1.length, array2.length);
        return joinedArray;
    }
    
    static long[] addAll(final long array1[], final long... array2) {
        if (array1 == null)
            return array2.clone();
        else if (array2 == null)
            return array1.clone();
        final long joinedArray[] = new long[array1.length + array2.length];
        System.arraycopy(array1, 0, joinedArray, 0, array1.length);
        System.arraycopy(array2, 0, joinedArray, array1.length, array2.length);
        return joinedArray;
    }
    
    static float[] addAll(final float array1[], final float... array2) {
        if (array1 == null)
            return array2.clone();
        else if (array2 == null)
            return array1.clone();
        final float joinedArray[] = new float[array1.length + array2.length];
        System.arraycopy(array1, 0, joinedArray, 0, array1.length);
        System.arraycopy(array2, 0, joinedArray, array1.length, array2.length);
        return joinedArray;
    }
    
    static double[] addAll(final double array1[], final double... array2) {
        if (array1 == null)
            return array2.clone();
        else if (array2 == null)
            return array1.clone();
        final double joinedArray[] = new double[array1.length + array2.length];
        System.arraycopy(array1, 0, joinedArray, 0, array1.length);
        System.arraycopy(array2, 0, joinedArray, array1.length, array2.length);
        return joinedArray;
    }
    
    static <T> T[] add(final @Nullable T array[], final @Nullable T element) {
        final Class<?> type;
        if (array != null)
            type = array.getClass().getComponentType();
        else if (element != null)
            type = element.getClass();
        else
            throw new IllegalArgumentException("Arguments cannot both be null");
        final T newArray[] = (T[]) copyArrayGrow1(array, type);
        newArray[newArray.length - 1] = element;
        return newArray;
    }
    
    static @Nullable boolean[] insert(final @Nullable boolean array[], final boolean element) = array == null ? add(array, element) : insert(0, array, element);
    
    static @Nullable byte[] insert(final @Nullable byte array[], final byte element) = array == null ? add(array, element) : insert(0, array, element);
    
    static @Nullable char[] insert(final @Nullable char array[], final char element) = array == null ? add(array, element) : insert(0, array, element);
    
    static @Nullable double[] insert(final @Nullable double array[], final double element) = array == null ? add(array, element) : insert(0, array, element);
    
    static @Nullable float[] insert(final @Nullable float array[], final float element) = array == null ? add(array, element) : insert(0, array, element);
    
    static @Nullable int[] insert(final @Nullable int array[], final int element) = array == null ? add(array, element) : insert(0, array, element);
    
    static @Nullable long[] insert(final @Nullable long array[], final long element) = array == null ? add(array, element) : insert(0, array, element);
    
    static @Nullable short[] insert(final @Nullable short array[], final short element) = array == null ? add(array, element) : insert(0, array, element);
    
    static @Nullable <T> T[] insert(final @Nullable T array[], final T element) = array == null ? add(array, element) : insert(0, array, element);
    
    static @Nullable boolean[] add(final @Nullable boolean array[], final boolean element) {
        final boolean newArray[] = (boolean[]) copyArrayGrow1(array, Boolean.TYPE);
        newArray[newArray.length - 1] = element;
        return newArray;
    }
    
    static @Nullable byte[] add(final @Nullable byte array[], final byte element) {
        final byte newArray[] = (byte[]) copyArrayGrow1(array, Byte.TYPE);
        newArray[newArray.length - 1] = element;
        return newArray;
    }
    
    static @Nullable char[] add(final @Nullable char array[], final char element) {
        final char newArray[] = (char[]) copyArrayGrow1(array, Character.TYPE);
        newArray[newArray.length - 1] = element;
        return newArray;
    }
    
    static @Nullable double[] add(final @Nullable double array[], final double element) {
        final double newArray[] = (double[]) copyArrayGrow1(array, Double.TYPE);
        newArray[newArray.length - 1] = element;
        return newArray;
    }
    
    static @Nullable float[] add(final @Nullable float array[], final float element) {
        final float newArray[] = (float[]) copyArrayGrow1(array, Float.TYPE);
        newArray[newArray.length - 1] = element;
        return newArray;
    }
    
    static @Nullable int[] add(final @Nullable int array[], final int element) {
        final int newArray[] = (int[]) copyArrayGrow1(array, Integer.TYPE);
        newArray[newArray.length - 1] = element;
        return newArray;
    }
    
    static @Nullable long[] add(final @Nullable long array[], final long element) {
        final long newArray[] = (long[]) copyArrayGrow1(array, Long.TYPE);
        newArray[newArray.length - 1] = element;
        return newArray;
    }
    
    static @Nullable short[] add(final @Nullable short array[], final short element) {
        final short newArray[] = (short[]) copyArrayGrow1(array, Short.TYPE);
        newArray[newArray.length - 1] = element;
        return newArray;
    }
    
    private static Object copyArrayGrow1(final @Nullable Object array, final Class<?> newArrayComponentType) {
        if (array != null) {
            final int arrayLength = Array.getLength(array);
            final Object newArray = Array.newInstance(array.getClass().getComponentType(), arrayLength + 1);
            System.arraycopy(array, 0, newArray, 0, arrayLength);
            return newArray;
        }
        return Array.newInstance(newArrayComponentType, 1);
    }
    
    // remove() always creates an array of the same type as its input
    static <T> T[] remove(final T array[], final int index) = (T[]) remove((Object) array, index);
    
    static <T> T[] removeElement(final T array[], final Object element) {
        final int index = indexOf(array, element);
        if (index == INDEX_NOT_FOUND)
            return array.clone();
        return remove(array, index);
    }
    
    static boolean[] remove(final boolean array[], final int index) = (boolean[]) remove((Object) array, index);
    
    static boolean[] removeElement(final boolean array[], final boolean element) {
        final int index = indexOf(array, element);
        if (index == INDEX_NOT_FOUND)
            return array.clone();
        return remove(array, index);
    }
    
    static byte[] remove(final byte array[], final int index) = (byte[]) remove((Object) array, index);
    
    static byte[] removeElement(final byte array[], final byte element) {
        final int index = indexOf(array, element);
        if (index == INDEX_NOT_FOUND)
            return array.clone();
        return remove(array, index);
    }
    
    static char[] remove(final char array[], final int index) = (char[]) remove((Object) array, index);
    
    static char[] removeElement(final char array[], final char element) {
        final int index = indexOf(array, element);
        if (index == INDEX_NOT_FOUND)
            return array.clone();
        return remove(array, index);
    }
    
    static double[] remove(final double array[], final int index) = (double[]) remove((Object) array, index);
    
    static double[] removeElement(final double array[], final double element) {
        final int index = indexOf(array, element);
        if (index == INDEX_NOT_FOUND)
            return array.clone();
        return remove(array, index);
    }
    
    static float[] remove(final float array[], final int index) = (float[]) remove((Object) array, index);
    
    static float[] removeElement(final float array[], final float element) {
        final int index = indexOf(array, element);
        if (index == INDEX_NOT_FOUND)
            return array.clone();
        return remove(array, index);
    }
    
    static int[] remove(final int array[], final int index) = (int[]) remove((Object) array, index);
    
    static int[] removeElement(final int array[], final int element) {
        final int index = indexOf(array, element);
        if (index == INDEX_NOT_FOUND)
            return array.clone();
        return remove(array, index);
    }
    
    static long[] remove(final long array[], final int index) = (long[]) remove((Object) array, index);
    
    static long[] removeElement(final long array[], final long element) {
        final int index = indexOf(array, element);
        if (index == INDEX_NOT_FOUND)
            return array.clone();
        return remove(array, index);
    }
    
    static short[] remove(final short array[], final int index) = (short[]) remove((Object) array, index);
    
    static short[] removeElement(final short array[], final short element) {
        final int index = indexOf(array, element);
        if (index == INDEX_NOT_FOUND)
            return array.clone();
        return remove(array, index);
    }
    
    private static Object remove(final Object array, final int index) {
        assert array.getClass().isArray();
        final int length = getLength(array);
        if (index < 0 || index >= length)
            throw new IndexOutOfBoundsException(STR."Index: \{index}, Length: \{length}");
        final Object result = Array.newInstance(array.getClass().getComponentType(), length - 1);
        System.arraycopy(array, 0, result, 0, index);
        if (index < length - 1)
            
            System.arraycopy(array, index + 1, result, index, length - index - 1);
        return result;
    }
    
    // removeAll() always creates an array of the same type as its input
    static <T> T[] removeAll(final T array[], final int... indices) = (T[]) removeAll((Object) array, indices);
    
    static byte[] removeAll(final byte array[], final int... indices) = (byte[]) removeAll((Object) array, indices);
    
    static short[] removeAll(final short array[], final int... indices) = (short[]) removeAll((Object) array, indices);
    
    static int[] removeAll(final int array[], final int... indices) = (int[]) removeAll((Object) array, indices);
    
    static char[] removeAll(final char array[], final int... indices) = (char[]) removeAll((Object) array, indices);
    
    static long[] removeAll(final long array[], final int... indices) = (long[]) removeAll((Object) array, indices);
    
    static float[] removeAll(final float array[], final int... indices) = (float[]) removeAll((Object) array, indices);
    
    static double[] removeAll(final double array[], final int... indices) = (double[]) removeAll((Object) array, indices);
    
    static boolean[] removeAll(final boolean array[], final int... indices) = (boolean[]) removeAll((Object) array, indices);
    
    private static Object removeAll(final Object array, final int... indices) {
        final int length = getLength(array);
        int diff = 0; // number of distinct indexes, i.e. number of entries that will be removed
        final int clonedIndices[] = indices.clone();
        Arrays.sort(clonedIndices);
        // identify length of result array
        if (isNotEmpty(clonedIndices)) {
            int i = clonedIndices.length;
            int prevIndex = length;
            while (--i >= 0) {
                final int index = clonedIndices[i];
                if (index < 0 || index >= length)
                    throw new IndexOutOfBoundsException(STR."Index: \{index}, Length: \{length}");
                if (index >= prevIndex)
                    continue;
                diff++;
                prevIndex = index;
            }
        }
        // create result array
        final Object result = Array.newInstance(array.getClass().getComponentType(), length - diff);
        if (diff < length) {
            int end = length; // index just after last copy
            int dest = length - diff; // number of entries so far not copied
            for (int i = clonedIndices.length - 1; i >= 0; i--) {
                final int index = clonedIndices[i];
                if (end - index > 1) { // same as (cp > 0)
                    final int cp = end - index - 1;
                    dest -= cp;
                    System.arraycopy(array, index + 1, result, dest, cp);
                    // After this copy, we still have room for dest items.
                }
                end = index;
            }
            if (end > 0)
                System.arraycopy(array, 0, result, 0, end);
        }
        return result;
    }
    
    static Object removeAll(final Object array, final BitSet indices) {
        final int srcLength = getLength(array);
        final int removals = indices.cardinality(); // true bits are items to remove
        final Object result = Array.newInstance(array.getClass().getComponentType(), srcLength - removals);
        int srcIndex = 0;
        int destIndex = 0;
        int count;
        int set;
        while ((set = indices.nextSetBit(srcIndex)) != -1) {
            count = set - srcIndex;
            if (count > 0) {
                System.arraycopy(array, srcIndex, result, destIndex, count);
                destIndex += count;
            }
            srcIndex = indices.nextClearBit(set);
        }
        count = srcLength - srcIndex;
        if (count > 0)
            System.arraycopy(array, srcIndex, result, destIndex, count);
        return result;
    }
    
    static <T extends Comparable<? super T>> boolean isSorted(final T array[]) = isSorted(array, Comparator.naturalOrder());
    
    static <T> boolean isSorted(final T array[], final Comparator<T> comparator) {
        if (comparator == null)
            throw new IllegalArgumentException("Comparator should not be null.");
        if (array == null || array.length < 2)
            return true;
        T previous = array[0];
        final int n = array.length;
        for (int i = 1; i < n; i++) {
            final T current = array[i];
            if (comparator.compare(previous, current) > 0)
                return false;
            previous = current;
        }
        return true;
    }
    
    static boolean isSorted(final int array[]) {
        if (array == null || array.length < 2)
            return true;
        int previous = array[0];
        final int n = array.length;
        for (int i = 1; i < n; i++) {
            final int current = array[i];
            if (previous > current)
                return false;
            previous = current;
        }
        return true;
    }
    
    static boolean isSorted(final long array[]) {
        if (array == null || array.length < 2)
            return true;
        long previous = array[0];
        final int n = array.length;
        for (int i = 1; i < n; i++) {
            final long current = array[i];
            if (previous > current)
                return false;
            previous = current;
        }
        return true;
    }
    
    static boolean isSorted(final short array[]) {
        if (array == null || array.length < 2)
            return true;
        short previous = array[0];
        final int n = array.length;
        for (int i = 1; i < n; i++) {
            final short current = array[i];
            if (previous > current)
                return false;
            previous = current;
        }
        return true;
    }
    
    static boolean isSorted(final double array[]) {
        if (array == null || array.length < 2)
            return true;
        double previous = array[0];
        final int n = array.length;
        for (int i = 1; i < n; i++) {
            final double current = array[i];
            if (previous > current)
                return false;
            previous = current;
        }
        return true;
    }
    
    static boolean isSorted(final float array[]) {
        if (array == null || array.length < 2)
            return true;
        float previous = array[0];
        final int n = array.length;
        for (int i = 1; i < n; i++) {
            final float current = array[i];
            if (previous > current)
                return false;
            previous = current;
        }
        return true;
    }
    
    static boolean isSorted(final byte array[]) {
        if (array == null || array.length < 2)
            return true;
        byte previous = array[0];
        final int n = array.length;
        for (int i = 1; i < n; i++) {
            final byte current = array[i];
            if (previous > current)
                return false;
            previous = current;
        }
        return true;
    }
    
    static boolean isSorted(final char array[]) {
        if (array == null || array.length < 2)
            return true;
        char previous = array[0];
        final int n = array.length;
        for (int i = 1; i < n; i++) {
            final char current = array[i];
            if (previous > current)
                return false;
            previous = current;
        }
        return true;
    }
    
    static boolean isSorted(final boolean array[]) {
        if (array == null || array.length < 2)
            return true;
        boolean previous = false;
        final int n = array.length;
        for (int i = 1; i < n; i++) {
            final boolean current = array[i];
            if (previous && !current)
                return false;
            previous = current;
        }
        return true;
    }
    
    static boolean[] removeAllOccurrences(final boolean array[], final boolean element) {
        int index = indexOf(array, element);
        if (index == INDEX_NOT_FOUND)
            return array.clone();
        final int indices[] = new int[array.length - index];
        indices[0] = index;
        int count = 1;
        while ((index = indexOf(array, element, indices[count - 1] + 1)) != INDEX_NOT_FOUND)
            indices[count++] = index;
        return removeAll(array, Arrays.copyOf(indices, count));
    }
    
    static char[] removeAllOccurrences(final char array[], final char element) {
        int index = indexOf(array, element);
        if (index == INDEX_NOT_FOUND)
            return array.clone();
        final int indices[] = new int[array.length - index];
        indices[0] = index;
        int count = 1;
        while ((index = indexOf(array, element, indices[count - 1] + 1)) != INDEX_NOT_FOUND)
            indices[count++] = index;
        return removeAll(array, Arrays.copyOf(indices, count));
    }
    
    static byte[] removeAllOccurrences(final byte array[], final byte element) {
        int index = indexOf(array, element);
        if (index == INDEX_NOT_FOUND)
            return array.clone();
        final int indices[] = new int[array.length - index];
        indices[0] = index;
        int count = 1;
        while ((index = indexOf(array, element, indices[count - 1] + 1)) != INDEX_NOT_FOUND)
            indices[count++] = index;
        return removeAll(array, Arrays.copyOf(indices, count));
    }
    
    static short[] removeAllOccurrences(final short array[], final short element) {
        int index = indexOf(array, element);
        if (index == INDEX_NOT_FOUND)
            return array.clone();
        final int indices[] = new int[array.length - index];
        indices[0] = index;
        int count = 1;
        while ((index = indexOf(array, element, indices[count - 1] + 1)) != INDEX_NOT_FOUND)
            indices[count++] = index;
        return removeAll(array, Arrays.copyOf(indices, count));
    }
    
    static int[] removeAllOccurrences(final int array[], final int element) {
        int index = indexOf(array, element);
        if (index == INDEX_NOT_FOUND)
            return array.clone();
        final int indices[] = new int[array.length - index];
        indices[0] = index;
        int count = 1;
        while ((index = indexOf(array, element, indices[count - 1] + 1)) != INDEX_NOT_FOUND)
            indices[count++] = index;
        return removeAll(array, Arrays.copyOf(indices, count));
    }
    
    static long[] removeAllOccurrences(final long array[], final long element) {
        int index = indexOf(array, element);
        if (index == INDEX_NOT_FOUND)
            return array.clone();
        final int indices[] = new int[array.length - index];
        indices[0] = index;
        int count = 1;
        while ((index = indexOf(array, element, indices[count - 1] + 1)) != INDEX_NOT_FOUND)
            indices[count++] = index;
        return removeAll(array, Arrays.copyOf(indices, count));
    }
    
    static float[] removeAllOccurrences(final float array[], final float element) {
        int index = indexOf(array, element);
        if (index == INDEX_NOT_FOUND)
            return array.clone();
        final int indices[] = new int[array.length - index];
        indices[0] = index;
        int count = 1;
        while ((index = indexOf(array, element, indices[count - 1] + 1)) != INDEX_NOT_FOUND)
            indices[count++] = index;
        return removeAll(array, Arrays.copyOf(indices, count));
    }
    
    static double[] removeAllOccurrences(final double array[], final double element) {
        int index = indexOf(array, element);
        if (index == INDEX_NOT_FOUND)
            return array.clone();
        final int indices[] = new int[array.length - index];
        indices[0] = index;
        int count = 1;
        while ((index = indexOf(array, element, indices[count - 1] + 1)) != INDEX_NOT_FOUND)
            indices[count++] = index;
        return removeAll(array, Arrays.copyOf(indices, count));
    }
    
    static <T> T[] removeAllOccurrences(final T array[], final T element) {
        int index = indexOf(array, element);
        if (index == INDEX_NOT_FOUND)
            return array.clone();
        final int indices[] = new int[array.length - index];
        indices[0] = index;
        int count = 1;
        while ((index = indexOf(array, element, indices[count - 1] + 1)) != INDEX_NOT_FOUND)
            indices[count++] = index;
        return removeAll(array, Arrays.copyOf(indices, count));
    }
    
    static @Nullable boolean[] insert(final int index, final @Nullable boolean array[], final boolean... values) {
        if (array == null)
            return null;
        if (isEmpty(values))
            return array.clone();
        if (index < 0 || index > array.length)
            throw new IndexOutOfBoundsException(STR."Index: \{index}, Length: \{array.length}");
        final boolean result[] = new boolean[array.length + values.length];
        System.arraycopy(values, 0, result, index, values.length);
        if (index > 0)
            System.arraycopy(array, 0, result, 0, index);
        if (index < array.length)
            System.arraycopy(array, index, result, index + values.length, array.length - index);
        return result;
    }
    
    static @Nullable byte[] insert(final int index, final @Nullable byte array[], final byte... values) {
        if (array == null)
            return null;
        if (isEmpty(values))
            return array.clone();
        if (index < 0 || index > array.length)
            throw new IndexOutOfBoundsException(STR."Index: \{index}, Length: \{array.length}");
        final byte result[] = new byte[array.length + values.length];
        System.arraycopy(values, 0, result, index, values.length);
        if (index > 0)
            System.arraycopy(array, 0, result, 0, index);
        if (index < array.length)
            System.arraycopy(array, index, result, index + values.length, array.length - index);
        return result;
    }
    
    static @Nullable char[] insert(final int index, final @Nullable char array[], final char... values) {
        if (array == null)
            return null;
        if (isEmpty(values))
            return array.clone();
        if (index < 0 || index > array.length)
            throw new IndexOutOfBoundsException(STR."Index: \{index}, Length: \{array.length}");
        final char result[] = new char[array.length + values.length];
        System.arraycopy(values, 0, result, index, values.length);
        if (index > 0)
            System.arraycopy(array, 0, result, 0, index);
        if (index < array.length)
            System.arraycopy(array, index, result, index + values.length, array.length - index);
        return result;
    }
    
    static @Nullable double[] insert(final int index, final @Nullable double array[], final double... values) {
        if (array == null)
            return null;
        if (isEmpty(values))
            return array.clone();
        if (index < 0 || index > array.length)
            throw new IndexOutOfBoundsException(STR."Index: \{index}, Length: \{array.length}");
        final double result[] = new double[array.length + values.length];
        System.arraycopy(values, 0, result, index, values.length);
        if (index > 0)
            System.arraycopy(array, 0, result, 0, index);
        if (index < array.length)
            System.arraycopy(array, index, result, index + values.length, array.length - index);
        return result;
    }
    
    static @Nullable float[] insert(final int index, final @Nullable float array[], final float... values) {
        if (array == null)
            return null;
        if (isEmpty(values))
            return array.clone();
        if (index < 0 || index > array.length)
            throw new IndexOutOfBoundsException(STR."Index: \{index}, Length: \{array.length}");
        final float result[] = new float[array.length + values.length];
        System.arraycopy(values, 0, result, index, values.length);
        if (index > 0)
            System.arraycopy(array, 0, result, 0, index);
        if (index < array.length)
            System.arraycopy(array, index, result, index + values.length, array.length - index);
        return result;
    }
    
    static @Nullable int[] insert(final int index, final @Nullable int array[], final int... values) {
        if (array == null)
            return null;
        if (isEmpty(values))
            return array.clone();
        if (index < 0 || index > array.length)
            throw new IndexOutOfBoundsException(STR."Index: \{index}, Length: \{array.length}");
        final int result[] = new int[array.length + values.length];
        System.arraycopy(values, 0, result, index, values.length);
        if (index > 0)
            System.arraycopy(array, 0, result, 0, index);
        if (index < array.length)
            System.arraycopy(array, index, result, index + values.length, array.length - index);
        return result;
    }
    
    static @Nullable long[] insert(final int index, final @Nullable long array[], final long... values) {
        if (array == null)
            return null;
        if (isEmpty(values))
            return array.clone();
        if (index < 0 || index > array.length)
            throw new IndexOutOfBoundsException(STR."Index: \{index}, Length: \{array.length}");
        final long result[] = new long[array.length + values.length];
        System.arraycopy(values, 0, result, index, values.length);
        if (index > 0)
            System.arraycopy(array, 0, result, 0, index);
        if (index < array.length)
            System.arraycopy(array, index, result, index + values.length, array.length - index);
        return result;
    }
    
    static @Nullable short[] insert(final int index, final @Nullable short array[], final short... values) {
        if (array == null)
            return null;
        if (isEmpty(values))
            return array.clone();
        if (index < 0 || index > array.length)
            throw new IndexOutOfBoundsException(STR."Index: \{index}, Length: \{array.length}");
        final short result[] = new short[array.length + values.length];
        System.arraycopy(values, 0, result, index, values.length);
        if (index > 0)
            System.arraycopy(array, 0, result, 0, index);
        if (index < array.length)
            System.arraycopy(array, index, result, index + values.length, array.length - index);
        return result;
    }
    
    @SafeVarargs
    static @Nullable <T> T[] insert(final int index, final @Nullable T array[], final T... values) {
        if (array == null)
            return null;
        if (isEmpty(values))
            return array.clone();
        if (index < 0 || index > array.length)
            throw new IndexOutOfBoundsException(STR."Index: \{index}, Length: \{array.length}");
        final Class<?> type = array.getClass().getComponentType();
        final T result[] = (T[]) Array.newInstance(type, array.length + values.length);
        System.arraycopy(values, 0, result, index, values.length);
        if (index > 0)
            System.arraycopy(array, 0, result, 0, index);
        if (index < array.length)
            System.arraycopy(array, index, result, index + values.length, array.length - index);
        return result;
    }
    
    static void shuffle(final Object array[], final RandomGenerator random) {
        for (int i = array.length; i > 1; i--)
            swap(array, i - 1, random.nextInt(i), 1);
    }
    
    static void shuffle(final boolean array[], final RandomGenerator random) {
        for (int i = array.length; i > 1; i--)
            swap(array, i - 1, random.nextInt(i), 1);
    }
    
    static void shuffle(final byte array[], final RandomGenerator random) {
        for (int i = array.length; i > 1; i--)
            swap(array, i - 1, random.nextInt(i), 1);
    }
    
    static void shuffle(final char array[], final RandomGenerator random) {
        for (int i = array.length; i > 1; i--)
            swap(array, i - 1, random.nextInt(i), 1);
    }
    
    static void shuffle(final short array[], final RandomGenerator random) {
        for (int i = array.length; i > 1; i--)
            swap(array, i - 1, random.nextInt(i), 1);
    }
    
    static void shuffle(final int array[], final RandomGenerator random) {
        for (int i = array.length; i > 1; i--)
            swap(array, i - 1, random.nextInt(i), 1);
    }
    
    static void shuffle(final long array[], final RandomGenerator random) {
        for (int i = array.length; i > 1; i--)
            swap(array, i - 1, random.nextInt(i), 1);
    }
    
    static void shuffle(final float array[], final RandomGenerator random) {
        for (int i = array.length; i > 1; i--)
            swap(array, i - 1, random.nextInt(i), 1);
    }
    
    static void shuffle(final double array[], final RandomGenerator random) {
        for (int i = array.length; i > 1; i--)
            swap(array, i - 1, random.nextInt(i), 1);
    }
    
    static boolean isArrayIndexValid(final Object array, final int index) = index >= 0 && index < getLength(array);
    
}
