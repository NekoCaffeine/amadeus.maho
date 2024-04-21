package amadeus.maho.util.serialization.base;

@FunctionalInterface
public interface ByteBufferHandler {
    
    void handle(byte buffer[], int offset, int length);
    
    default void handle(final byte buffer[]) = handle(buffer, 0, buffer.length);
    
}
