package amadeus.maho.util.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.ToString;

@Event.Base
@ToString
public abstract class Event {
    
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Base { }
    
    @Base
    @Setter
    @Getter
    @ToString
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static abstract class Cancellable extends Event {
        
        boolean cancel = false;
        
        public boolean confirm() = !cancel();
    
    }
    
}
