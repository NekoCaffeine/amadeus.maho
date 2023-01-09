package amadeus.maho.util.reference;

import amadeus.maho.lang.inspection.Nullable;

@FunctionalInterface
public interface Observer<T> {
    
    @FunctionalInterface
    interface Byte {
        
        void onChanged(Observable.Byte observable, byte source, byte value);
        
    }
    
    @FunctionalInterface
    interface Short {
        
        void onChanged(Observable.Short observable, short source, short value);
        
    }
    
    @FunctionalInterface
    interface Int {
        
        void onChanged(Observable.Int observable, int source, int value);
        
    }
    
    @FunctionalInterface
    interface Long {
        
        void onChanged(Observable.Long observable, long source, long value);
        
    }
    
    @FunctionalInterface
    interface Float {
        
        void onChanged(Observable.Float observable, float source, float value);
        
    }
    
    @FunctionalInterface
    interface Double {
        
        void onChanged(Observable.Double observable, double source, double value);
        
    }
    
    @FunctionalInterface
    interface Boolean {
        
        void onChanged(Observable.Boolean observable, boolean source, boolean value);
        
    }
    
    @FunctionalInterface
    interface Char {
        
        void onChanged(Observable.Char observable, char source, char value);
        
    }
    
    @FunctionalInterface
    interface $Any {
        
        void onChanged(Observable.$Any observable, Any source, Any value);
        
    }
    
    void onChanged(Observable<T> observable, @Nullable T source, @Nullable T value);
    
}
