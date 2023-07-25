package amadeus.maho.util.function;

@FunctionalInterface
public interface Function1<T1, R> extends FunctionR<R> {
    
    R apply(T1 v1);
    
}
