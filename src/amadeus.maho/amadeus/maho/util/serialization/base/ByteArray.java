package amadeus.maho.util.serialization.base;

import java.io.IOException;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.util.serialization.BinaryMapper;

@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PUBLIC)
public class ByteArray implements BinaryMapper {
    
    public static final byte EMPTY[] = new byte[0];
    
    int length;
    
    @Default
    byte value[] = new byte[length];
    
    public ByteArray(final byte value[] = EMPTY) = this(value.length, value);
    
    @Override
    public void write(final Output output) throws IOException {
        output.writeVarInt(length);
        output.write(value, 0, length);
    }
    
    @Override
    public void read(final Input input) throws IOException {
        length = input.readVarInt();
        value = new byte[length];
        input.readFully(value);
    }
    
}
