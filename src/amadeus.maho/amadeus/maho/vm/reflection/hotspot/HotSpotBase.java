package amadeus.maho.vm.reflection.hotspot;

import jdk.internal.misc.Unsafe;

import amadeus.maho.util.runtime.UnsafeHelper;

public interface HotSpotBase {
    
    Unsafe unsafe = UnsafeHelper.unsafe();
    
    HotSpot jvm = HotSpot.instance();
    
}
