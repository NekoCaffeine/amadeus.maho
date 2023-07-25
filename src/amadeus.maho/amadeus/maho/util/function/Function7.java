package amadeus.maho.util.function;

@FunctionalInterface
public interface Function7<T1, T2, T3, T4, T5, T6, T7, R> extends FunctionR<R> {
    
    R apply(T1 v1, T2 v2, T3 v3, T4 v4, T5 v5, T6 v6, T7 v7);
    
}
