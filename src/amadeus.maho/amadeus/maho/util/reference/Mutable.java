package amadeus.maho.util.reference;

import amadeus.maho.lang.Extension;

public interface Mutable<T> extends Readable<T>, Observer<T> {
    
    interface Byte extends Readable.Byte, Observer.Byte {
        
        @Extension.Operator("<<")
        void set(byte value);
        
    }
    
    interface Short extends Readable.Short, Observer.Short {
        
        @Extension.Operator("<<")
        void set(short value);
        
    }
    
    interface Int extends Readable.Int, Observer.Int {
        
        @Extension.Operator("<<")
        void set(int value);
        
    }
    
    interface Long extends Readable.Long, Observer.Long {
        
        @Extension.Operator("<<")
        void set(long value);
        
    }
    
    interface Float extends Readable.Float, Observer.Float {
        
        @Extension.Operator("<<")
        void set(float value);
        
    }
    
    interface Double extends Readable.Double, Observer.Double {
        
        @Extension.Operator("<<")
        void set(double value);
        
    }
    
    interface Boolean extends Readable.Boolean, Observer.Boolean {
        
        @Extension.Operator("<<")
        void set(boolean value);
        
    }
    
    interface Char extends Readable.Char, Observer.Char {
        
        @Extension.Operator("<<")
        void set(char value);
        
    }
    
    @Extension.Operator("<<")
    void set(T value);
    
}
