package amadeus.maho.util.type;

import java.lang.reflect.Type;

import amadeus.maho.util.runtime.TypeHelper;

public sealed interface InferredGenericType<T> extends Type permits InferredClassType, InferredGenericArrayType, InferredParameterizedType, InferredParameterizedType.Cached, InferredTypeVariable, InferredWildcardType {
    
    Type sourceType();
    
    default Class<T> erasedType() = (Class<T>) TypeHelper.erase(this);
    
}
