package amadeus.maho.util.type;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;

public record RuntimeGenericArrayType(Type genericComponentType) implements GenericArrayType {
    
    @Override
    public Type getGenericComponentType() = genericComponentType;
    
    @Override
    public String toString() = getGenericComponentType().getTypeName() + "[]";
    
    @Override
    public boolean equals(final Object obj) = obj instanceof GenericArrayType genericArrayType && genericArrayType.equals(genericArrayType.getGenericComponentType());
    
    @Override
    public int hashCode() = genericComponentType.hashCode();
    
}
