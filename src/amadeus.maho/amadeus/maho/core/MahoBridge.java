package amadeus.maho.core;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.NoArgsConstructor;

@NoArgsConstructor(AccessLevel.PRIVATE)
public final class MahoBridge {
    
    @SuppressWarnings("unused")
    private static ClassLoader bridgeClassLoader;
    
    public static ClassLoader bridgeClassLoader() = bridgeClassLoader;
    
}
