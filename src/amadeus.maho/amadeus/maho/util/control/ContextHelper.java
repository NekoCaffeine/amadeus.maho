package amadeus.maho.util.control;

import amadeus.maho.lang.inspection.Nullable;

public interface ContextHelper {
    
    static AutoCloseable rollbackThreadContextClassLoader(final @Nullable ClassLoader loader) {
        final Thread context = Thread.currentThread();
        final @Nullable ClassLoader rollback = context.getContextClassLoader();
        context.setContextClassLoader(loader);
        return () -> context.setContextClassLoader(rollback);
    }
    
}
