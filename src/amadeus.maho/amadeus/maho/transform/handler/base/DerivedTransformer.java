package amadeus.maho.transform.handler.base;

import java.util.stream.Stream;

import amadeus.maho.transform.ClassTransformer;

public interface DerivedTransformer {
    
    Stream<? extends ClassTransformer> derivedTransformers();
    
}
