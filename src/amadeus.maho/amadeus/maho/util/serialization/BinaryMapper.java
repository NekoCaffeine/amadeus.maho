package amadeus.maho.util.serialization;

import java.io.EOFException;
import java.io.IOException;

import amadeus.maho.lang.SneakyThrows;

public interface BinaryMapper extends Serializable, Deserializable {
    
    interface EOFMark {
        
        default boolean eofMark() = false;
        
    }
    
    interface OffsetMark {
        
        long offset();
        
    }
    
    // Spontaneous symmetry breaking! Only the part of interest is read, so it cannot be used for serialisation.
    interface Metadata extends Deserializable {
        
        default self read(final Input input) throws IOException = deserialization(input);
        
    }
    
    void write(Output output) throws IOException;
    
    void read(Input input) throws IOException;
    
    default self serialization(final Output output) throws IOException = write(output);
    
    default self deserialization(final Input input) throws IOException = read(input);
    
    @SneakyThrows
    static void eof() { throw new EOFException(); }
    
}
