package amadeus.maho.util.runtime;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import amadeus.maho.lang.Extension;
import amadeus.maho.lang.inspection.Nullable;

@Extension
public interface ByteBufferHelper {
    
    static ByteBuffer ensureBufferCapacity(final ByteBuffer buffer, final int size, final boolean copy = true) {
        if (size < 1)
            return buffer;
        final int limit = buffer.position() + size;
        if (buffer.capacity() >= limit) {
            if (buffer.limit() < limit)
                buffer.limit(limit);
            return buffer;
        }
        final ByteBuffer result = buffer.isDirect() ? ByteBuffer.allocateDirect(limit) : ByteBuffer.allocate(limit);
        result.order(buffer.order());
        buffer.flip();
        if (copy)
            result.put(buffer);
        return result;
    }
    
    @Extension.Operator("<<")
    static void putUTF8String(final ByteBuffer buffer, final @Nullable String string) = putString(buffer, string, StandardCharsets.UTF_8);
    
    static void putString(final ByteBuffer buffer, final @Nullable String string, final Charset charset = Charset.defaultCharset()) {
        if (string == null) {
            buffer.putInt(-1);
            return;
        }
        final byte data[] = string.getBytes(charset);
        buffer.putInt(data.length)
                .put(data);
    }
    
    static @Nullable String getUTF8String(final ByteBuffer buffer) = getString(buffer, StandardCharsets.UTF_8);
    
    static @Nullable String getString(final ByteBuffer buffer, final Charset charset = Charset.defaultCharset()) {
        final int size = buffer.getInt();
        if (size == -1)
            return null;
        final byte data[] = new byte[size];
        buffer.get(data);
        return { data, charset };
    }
    
    static byte GET(final ByteBuffer buffer, final int index) = buffer.get(index);
    
    static void PUT(final ByteBuffer buffer, final int index, final byte value) = buffer.put(index, value);
    
    static ByteBuffer LTLT(final ByteBuffer buffer, final byte value) = buffer.put(value);
    
    static ByteBuffer LTLT(final ByteBuffer buffer, final char value) = buffer.putChar(value);
    
    static ByteBuffer LTLT(final ByteBuffer buffer, final double value) = buffer.putDouble(value);
    
    static ByteBuffer LTLT(final ByteBuffer buffer, final float value) = buffer.putFloat(value);
    
    static ByteBuffer LTLT(final ByteBuffer buffer, final int value) = buffer.putInt(value);
    
    static ByteBuffer LTLT(final ByteBuffer buffer, final long value) = buffer.putLong(value);
    
    static ByteBuffer LTLT(final ByteBuffer buffer, final short value) = buffer.putShort(value);
    
    static ByteBuffer LTLT(final ByteBuffer buffer, final boolean value) = buffer.put(value ? (byte) 1 : 0);
    
    static ByteBuffer LTLT(final ByteBuffer buffer, final byte value[]) = buffer.put(value);
    
    static ByteBuffer LTLT(final ByteBuffer buffer, final ByteBuffer value) = buffer.put(value);
    
    static byte GTGT(final ByteBuffer buffer, final int index) = buffer.get(index);
    
    static ByteBuffer GTGT(final ByteBuffer buffer, final byte array[]) = buffer.get(array);
    
    static int XOR(final ByteBuffer buffer, final ByteBuffer target) = buffer.mismatch(target);
    
    static ByteBuffer MUL(final ByteBuffer buffer, final ByteOrder order) = buffer.order(order);
    
    static ByteBuffer DIV(final ByteBuffer buffer, final int limit) = buffer.limit(limit);
    
    static ByteBuffer OR(final ByteBuffer buffer, final int position) = buffer.position(position);
    
    static byte TILDE(final ByteBuffer buffer) = buffer.get();
    
    static ByteBuffer NOT(final ByteBuffer buffer) = buffer.rewind();
    
    // float
    
    static float GET(final FloatBuffer buffer, final int index) = buffer.get(index);
    
    static void PUT(final FloatBuffer buffer, final int index, final float value) = buffer.put(index, value);
    
    static FloatBuffer LTLT(final FloatBuffer buffer, final float value) = buffer.put(value);
    
    static FloatBuffer LTLT(final FloatBuffer buffer, final float value[]) = buffer.put(value);
    
    static FloatBuffer LTLT(final FloatBuffer buffer, final FloatBuffer value) = buffer.put(value);
    
    static float GTGT(final FloatBuffer buffer, final int index) = buffer.get(index);
    
    static FloatBuffer GTGT(final FloatBuffer buffer, final float array[]) = buffer.get(array);
    
    static int XOR(final FloatBuffer buffer, final FloatBuffer target) = buffer.mismatch(target);
    
    static FloatBuffer DIV(final FloatBuffer buffer, final int limit) = buffer.limit(limit);
    
    static FloatBuffer OR(final FloatBuffer buffer, final int position) = buffer.position(position);
    
    static float TILDE(final FloatBuffer buffer) = buffer.get();
    
    static FloatBuffer NOT(final FloatBuffer buffer) = buffer.rewind();
    
    // double
    
    static double GET(final DoubleBuffer buffer, final int index) = buffer.get(index);
    
    static void PUT(final DoubleBuffer buffer, final int index, final double value) = buffer.put(index, value);
    
    static DoubleBuffer LTLT(final DoubleBuffer buffer, final double value) = buffer.put(value);
    
    static DoubleBuffer LTLT(final DoubleBuffer buffer, final double value[]) = buffer.put(value);
    
