package amadeus.maho.util.serialization;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import amadeus.maho.lang.Default;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;

public interface Deserializable {
    
    @Getter
    @RequiredArgsConstructor
    class Input extends InputStream implements BinaryMapper.OffsetMark {
        
        private final InputStream input;
        
        @Default
        protected long offset = 0;
    
        @Override
        public int read() throws IOException {
            final int read = input().read();
            if (read != -1)
                offset = offset + 1;
            return read;
        }
        
        @Override
        public int read(final byte buffer[], final int offset, final int length) throws IOException {
            assert Objects.checkFromIndexSize(offset, length, buffer.length) == offset;
            final int read = input().read(buffer, offset, length);
            if (read != -1)
                this.offset = this.offset + read;
            return read;
        }
    
        public final void readFully(final byte buffer[], final int offset = 0, final int length = buffer.length - offset) throws IOException {
            int n = 0;
            while (n < length) {
                final int count = read(buffer, offset + n, length - n);
                if (count < 0)
                    throw new EOFException();
                n += count;
            }
        }
        
        @Override
        public long skip(final long n) throws IOException {
            final long skip = input().skip(n);
            if (skip > 0)
                offset = offset + skip;
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
        
    }
    
    self deserialization(Input input) throws IOException;
    
}
