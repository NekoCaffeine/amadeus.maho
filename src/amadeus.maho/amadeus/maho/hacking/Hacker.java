package amadeus.maho.hacking;

public interface Hacker {
    
    void irrupt();
    
    void recovery();
    
    boolean working();
    
    default boolean available() = true;
    
    default void requiredAvailable() {
        if (!available())
            throw new UnsupportedOperationException("Hacker: " + getClass().getName());
    }
    
}
