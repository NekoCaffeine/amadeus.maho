package amadeus.maho.lang.javac.multithreaded.concurrent;

import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Options;

import amadeus.maho.lang.NoArgsConstructor;

@NoArgsConstructor
public class ConcurrentNames extends Names {
    
    @Override
    protected ConcurrentNameTable createTable(final Options options) = { this };
    
}
