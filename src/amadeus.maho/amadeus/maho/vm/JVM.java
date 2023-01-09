package amadeus.maho.vm;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.vm.reflection.hotspot.HotSpot;

public interface JVM {
    
    <T> T copyObjectWithoutHead(Class<T> type, Object target);
    
    <T> @Nullable T shadowClone(@Nullable T target);
    
    static JVM local() = HotSpot.instance();
    
}
