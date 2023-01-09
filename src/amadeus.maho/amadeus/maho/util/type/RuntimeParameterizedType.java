package amadeus.maho.util.type;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.runtime.ArrayHelper;
import amadeus.maho.util.runtime.ObjectHelper;

import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;

public record RuntimeParameterizedType(Type actualTypeArguments[], Type rawType, @Nullable Type ownerType) implements ParameterizedType {
    
    @Override
    public Type[] getActualTypeArguments() = actualTypeArguments.clone();
    
    @Override
    public Type getRawType() = rawType;
    
    @Override
    public Type getOwnerType() = ownerType;
    
    @Override
    public String toString() {
        final Type ownerType = getOwnerType(), rawType = getRawType(), actualTypeArguments[] = getActualTypeArguments();
        final StringBuilder builder = { };
        if (ownerType != null) {
            if (ownerType instanceof Class<?> clazz)
                builder.append(clazz.getName());
            else
                builder.append(ownerType);
            builder.append(".");
            if (ownerType instanceof ParameterizedType parameterizedType)
                builder.append(rawType.getTypeName().replace(parameterizedType.getRawType().getTypeName() + "$", ""));
            else
                builder.append(rawType.getTypeName());
        } else
            builder.append(rawType.getTypeName());
        if (actualTypeArguments != null && actualTypeArguments.length > 0)
            builder.append(Stream.of(actualTypeArguments).map(type -> type == null ? "null" : type.getTypeName()).collect(Collectors.joining(", ", "<", ">")));
        return builder.toString();
    }
    
    @Override
    public boolean equals(final Object obj) = obj instanceof ParameterizedType parameterizedType &&
            ObjectHelper.equals(ownerType, parameterizedType.getOwnerType()) &&
            ObjectHelper.equals(rawType, parameterizedType.getRawType()) &&
            ArrayHelper.equals(actualTypeArguments,
                    parameterizedType instanceof ParameterizedTypeImpl impl ? (Privilege) impl.actualTypeArguments : // avoid clone
                    parameterizedType instanceof RuntimeParameterizedType impl ? impl.actualTypeArguments :
                    parameterizedType.getActualTypeArguments());
    
    @Override
    public int hashCode() = ArrayHelper.hashCode(actualTypeArguments) ^ rawType.hashCode() ^ ObjectHelper.hashCode(ownerType);
    
}
