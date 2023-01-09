package amadeus.maho.util.runtime;

import java.io.InputStream;

import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;

import amadeus.maho.lang.Extension;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.Setter;

@Extension
public interface MemoryHelper {
    
    @RequiredArgsConstructor
    class Input extends InputStream {
        
        final MemorySegment segment;
        
        @Setter
        int mark;
        
        long offset = 0, length = segment.byteSize();
        
        @Override
        public int read() = offset < length ? MemoryAccess.getByteAtOffset(segment, offset++) : -1;
        
        @Override
        public int read(final byte buffer[], final int offset, final int length) {
            if (this.offset >= this.length)
                return -1;
            final long size = Math.min(this.length - this.offset, length);
            if (size <= 0)
                return 0;
            (offset == 0 ? MemorySegment.ofArray(buffer) : MemorySegment.ofArray(buffer).asSlice(offset)).copyFrom(segment.asSlice(offset, size));
            return size > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) size;
        }
        
        @Override
        public int read(final byte buffer[]) = read(buffer, 0, buffer.length);
        
        @Override
        public byte[] readAllBytes() {
            final long remaining = length - offset;
            if (remaining > Integer.MAX_VALUE)
                throw new UnsupportedOperationException("remaining > Integer.MAX_VALUE");
            final byte buffer[] = new byte[(int) remaining];
            read(buffer);
            return buffer;
        }
        
        @Override
        public int readNBytes(final byte[] buffer, final int offset, final int length) {
            final int n = read(buffer, offset, length);
            return n == -1 ? 0 : n;
        }
        
        @Override
        public long skip(final long n) {
            long remaining = length - offset;
            if (n < remaining)
                remaining = n < 0 ? 0 : n;
            offset += remaining;
            return remaining;
        }
        
        @Override
        public boolean markSupported() = true;
        
        @Override
        public void reset() = offset = mark;
        
        @Override
        public int available() {
            final long remaining = length - offset;
            return remaining > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) remaining;
        }
        
        @Override
        public void close() { }
        
    }
    
    static Input input(final MemorySegment segment) = { segment };
    
    static MemorySegment checkNativeTransfer(final MemorySegment segment, final boolean canFree = true, final boolean asyncFree = true) {
        final ResourceScope scope = segment.scope();
        if (!scope.isAlive())
            throw new IllegalStateException("Invalid memory segment!");
        if (scope.isImplicit())
            throw new IllegalStateException("The life cycle of a memory segment must be explicit!");
        if (canFree && scope == ResourceScope.globalScope())
            throw new IllegalStateException("The global memory segment cannot be freed!");
        if (asyncFree && scope.ownerThread() != null)
            throw new IllegalStateException("The memory statement cannot be owned by a thread!");
        return segment;
    }
    
    static MemorySegment LTLT(final MemorySegment dst, final MemorySegment src) {
        dst.copyFrom(src);
        return dst;
    }
    
}
