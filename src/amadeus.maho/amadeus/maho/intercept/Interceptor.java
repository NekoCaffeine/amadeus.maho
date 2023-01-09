package amadeus.maho.intercept;

import java.lang.invoke.MethodType;

public interface Interceptor {
    
    default void enter(final Class<?> clazz, final String name, final MethodType methodType, final Object... args) { }
    
    default void exit() { }
    
}
