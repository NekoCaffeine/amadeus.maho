package amadeus.maho.util.serialization;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.foreign.MemorySegment;
import java.util.function.LongConsumer;
import java.util.stream.LongStream;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Extension;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

@Extension
public class BitAccess {
    
    private static final int MAX_SIZE = 1 << 6;
    
    private static final long MASKS[] = new long[MAX_SIZE + 1];
    
    static {
        for (int i = 1; i <= MAX_SIZE; i++)
            MASKS[i] = MASKS[i - 1] << 1 | 1;
    }
    
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Input {
        
        final InputStream in;
        
        long cache;
        
        int cachedLength;
        
        public long readNBits(final int length, final @Nullable boolean hasNext[] = null) throws IOException {
            if (length < 0 || length > MAX_SIZE)
                throw new IllegalArgumentException(STR."length: \{length}");
            while (cachedLength < length && cachedLength < 7 * 8 + 1) {
                final long next = in.read();
                if (next < 0)
                    if (hasNext != null) {
                        hasNext[0] = false;
                        return -1L;
                    } else
                        throw new EOFException();
                cache = cache << Byte.SIZE | next;
                cachedLength += Byte.SIZE;
            }
            if (cachedLength < length) {
                final long next = in.read();
                if (next < 0)
                    if (hasNext != null) {
                        hasNext[0] = false;
                        return -1L;
                    } else
                        throw new EOFException();
                final int lastPartCount = length - cachedLength, remainingBits = Byte.SIZE - lastPartCount;
                final long lastPart = next >>> remainingBits & MASKS[lastPartCount];
                final long result = (cache << lastPartCount | lastPart) & MASKS[length];
                cache = next & MASKS[remainingBits];
                cachedLength = remainingBits;
                return result;
            }
            final long result;
            result = cache >>> cachedLength - length & MASKS[length];
            cachedLength -= length;
            return result;
        }
        
        @SneakyThrows
        public LongStream stream(final int length, final @Nullable boolean hasNext[] = { true }) = LongStream.generate(() -> readNBits(length, hasNext)).takeWhile(_ -> hasNext[0]);
        
    }
    
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Output {
        
        final OutputStream out;
        
        long cache;
        
        int cachedLength;
        
        public void writeNBits(final long bits, final int length) throws IOException {
            if (length < 0 || length > MAX_SIZE)
                throw new IllegalArgumentException(STR."length: \{length}");
            final int offset = Math.min(MAX_SIZE - cachedLength, length), overflow = length - offset;
            cache = cache << offset | bits & MASKS[offset];
            if ((cachedLength += offset) == MAX_SIZE) {
                out.write((int) (cache >>> 8 * 7));
                out.write((int) (cache >>> 8 * 6));
                out.write((int) (cache >>> 8 * 5));
                out.write((int) (cache >>> 8 * 4));
                out.write((int) (cache >>> 8 * 3));
                out.write((int) (cache >>> 8 * 2));
                out.write((int) (cache >>> 8 * 1));
                out.write((int) (cache >>> 8 * 0));
                if (overflow > 0) {
                    cache = bits >>> offset & MASKS[overflow];
                    cachedLength = overflow;
                } else {
                    cache = 0L;
                    cachedLength = 0;
                }
            }
        }
        
        public void flush() throws IOException {
            if (cachedLength != 0) {
                final int length = cachedLength + Byte.SIZE - 1 >>> 3;
                cache <<= Byte.SIZE - cachedLength & 0b111 & ~Byte.SIZE;
                for (int i = 0; i < length; i++)
                    out.write((int) (cache >>> 8 * (length - i - 1)));
                cache = 0L;
                cachedLength = 0;
            }
        }
        
        @SneakyThrows
        public LongConsumer consumer(final int length) = bits -> writeNBits(bits, length);
        
    }
    
    public static Input bitAccess(final InputStream in) = { in };
    
    public static Output bitAccess(final OutputStream out) = { out };
    
    public static void writeNBits(final MemorySegment segment, final long offset, final int index, final long bits, final int length) {
        if (index < 0 || index > Byte.SIZE)
            throw new IllegalArgumentException(STR."index: \{index}");
        if (length < 0 || length > MAX_SIZE)
            throw new IllegalArgumentException(STR."length: \{length}");
        final int begin = index > 0 ? Byte.SIZE - index : 0, middle = length - begin >>> 3, end = length - begin & 0b111;
        if (begin > 0)
            segment.set(JAVA_BYTE, offset, mergeByte(segment.get(JAVA_BYTE, offset), (byte) (bits >>> length - begin & MASKS[begin]), index));
        int i = begin > 0 ? 1 : 0;
        for (final int max = begin > 0 ? middle + 1 : middle; i < max; i++)
            segment.set(JAVA_BYTE, offset + i, (byte) (bits >>> 8 * (middle - i) + end));
        if (end > 0)
            segment.set(JAVA_BYTE, offset + i, mergeByte((byte) (bits & MASKS[end]), segment.get(JAVA_BYTE, offset + i), end));
    }
    
    public static long readNBits(final MemorySegment segment, final long offset, final int index, final int length) {
        if (index < 0 || index > Byte.SIZE)
            throw new IllegalArgumentException(STR."index: \{index}");
        if (length < 0 || length > MAX_SIZE)
            throw new IllegalArgumentException(STR."length: \{length}");
        long result = 0L;
        final int begin = index > 0 ? Byte.SIZE - index : 0, middle = length - begin >>> 3, end = length - begin & 0b111;
        if (begin > 0)
            result = (segment.get(JAVA_BYTE, offset) & MASKS[begin]) << length - begin;
        int i = begin > 0 ? 1 : 0;
        for (final int max = begin > 0 ? middle + 1 : middle; i < max; i++)
            result |= ((long) segment.get(JAVA_BYTE, offset + i) & 0xFF) << 8 * (middle - i) + end;
        if (end > 0)
            result |= segment.get(JAVA_BYTE, offset + i) >>> Byte.SIZE - end;
        return result;
    }
    
    public static byte mergeByte(final byte a, final byte b, final int offset) = (byte) ((a & MASKS[offset]) << Byte.SIZE - offset | b & MASKS[Byte.SIZE - offset]);
    
}
