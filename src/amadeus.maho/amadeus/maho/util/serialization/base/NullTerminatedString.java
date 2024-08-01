package amadeus.maho.util.serialization.base;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.IntFunction;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.runtime.ObjectHelper;
import amadeus.maho.util.serialization.BinaryMapper;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PUBLIC)
public class NullTerminatedString implements BinaryMapper {
    
    @Default
    final Charset charset = StandardCharsets.UTF_8;
    
    @Default
    final int maxLength = 256;
    
    @Default
    @Nullable String value = null;
    
    private @Nullable String cacheIdentity = null;
    
    private @Nullable byte cacheBuffer[] = null;
    
    @Override
    public void write(final Output output) throws IOException {
        assert value != null;
        ensureIdentity();
        // noinspection DataFlowIssue
        output.write(cacheBuffer, 0, Math.min(cacheBuffer.length, maxLength - 1));
        output.write('\0');
    }
    
    @Override
    public void read(final Input input) throws IOException {
        final ByteArrayOutputStream buffer = { Math.min(2 << 12, maxLength - 1) };
        int b = 0;
        int count = 0;
        while (maxLength > count++ && (b = input.read()) != 0) {
            if (b == -1)
                throw new EOFException();
            buffer.write(b);
        }
        if (b != 0)
            throw new IOException("Found non null terminated string.");
        value = cacheIdentity = { cacheBuffer = buffer.toByteArray(), charset };
    }
    
    protected void ensureIdentity() throws IOException {
        assert value != null;
        if (cacheIdentity != value) {
            cacheBuffer = value.getBytes(charset);
            assert cacheBuffer.length < maxLength;
            cacheIdentity = value;
        }
    }
    
    public int size() throws IOException {
        ensureIdentity();
        // noinspection DataFlowIssue
        return cacheBuffer.length + 1;
    }
    
    @Override
    public String toString() = ObjectHelper.toString(value);
    
    public static IntFunction<NullTerminatedString> withCharset(final Charset charset) = length -> new NullTerminatedString(charset, length);
    
}
