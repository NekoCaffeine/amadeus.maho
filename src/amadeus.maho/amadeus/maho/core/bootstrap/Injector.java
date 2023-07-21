package amadeus.maho.core.bootstrap;

import java.lang.instrument.ClassFileTransformer;

public interface Injector extends ClassFileTransformer {
    
    String className();
    
    String target();
    
}
