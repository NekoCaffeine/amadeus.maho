package amadeus.maho.util.reference;

public interface Puppet<T> extends Overwritable<T>, Observable<T> {
    
    interface Byte extends Overwritable.Byte, Observable.Byte { }
    
    interface Short extends Overwritable.Short, Observable.Short { }
    
    interface Int extends Overwritable.Int, Observable.Int { }
    
    interface Long extends Overwritable.Long, Observable.Long { }
    
    interface Float extends Overwritable.Float, Observable.Float { }
    
    interface Double extends Overwritable.Double, Observable.Double { }
    
    interface Boolean extends Overwritable.Boolean, Observable.Boolean { }
    
    interface Char extends Overwritable.Char, Observable.Char { }
    
}
