package amadeus.maho.util.function;

import amadeus.maho.util.type.TypeInferer;
import amadeus.maho.util.type.TypeToken;

public interface FunctionR<R> {
    
    default Class<?> returnType() = TypeInferer.infer(TypeToken.<R>capture(), getClass()).erasedType();
    
}
