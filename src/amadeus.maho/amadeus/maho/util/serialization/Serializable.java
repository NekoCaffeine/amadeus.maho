package amadeus.maho.util.serialization;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;

public interface Serializable {
    
    @Getter
    @RequiredArgsConstructor
    class Output extends OutputStream implements BinaryMapper.OffsetMark {
        
        @Getter
        @RequiredArgsConstructor
        @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
        public static class Limited extends Output {
            
            long limit;
            
            @Override
            public void write(final int b) throws IOException {
                if (offset >= limit)
                    throw new OverflowException();
                super.write(b);
            }
            
            @Override
            public void write(final byte buffer[], final int at, final int length) throws IOException {
                if (length > limit - offset)
                    throw new OverflowException();
                super.write(buffer, at, length);
            }
            
        }
        
        private final OutputStream output;
        
        @Default
        protected long offset = 0;
        
        @Override
        public void write(final int b) throws IOException {
            output().write(b);
            offset++;
        }
        
        @Override
        public void write(final byte buffer[], final int at, final int length) throws IOException {
            output().write(buffer, at, length);
            offset += length;
        }
        
        public void writeByte(final byte value) throws IOException = write(value);
        
        public void writeBoolean(final boolean value) throws IOException = write(value ? 1 : 0);
        
        public void writeShortLittleEndian(final short value) throws IOException {
            write(value >>> 0);
            write(value >>> 8);
        }
        
        public void writeShortBigEndian(final short value) throws IOException {
            write(value >>> 8);
            write(value >>> 0);
        }
        
        public void writeCharLittleEndian(final char value) throws IOException = writeShortLittleEndian((short) value);
        
        public void writeCharBigEndian(final char value) throws IOException = writeShortBigEndian((short) value);
        
        public void writeIntLittleEndian(final int value) throws IOException {
            write(value >>> 0);
            write(value >>> 8);
            write(value >>> 16);
            write(value >>> 24);
        }
        
        public void writeIntBigEndian(final int value) throws IOException {
            write(value >>> 24);
            write(value >>> 16);
            write(value >>> 8);
            write(value >>> 0);
        }
        
        public void writeLongLittleEndian(final long value) throws IOException {
            write((int) (value >>> 0));
            write((int) (value >>> 8));
            write((int) (value >>> 16));
            write((int) (value >>> 24));
            write((int) (value >>> 32));
            write((int) (value >>> 40));
            write((int) (value >>> 48));
            write((int) (value >>> 56));
        }
        
        public void writeLongBigEndian(final long value) throws IOException {
            write((int) (value >>> 56));
            write((int) (value >>> 48));
            write((int) (value >>> 40));
            write((int) (value >>> 32));
            write((int) (value >>> 24));
            write((int) (value >>> 16));
            write((int) (value >>> 8));
            write((int) (value >>> 0));
        }
        
        public void writeFloatLittleEndian(final float value) throws IOException = writeIntLittleEndian(Float.floatToRawIntBits(value));
        
        public void writeFloatBigEndian(final float value) throws IOException = writeIntBigEndian(Float.floatToRawIntBits(value));
        
        public void writeDoubleLittleEndian(final double value) throws IOException = writeLongLittleEndian(Double.doubleToRawLongBits(value));
        
        public void writeDoubleBigEndian(final double value) throws IOException = writeLongBigEndian(Double.doubleToRawLongBits(value));
        
        public void writeVarInt(final int value) throws IOException {
            int b = value;
            while ((b & 0xFFFFFF80) != 0) {
                write(b & 0x7F | 0x80);
                b >>>= 7;
            }
            write(b & 0x7F);
        }
        
        public void writeVarLong(final long value) throws IOException {
            long b = value;
            while ((b & 0xFFFFFFFFFFFFFF80L) != 0) {
                write((int) (b & 0x7F | 0x80));
                b >>>= 7;
            }
            write((int) (b & 0x7F));
        }
        
        public void writeUTF(final String value) throws IOException {
            final byte buffer[] = value.getBytes(StandardCharsets.UTF_8);
            writeVarInt(buffer.length);
            write(buffer);
        }
        
        @Override
        public void flush() throws IOException = output().flush();
        
        @Override
        public void close() throws IOException = output().close();
        
        public Limited limit(final long limit) = { output, limit };
        
    }
    
    self serialization(Output output) throws IOException;
    
}