    static DoubleBuffer LTLT(final DoubleBuffer buffer, final DoubleBuffer value) = buffer.put(value);
    
    static double GTGT(final DoubleBuffer buffer, final int index) = buffer.get(index);
    
    static DoubleBuffer GTGT(final DoubleBuffer buffer, final double array[]) = buffer.get(array);
    
    static int XOR(final DoubleBuffer buffer, final DoubleBuffer target) = buffer.mismatch(target);
    
    static DoubleBuffer DIV(final DoubleBuffer buffer, final int limit) = buffer.limit(limit);
    
    static DoubleBuffer OR(final DoubleBuffer buffer, final int position) = buffer.position(position);
    
    static double TILDE(final DoubleBuffer buffer) = buffer.get();
    
    static DoubleBuffer NOT(final DoubleBuffer buffer) = buffer.rewind();
    
    // int
    
    static int GET(final IntBuffer buffer, final int index) = buffer.get(index);
    
    static void PUT(final IntBuffer buffer, final int index, final int value) = buffer.put(index, value);
    
    static IntBuffer LTLT(final IntBuffer buffer, final int value) = buffer.put(value);
    
    static IntBuffer LTLT(final IntBuffer buffer, final int value[]) = buffer.put(value);
    
    static IntBuffer LTLT(final IntBuffer buffer, final IntBuffer value) = buffer.put(value);
    
    static int GTGT(final IntBuffer buffer, final int index) = buffer.get(index);
    
    static IntBuffer GTGT(final IntBuffer buffer, final int array[]) = buffer.get(array);
    
    static int XOR(final IntBuffer buffer, final IntBuffer target) = buffer.mismatch(target);
    
    static IntBuffer DIV(final IntBuffer buffer, final int limit) = buffer.limit(limit);
    
    static IntBuffer OR(final IntBuffer buffer, final int position) = buffer.position(position);
    
    static int TILDE(final IntBuffer buffer) = buffer.get();
    
    static IntBuffer NOT(final IntBuffer buffer) = buffer.rewind();
    
    // short
    
    static short GET(final ShortBuffer buffer, final int index) = buffer.get(index);
    
    static void PUT(final ShortBuffer buffer, final int index, final short value) = buffer.put(index, value);
    
    static ShortBuffer LTLT(final ShortBuffer buffer, final short value) = buffer.put(value);
    
    static ShortBuffer LTLT(final ShortBuffer buffer, final short value[]) = buffer.put(value);
    
    static ShortBuffer LTLT(final ShortBuffer buffer, final ShortBuffer value) = buffer.put(value);
    
    static short GTGT(final ShortBuffer buffer, final int index) = buffer.get(index);
    
    static ShortBuffer GTGT(final ShortBuffer buffer, final short array[]) = buffer.get(array);
    
    static int XOR(final ShortBuffer buffer, final ShortBuffer target) = buffer.mismatch(target);
    
    static ShortBuffer DIV(final ShortBuffer buffer, final int limit) = buffer.limit(limit);
    
    static ShortBuffer OR(final ShortBuffer buffer, final int position) = buffer.position(position);
    
    static short TILDE(final ShortBuffer buffer) = buffer.get();
    
    static ShortBuffer NOT(final ShortBuffer buffer) = buffer.rewind();
    
    // long
    
    static long GET(final LongBuffer buffer, final int index) = buffer.get(index);
    
    static void PUT(final LongBuffer buffer, final int index, final long value) = buffer.put(index, value);
    
    static LongBuffer LTLT(final LongBuffer buffer, final long value) = buffer.put(value);
    
    static LongBuffer LTLT(final LongBuffer buffer, final long value[]) = buffer.put(value);
    
    static LongBuffer LTLT(final LongBuffer buffer, final LongBuffer value) = buffer.put(value);
    
    static long GTGT(final LongBuffer buffer, final int index) = buffer.get(index);
    
    static LongBuffer GTGT(final LongBuffer buffer, final long array[]) = buffer.get(array);
    
    static int XOR(final LongBuffer buffer, final LongBuffer target) = buffer.mismatch(target);
    
    static LongBuffer DIV(final LongBuffer buffer, final int limit) = buffer.limit(limit);
    
    static LongBuffer OR(final LongBuffer buffer, final int position) = buffer.position(position);
    
    static long TILDE(final LongBuffer buffer) = buffer.get();
    
    static LongBuffer NOT(final LongBuffer buffer) = buffer.rewind();
    
    // char
    
    static char GET(final CharBuffer buffer, final int index) = buffer.get(index);
    
    static void PUT(final CharBuffer buffer, final int index, final char value) = buffer.put(index, value);
    
    static CharBuffer LTLT(final CharBuffer buffer, final char value) = buffer.put(value);
    
    static CharBuffer LTLT(final CharBuffer buffer, final char value[]) = buffer.put(value);
    
    static CharBuffer LTLT(final CharBuffer buffer, final CharBuffer value) = buffer.put(value);
    
    static char GTGT(final CharBuffer buffer, final int index) = buffer.get(index);
    
    static CharBuffer GTGT(final CharBuffer buffer, final char array[]) = buffer.get(array);
    
    static int XOR(final CharBuffer buffer, final CharBuffer target) = buffer.mismatch(target);
    
    static CharBuffer DIV(final CharBuffer buffer, final int limit) = buffer.limit(limit);
    
    static CharBuffer OR(final CharBuffer buffer, final int position) = buffer.position(position);
    
    static char TILDE(final CharBuffer buffer) = buffer.get();
    
    static CharBuffer NOT(final CharBuffer buffer) = buffer.rewind();
    
}
