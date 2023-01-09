package amadeus.maho.util.serialization;

import java.nio.ByteBuffer;

import amadeus.maho.util.runtime.UnsafeHelper;

public interface Serializer<T> {
    
    interface Context {
        
        <T> Serializer<T> lookupSerializer(Class<T> type);
        
        default <T> T instantiation(final Class<T> type) = UnsafeHelper.allocateInstanceOfType(type);
        
    }
    
    T deserialization(Context context, ByteBuffer buffer, T instance);
    
    void serialization(Context context, ByteBuffer buffer, T instance);
    
}
