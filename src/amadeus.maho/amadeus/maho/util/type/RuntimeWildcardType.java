package amadeus.maho.util.type;

import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.util.runtime.ArrayHelper;

@EqualsAndHashCode
public record RuntimeWildcardType(Type upperBounds[], Type lowerBounds[]) implements WildcardType {
    
    @Override
    public Type[] getUpperBounds() = upperBounds.clone();
    
    @Override
    public Type[] getLowerBounds() = lowerBounds.clone();
    
    @Override
    public String toString() {
        if (lowerBounds.length == 0) {
            if (upperBounds.length == 0 || upperBounds[0] == Object.class)
                return "?";
            return STR."? extends \{toString(upperBounds)}";
        }
        return STR."? super \{toString(lowerBounds)}";
    }
    
    private String toString(final Type bounds[]) = Stream.of(bounds).map(type -> type instanceof Class<?> clazz ? clazz.getName() : type.toString()).collect(Collectors.joining(" & "));
    
    @Override
    public boolean equals(final Object obj) = obj instanceof WildcardType wildcardType && (wildcardType instanceof RuntimeWildcardType impl ?
            ArrayHelper.equals(lowerBounds, impl.lowerBounds) && ArrayHelper.equals(upperBounds, impl.upperBounds) :
            ArrayHelper.equals(lowerBounds, wildcardType.getLowerBounds()) && ArrayHelper.equals(upperBounds, wildcardType.getUpperBounds()));
    
    @Override
    public int hashCode() = ArrayHelper.hashCode(lowerBounds) ^ ArrayHelper.hashCode(upperBounds);
    
}
