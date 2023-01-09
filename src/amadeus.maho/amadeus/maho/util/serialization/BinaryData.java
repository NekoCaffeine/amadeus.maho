package amadeus.maho.util.serialization;

import java.io.EOFException;
import java.io.IOException;

import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.ResourceScope;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.reference.Reference;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE,makeFinal = true)
public class BinaryData implements BinaryMapper {
    
    @Nullable ResourceScope scope;
    
    @Nullable MemorySegment segment;
    
    int bufferSize;
    
    public BinaryData(final long size, final int bufferSize = 1 << 14) {
        assert size > -1;
        if (size < 1) {
            scope = null;
            segment = null;
            this.bufferSize = 0;
        } else {
            segment = MemorySegment.allocateNative(size, scope = ResourceScope.newSharedScope(Reference.Cleaner.instance()));
            this.bufferSize = size < bufferSize ? (int) size : bufferSize;
        }
    }
    
    @Override
    public void read(final Input input) throws IOException {
        if (segment != null) {
            final byte buffer[] = new byte[bufferSize];
            final MemorySegment bufferSegment = MemorySegment.ofArray(buffer);
            final long size = segment.byteSize();
            long remaining = size;
            do {
                final int length = input.read(buffer, 0, remaining > bufferSize ? bufferSize : (int) remaining);
                if (length == -1)
                    throw new EOFException();
                segment.asSlice(size - remaining, length).copyFrom(bufferSegment.asSlice(0, length));
                remaining -= length;
            } while (remaining > 0);
        }
    }
    
    @Override
    public void write(final Output output) throws IOException {
        if (segment != null) {
            final byte buffer[] = new byte[bufferSize];
            final MemorySegment bufferSegment = MemorySegment.ofArray(buffer);
            final long size = segment.byteSize();
            long remaining = size;
            do {
                final int length = remaining > bufferSize ? bufferSize : (int) remaining;
                bufferSegment.asSlice(0, length).copyFrom(segment.asSlice(size - remaining, length));
                output.write(buffer, 0, length);
                remaining -= bufferSize;
            } while (remaining > 0);
        }
    }
    
}
