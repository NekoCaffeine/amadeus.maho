package amadeus.maho.util.function;

@FunctionalInterface
public interface Consumer2<T1, T2> {
    
    void accept(T1 v1, T2 v2);
    
}
