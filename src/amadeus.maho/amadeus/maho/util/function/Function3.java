package amadeus.maho.util.function;

@FunctionalInterface
public interface Function3<T1, T2, T3, R> extends FunctionR<R> {
    
    R apply(T1 v1, T2 v2, T3 v3);
    
}
