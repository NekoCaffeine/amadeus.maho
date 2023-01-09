package amadeus.maho.util.reference;

import java.util.List;

public interface Observable<T> extends Mutable<T> {
    
    interface Byte extends Mutable.Byte {
        
        List<Observer.Byte> observers();
        
    }
    
    interface Short extends Mutable.Short {
        
        List<Observer.Short> observers();
        
    }
    
    interface Int extends Mutable.Int {
        
        List<Observer.Int> observers();
        
    }
    
    interface Long extends Mutable.Long {
        
        List<Observer.Long> observers();
        
    }
    
    interface Float extends Mutable.Float {
        
        List<Observer.Float> observers();
        
    }
    
    interface Double extends Mutable.Double {
        
        List<Observer.Double> observers();
        
    }
    
    interface Boolean extends Mutable.Boolean {
        
        List<Observer.Boolean> observers();
        
    }
    
    interface Char extends Mutable.Char {
        
        List<Observer.Char> observers();
        
    }
    
    interface $Any { }
    
    interface Template extends $Any {
        
        List<Observer.$Any> observers();
        
        default void notifyObservers(final Any source, final Any value) = observers().forEach(observer -> observer.onChanged(this, source, value));
        
    }
    
    List<Observer<T>> observers();
    
}
