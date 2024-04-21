package amadeus.maho.util.serialization.base;

import java.io.IOException;
import java.io.InputStream;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.serialization.BinaryMapper;

@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PUBLIC)
public class UnknownLengthStream implements BinaryMapper {
    
    @Default
    int chunkSize = 8192;
    
    @Nullable
    InputStream stream;
    
    @Default
    @Nullable ByteBufferHandler handler = null; // symmetry broken
    
    public UnknownLengthStream(final ByteBufferHandler handler) = this(8192, null, handler);
    
    @Override
    public void write(final Output output) throws IOException {
        assert stream != null;
        output.writeIntLittleEndian(chunkSize);
        final byte buffer[] = new byte[chunkSize];
        int count;
        while ((count = stream.read(buffer)) != -1) {
            output.writeIntLittleEndian(count);
            output.write(buffer, 0, count);
        }
        output.writeIntLittleEndian(-1);
    }
    
    @Override
    public void read(final Input input) throws IOException {
        chunkSize = input.readIntLittleEndian();
        if (handler == null)
            handler = new TrustedByteArrayOutputStream(chunkSize);
        final byte buffer[] = new byte[chunkSize];
        int count;
        while ((count = input.readIntLittleEndian()) != -1) {
            input.readFully(buffer, 0, count);
            handler.handle(buffer, 0, count);
        }
        stream = handler instanceof TrustedByteArrayOutputStream output ? output.toInputStream() : null;
    }
    
}
