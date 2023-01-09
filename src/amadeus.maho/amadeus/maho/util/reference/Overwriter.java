package amadeus.maho.util.reference;

import amadeus.maho.lang.inspection.Nullable;

@FunctionalInterface
public interface Overwriter<T> {
    
    interface Replace { }
    
    @FunctionalInterface
    interface Byte {
        
        byte overwrite(byte value);
        
    }
    
    @FunctionalInterface
    interface Short {
        
        short overwrite(short value);
        
    }
    
    @FunctionalInterface
    interface Int {
        
        int overwrite(int value);
        
    }
    
    @FunctionalInterface
    interface Long {
        
        long overwrite(long value);
        
    }
    
    @FunctionalInterface
    interface Float {
        
        float overwrite(float value);
        
    }
    
    @FunctionalInterface
    interface Double {
        
        double overwrite(double value);
        
    }
    
    @FunctionalInterface
    interface Boolean {
        
        boolean overwrite(boolean value);
        
    }
    
    @FunctionalInterface
    interface Char {
        
        char overwrite(char value);
        
    }
    
    @FunctionalInterface
    interface $Any {
        
        Any overwrite(Any value);
        
    }
    
    @Nullable T overwrite(final @Nullable T value);
    
}
