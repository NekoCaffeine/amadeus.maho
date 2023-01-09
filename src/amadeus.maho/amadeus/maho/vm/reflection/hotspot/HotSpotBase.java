package amadeus.maho.vm.reflection.hotspot;

import jdk.internal.misc.Unsafe;

import amadeus.maho.util.runtime.UnsafeHelper;

public interface HotSpotBase {
    
    HotSpot jvm = HotSpot.instance();
    
    Unsafe unsafe = UnsafeHelper.unsafe();
    
}
