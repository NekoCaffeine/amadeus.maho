package amadeus.maho.util.bytecode.remap;

public interface RemapContext {
    
    default String lookupOwner(final String name) { throw new UnsupportedOperationException(); }
    
    default String lookupDescriptor(final String name) { throw new UnsupportedOperationException(); }
    
}
