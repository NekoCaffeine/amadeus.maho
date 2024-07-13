package amadeus.maho.profile;

import java.lang.invoke.MethodType;

public interface Profiler {
    
    default void enter(final Class<?> clazz, final String name, final MethodType methodType) { }
    
    default void exit() { }
    
}
