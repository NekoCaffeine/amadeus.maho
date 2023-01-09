package amadeus.maho.util.type;

public record InferredClassType(Class<?> sourceType) implements InferredGenericType {
    
    @Override
    public String toString() = sourceType.getName();
    
    @Override
    public Class erasedType() = sourceType;
    
}
