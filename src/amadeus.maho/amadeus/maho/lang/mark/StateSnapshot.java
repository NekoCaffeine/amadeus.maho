package amadeus.maho.lang.mark;

// Used to mark this as a reversible AutoCloseable, currently used for unused var checking.
@FunctionalInterface
public interface StateSnapshot extends AutoCloseable {
    
    @Override
    void close();
    
}
