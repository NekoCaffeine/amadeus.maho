package amadeus.maho.util.type;

import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import amadeus.maho.lang.EqualsAndHashCode;

@EqualsAndHashCode
public record InferredWildcardType(WildcardType sourceType, Type upperBounds[], Type lowerBounds[]) implements InferredGenericType, WildcardType {
    
    @Override
    public Type[] getUpperBounds() = upperBounds.clone();
    
    @Override
    public Type[] getLowerBounds() = lowerBounds.clone();
    
    @Override
    public String toString() {
        if (lowerBounds.length == 0) {
            if (upperBounds.length == 0 || upperBounds[0] == Object.class)
                return "?";
            return "? extends " + toString(upperBounds);
        }
        return "? super " + toString(lowerBounds);
    }
    
    private String toString(final Type bounds[]) = Stream.of(bounds).map(type -> type instanceof Class<?> clazz ? clazz.getName() : type.toString()).collect(Collectors.joining(" & "));
    
}
