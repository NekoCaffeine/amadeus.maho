package amadeus.maho.vm.reflection.hotspot;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.ToString;

import static amadeus.maho.vm.reflection.hotspot.HotSpotBase.*;

@ToString
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
public class KlassMethod {
    
    public static final HotSpotType
            Method       = jvm.type("Method");
    public static final long
            _constMethod = Method.offset("_constMethod");
    
    long address;
    
    ConstMethod constMethod = { unsafe.getAddress(address + _constMethod) };
    
}
