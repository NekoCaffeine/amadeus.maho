package amadeus.maho.util.function;

@FunctionalInterface
public interface Function0<R> extends FunctionR<R> {
    
    R apply();
    
}
