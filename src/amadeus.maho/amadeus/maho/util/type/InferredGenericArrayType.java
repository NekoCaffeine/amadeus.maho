package amadeus.maho.util.type;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;

import amadeus.maho.lang.EqualsAndHashCode;

@EqualsAndHashCode
public record InferredGenericArrayType(GenericArrayType sourceType, Type genericComponentType) implements InferredGenericType, GenericArrayType {
    
    @Override
    public Type getGenericComponentType() = genericComponentType;
    
    @Override
    public String toString() = getGenericComponentType().getTypeName() + "[]";
    
}
