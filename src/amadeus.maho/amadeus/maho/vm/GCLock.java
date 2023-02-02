package amadeus.maho.vm;

import java.util.function.Supplier;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.vm.tools.hotspot.WhiteBox;

public interface GCLock {
    
    static void lockCritical() = WhiteBox.instance().lockCritical();
    
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
