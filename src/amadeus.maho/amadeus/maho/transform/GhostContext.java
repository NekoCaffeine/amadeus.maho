package amadeus.maho.transform;

import amadeus.maho.util.annotation.mark.Ghost;

public interface GhostContext {
    
    @Ghost
    static IncompatibleClassChangeError touch() { throw new Ghost.TouchGhostError(); }
    
}
