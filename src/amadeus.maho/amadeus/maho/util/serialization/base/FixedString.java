package amadeus.maho.util.serialization.base;

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
public class FixedString implements BinaryMapper {
    
    @Default
    final Charset charset = StandardCharsets.UTF_8;
    
    @Default
    int length;
    
    @Default
    String value = "";
    
    private @Nullable String cacheIdentity = null;
    
    private @Nullable byte cacheBuffer[] = null;
    
    @Override
    public void write(final Output output) throws IOException {
        assert length > 1;
        assert value != null;
        ensureIdentity();
        final int len = length - 1;
        // noinspection DataFlowIssue
        output.write(cacheBuffer, 0, Math.min(cacheBuffer.length, len));
        if (cacheBuffer.length < len)
            for (int i = cacheBuffer.length; i < len; i++)
                output.write('\0');
    }
    
    @Override
    public void read(final Input input) throws IOException {
        assert length > 1;
        cacheBuffer = new byte[length];
        input.readFully(cacheBuffer);
        value = cacheIdentity = { cacheBuffer, charset };
    }
    
    protected void ensureIdentity() throws IOException {
        if (cacheIdentity != value) {
            cacheBuffer = value.getBytes(charset);
            if (length == -1)
                length = cacheBuffer.length;
            cacheIdentity = value;
        }
    }
    
    public void updateLength() throws IOException {
        ensureIdentity();
        // noinspection DataFlowIssue
        length = cacheBuffer.length;
    }
    
    public int size() throws IOException {
        ensureIdentity();
        // noinspection DataFlowIssue
        return cacheBuffer.length;
    }
    
    @Override
    public String toString() = ObjectHelper.toString(value);
    
    public static IntFunction<FixedString> withCharset(final Charset charset) = length -> new FixedString(charset, length);
    
}
