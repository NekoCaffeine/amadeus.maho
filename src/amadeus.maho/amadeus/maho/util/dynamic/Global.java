package amadeus.maho.util.dynamic;

import java.util.concurrent.ConcurrentHashMap;

import amadeus.maho.lang.Getter;

public interface Global {

    @Getter
    ConcurrentHashMap<Object, Object> mapping = { };
    
}
