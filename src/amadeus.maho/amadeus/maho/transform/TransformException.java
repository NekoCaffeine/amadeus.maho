package amadeus.maho.transform;

import amadeus.maho.lang.NoArgsConstructor;

@NoArgsConstructor
public class TransformException extends RuntimeException {
    
    public static TransformException of(final Throwable throwable) = throwable instanceof TransformException exception ? exception : new TransformException(throwable);
    
}
