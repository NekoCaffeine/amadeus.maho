package amadeus.maho.util.dynamic;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ResourceCounter {
    
    volatile int count;
    
    final Runnable load, free;
    
    public synchronized void increment() {
        if (++count == 1)
            load.run();
    }
    
    public synchronized void decrement() {
        if (--count == 0)
            free.run();
    }
    
}
