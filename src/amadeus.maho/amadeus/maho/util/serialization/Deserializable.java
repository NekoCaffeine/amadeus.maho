package amadeus.maho.util.serialization;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;

public interface Deserializable {
    
    @Getter
    @RequiredArgsConstructor
    class Input extends InputStream implements BinaryMapper.OffsetMark {
        
        @Getter
        @RequiredArgsConstructor
        @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
        public static class Limited extends Input {
            
            long limit;
            
            @Override
            public int read() throws IOException = offset >= limit ? -1 : super.read();
            
            @Override
            public int read(final byte buffer[], final int at, final int length) throws IOException = super.read(buffer, at, (int) Math.min(length, limit - offset));
            
            @Override
            public long skip(final long n) throws IOException = super.skip(Math.min(n, limit - offset));
            
            @Override
            public int available() throws IOException = (int) Math.min(super.available(), limit - offset);
            
        }
        
        private final InputStream input;
        
        @Default
        protected long offset = 0;
    
        @Override
        public int read() throws IOException {
            final int read = input().read();
            if (read != -1)
                offset++;
            return read;
        }
        
        public byte readByte() throws IOException {
            final int read = read();
            if (read == -1)
                throw new EOFException();
            return (byte) read;
        }
        
        public int readByteUnsigned() throws IOException {
            final int read = read();
            if (read == -1)
                throw new EOFException();
            return read;
        }
        
        @Override
        public int read(final byte buffer[], final int at, final int length) throws IOException {
            final int read = input().read(buffer, at, length);
            if (read != -1)
                offset += read;
            return read;
        }
        
        public boolean readBoolean() throws IOException = read() != 0;
        
        public short readShortLittleEndian() throws IOException {
            final int b0 = readByteUnsigned();
            final int b1 = readByteUnsigned();
            return (short) (b0 | b1 << 8);
        }
        
        public short readShortBigEndian() throws IOException {
            final int b0 = readByteUnsigned();
            final int b1 = readByteUnsigned();
            return (short) (b0 << 8 | b1);
        }
        
        public char readCharLittleEndian() throws IOException = (char) readShortLittleEndian();
        
        public char readCharBigEndian() throws IOException = (char) readShortBigEndian();
        
        public int readIntLittleEndian() throws IOException {
            final int b0 = readByteUnsigned();
            final int b1 = readByteUnsigned();
            final int b2 = readByteUnsigned();
            final int b3 = readByteUnsigned();
            return b3 << 24 | b2 << 16 | b1 << 8 | b0;
        }
        
        public int readIntBigEndian() throws IOException {
            final int b0 = readByteUnsigned();
            final int b1 = readByteUnsigned();
            final int b2 = readByteUnsigned();
            final int b3 = readByteUnsigned();
            return b0 << 24 | b1 << 16 | b2 << 8 | b3;
        }
        
        public long readLongLittleEndian() throws IOException {
            final long b0 = readByteUnsigned();
            final long b1 = readByteUnsigned();
            final long b2 = readByteUnsigned();
            final long b3 = readByteUnsigned();
            final long b4 = readByteUnsigned();
            final long b5 = readByteUnsigned();
            final long b6 = readByteUnsigned();
            final long b7 = readByteUnsigned();
            return b7 << 56 | b6 << 48 | b5 << 40 | b4 << 32 | b3 << 24 | b2 << 16 | b1 << 8 | b0;
        }
        
        public long readLongBigEndian() throws IOException {
            final long b0 = readByteUnsigned();
            final long b1 = readByteUnsigned();
            final long b2 = readByteUnsigned();
            final long b3 = readByteUnsigned();
            final long b4 = readByteUnsigned();
            final long b5 = readByteUnsigned();
            final long b6 = readByteUnsigned();
            final long b7 = readByteUnsigned();
            return b0 << 56 | b1 << 48 | b2 << 40 | b3 << 32 | b4 << 24 | b5 << 16 | b6 << 8 | b7;
        }
        
        public float readFloatLittleEndian() throws IOException = Float.intBitsToFloat(readIntLittleEndian());
        
        public float readFloatBigEndian() throws IOException = Float.intBitsToFloat(readIntBigEndian());
        
        public double readDoubleLittleEndian() throws IOException = Double.longBitsToDouble(readLongLittleEndian());
        
        public double readDoubleBigEndian() throws IOException = Double.longBitsToDouble(readLongBigEndian());
        
        public int readVarInt() throws IOException {
            int value = 0, shift = 0;
            int b;
            do {
                b = readByteUnsigned();
                value |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0);
            return value;
        }
        
        public long readVarLong() throws IOException {
            long value = 0, shift = 0;
            int b;
            do {
                b = readByteUnsigned();
                value |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0);
            return value;
        }
        
        public String readUTF() throws IOException {
            final int length = readVarInt();
            final byte buffer[] = new byte[length];
            readFully(buffer);
            return new String(buffer, StandardCharsets.UTF_8);
        }
    
        public final void readFully(final byte buffer[], final int at = 0, final int length = buffer.length - at) throws IOException {
            int n = 0;
            while (n < length) {
                final int count = read(buffer, at + n, length - n);
                if (count < 0)
                    throw new EOFException();
                n += count;
            }
        }
        
        public final byte[] readFully(final int length) throws IOException {
            final byte buffer[] = new byte[length];
            readFully(buffer);
            return buffer;
        }
        
        @Override
        public long skip(final long n) throws IOException {
            final long skip = input().skip(n);
            if (skip > 0)
                offset += skip;
            return skip;
        }
        
        @Override
        public int available() throws IOException = input().available();
        
        @Override
        public void close() throws IOException = input().close();
        
        @Override
        public void mark(final int limit) = input().mark(limit);
        
        @Override
        public void reset() throws IOException = input().reset();
        
        @Override
        public boolean markSupported() = input().markSupported();
        
        public Limited limit(final long limit) = { input, limit };
        
    }
    
    self deserialization(Input input) throws IOException;
    
}
