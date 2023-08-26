package amadeus.maho.util.resource;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.runtime.UnsafeHelper;

public interface ClassLoadable {
    
    @Getter
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    class Loaded implements ClassLoadable {
        
        Class<?> loadedClass;
        
        @Override
        public Class<?> load(final boolean initialize, @Nullable final ClassLoader loader) {
            if (initialize)
                UnsafeHelper.unsafe().ensureClassInitialized(loadedClass);
            return loadedClass;
        }
        
    }
    
    Class<?> load(boolean initialize = false, @Nullable ClassLoader loader = null);
    
}
