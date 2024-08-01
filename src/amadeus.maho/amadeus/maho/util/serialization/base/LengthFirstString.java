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
import amadeus.maho.util.serialization.Deserializable;
import amadeus.maho.util.serialization.Serializable;
import amadeus.maho.util.serialization.VarHelper;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PUBLIC)
public class LengthFirstString implements BinaryMapper {
    
    @Default
    final Charset charset = StandardCharsets.UTF_8;
    
    @Default
    String value = "";
    
    private @Nullable String cacheIdentity = null;
    
    private @Nullable byte cacheBuffer[] = null;
    
    public LengthFirstString(final String value) = this(StandardCharsets.UTF_8, value);
    
    @Override
    public void write(final Serializable.Output output) throws IOException {
        assert value != null;
        ensureIdentity();
        // noinspection DataFlowIssue
        output.writeVarInt(cacheBuffer.length);
        output.write(cacheBuffer);
    }
    
    @Override
    public void read(final Deserializable.Input input) throws IOException {
        final int length = input.readVarInt();
        cacheBuffer = new byte[length];
        input.readFully(cacheBuffer);
        value = cacheIdentity = { cacheBuffer, charset };
    }
    
    protected void ensureIdentity() throws IOException {
        if (cacheIdentity != value) {
            cacheBuffer = value.getBytes(charset);
            cacheIdentity = value;
        }
    }
    
    public int size() throws IOException {
        ensureIdentity();
        // noinspection DataFlowIssue
        return cacheBuffer.length + VarHelper.varIntSize(cacheBuffer.length);
    }
    
    @Override
    public String toString() = ObjectHelper.toString(value);
    
    public static IntFunction<NullTerminatedString> withCharset(final Charset charset) = length -> new NullTerminatedString(charset, length);
    
}
