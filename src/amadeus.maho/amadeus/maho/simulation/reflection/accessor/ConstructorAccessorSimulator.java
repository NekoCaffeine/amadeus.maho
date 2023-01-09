package amadeus.maho.simulation.reflection.accessor;

import java.lang.invoke.MethodHandle;

import jdk.internal.reflect.ConstructorAccessor;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.SneakyThrows;

@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
public class ConstructorAccessorSimulator implements ConstructorAccessor {
    
    MethodHandle handle;
    
    @Override
    @SneakyThrows
    public Object newInstance(final Object[] objects) throws IllegalArgumentException = handle.invokeWithArguments(objects);
    
}
