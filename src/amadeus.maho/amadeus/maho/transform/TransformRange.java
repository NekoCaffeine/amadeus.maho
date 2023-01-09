package amadeus.maho.transform;

import amadeus.maho.lang.inspection.Nullable;

public interface TransformRange {
    
    default boolean isTarget(final @Nullable ClassLoader loader, final String name) = false;
    
    default boolean isTarget(final Class<?> clazz) = isTarget(clazz.getClassLoader(), clazz.getName());
    
}
