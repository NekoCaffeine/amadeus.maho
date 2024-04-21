package amadeus.maho.util.serialization.base;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.IntFunction;
import java.util.zip.Deflater;
import java.util.zip.DeflaterInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.serialization.BinaryMapper;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PUBLIC)
public abstract class Compressible<V extends BinaryMapper> implements BinaryMapper {
    
    @NoArgsConstructor
    public static class Zlib<V extends BinaryMapper> extends Compressible<V> {
        
        protected static class InflaterStream extends InflaterInputStream {
            
            @Getter
            private static final ThreadLocal<InflaterStream> local = ThreadLocal.withInitial(InflaterStream::new);
            
            public InflaterStream() = super(new ByteArrayInputStream(new byte[0]), new Inflater(), 4096);
            
            public self in(final InputStream in) = this.in = in;
            
        }
        
        protected static class DeflaterStream extends DeflaterInputStream {
            
            @Getter
            private static final ThreadLocal<DeflaterStream> local = ThreadLocal.withInitial(DeflaterStream::new);
            
            public DeflaterStream() = super(new ByteArrayInputStream(new byte[0]), new Deflater(), 4096);
            
            public self in(final InputStream in) = this.in = in;
            
        }
        
        @Override
        protected byte[] compression(final byte buffer[]) throws IOException = InflaterStream.local().get().in(new ByteArrayInputStream(buffer)).readAllBytes();
        
        @Override
        protected byte[] decompression(final byte buffer[]) throws IOException = DeflaterStream.local().get().in(new ByteArrayInputStream(buffer)).readAllBytes();
        
    }
    
    @Default
    IntFunction<V> make;
    
    @Default
    byte compressedBuffer[] = null;
    
    @Default
    boolean compressed = true;
    
    @Nullable V value;
    
    byte buffer[];
    
    private @Nullable V cacheIdentity;
    
    @Override
    public void write(final Output output) throws IOException {
        if (compressed) {
            if (value != null && cacheIdentity != value) {
                final ByteArrayOutputStream temp = { };
                final Output next = { temp, output.offset() };
                value.serialization(next);
                compressedBuffer = compression(buffer = temp.toByteArray());
                cacheIdentity = value;
            }
            output.write(compressedBuffer);
        } else
            value.serialization(output);
    }
    
    @Override
    public void read(final Input input) throws IOException {
        if (compressed) {
            input.readFully(compressedBuffer);
            final Input next = { new ByteArrayInputStream(buffer = decompression(compressedBuffer)), input.offset() };
            (value = make.apply(buffer.length)).deserialization(next);
            cacheIdentity = value;
        } else
            (value = make.apply(compressedBuffer.length)).read(input);
    }
    
    protected abstract byte[] compression(byte buffer[]) throws IOException;
    
    protected abstract byte[] decompression(byte buffer[]) throws IOException;
    
    public int size() throws IOException = compressedBuffer.length;
    
}
