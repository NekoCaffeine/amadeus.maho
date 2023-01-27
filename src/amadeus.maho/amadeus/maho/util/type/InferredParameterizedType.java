package amadeus.maho.util.type;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import amadeus.maho.lang.EqualsAndHashCode;

@EqualsAndHashCode
public record InferredParameterizedType(ParameterizedType sourceType, InferredGenericType actualTypeArguments[]) implements InferredGenericType, ParameterizedType {
    
    public record Cached(ParameterizedType sourceType, InferredGenericType actualTypeArguments[], Map<TypeVariable<?>, Type> typeVariableMap) implements InferredGenericType, ParameterizedType {
        
        @Override
        public InferredGenericType[] getActualTypeArguments() = actualTypeArguments.clone();
        
        @Override
        public Type getRawType() = sourceType.getRawType();
        
        @Override
        public Type getOwnerType() = sourceType.getOwnerType();
        
        @Override
        public String toString() = InferredParameterizedType.toString(this);
    
    }
    
    public Cached cache() = { sourceType, actualTypeArguments, TypeInferer.typeVariableMap(this) };
    
    @Override
    public InferredGenericType[] getActualTypeArguments() = actualTypeArguments.clone();
    
    @Override
    public Type getRawType() = sourceType.getRawType();
    
    @Override
    public Type getOwnerType() = sourceType.getOwnerType();
    
    @Override
    public String toString() = toString(this);
    
    public static String toString(final ParameterizedType parameterizedType) {
        final Type ownerType = parameterizedType.getOwnerType(), rawType = parameterizedType.getRawType(), actualTypeArguments[] = parameterizedType.getActualTypeArguments();
        final StringBuilder builder = { };
        if (ownerType != null) {
            if (ownerType instanceof Class<?> clazz)
                builder.append(clazz.getName());
            else
                builder.append(ownerType);
            builder.append(".");
            if (ownerType instanceof ParameterizedType ownerParameterizedType)
                builder.append(rawType.getTypeName().replace(ownerParameterizedType.getRawType().getTypeName() + "$", ""));
            else
                builder.append(rawType.getTypeName());
        } else
            builder.append(rawType.getTypeName());
        if (actualTypeArguments != null && actualTypeArguments.length > 0)
            builder.append(Stream.of(actualTypeArguments).map(type -> type == null ? "null" : type.getTypeName()).collect(Collectors.joining(", ", "<", ">")));
        return builder.toString();
    }
    
}
