package amadeus.maho.vm;

import java.util.function.Supplier;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.vm.tools.hotspot.WhiteBox;
import amadeus.maho.vm.transform.mark.HotSpotJIT;
import amadeus.maho.vm.transform.mark.HotSpotMethodFlags;

import static amadeus.maho.vm.reflection.hotspot.KlassMethod.Flags._force_inline;

@HotSpotJIT
public interface GCLock {
    
    @HotSpotMethodFlags(_force_inline)
    static void lockCritical() = WhiteBox.instance().lockCritical();
    
    @HotSpotMethodFlags(_force_inline)
    static void unlockCritical() = WhiteBox.instance().unlockCritical();
    
    static void runCritical(final Runnable runnable) {
        try {
            lockCritical();
            runnable.run();
        } finally {
            unlockCritical();
        }
    }
    
    static <T> @Nullable T getCritical(final Supplier<T> supplier) {
        try {
            lockCritical();
            return supplier.get();
        } finally {
            unlockCritical();
        }
    }
    
}
