package amadeus.maho.vm.tools.hotspot.jit;

import java.lang.reflect.Executable;

import amadeus.maho.core.Maho;
import amadeus.maho.lang.Getter;
import amadeus.maho.util.concurrent.ConcurrentWeakIdentityHashMap;
import amadeus.maho.vm.tools.hotspot.WhiteBox;

public enum JITCompiler {
    
    @Getter
    instance;
    
    public enum Level {
        DONT, NONE, SIMPLE, LIMITED_PROFILE, FULL_PROFILE, FULL_OPTIMIZATION
    }
    
    private volatile boolean isReady = false;
    
    private final ConcurrentWeakIdentityHashMap<Executable, Level> waitingQueue = { };
    
    public void compile(final Executable executable, final Level level) {
        if (!isReady)
            synchronized (waitingQueue) {
                if (!isReady) {
                    waitingQueue.put(executable, level);
                    return;
                }
            }
        if (level == Level.DONT)
            WhiteBox.instance().makeMethodNotCompilable(executable, 4);
            // WhiteBox.instance().makeMethodNotCompilable(executable);
        else if (WhiteBox.instance().getMethodCompilationLevel(executable) < level.ordinal() - 1 && WhiteBox.instance().isMethodCompilable(executable, level.ordinal()))
            WhiteBox.instance().enqueueMethodForCompilation(executable, level.ordinal() - 1);
    }
    
    public synchronized void ready() {
        synchronized (waitingQueue) {
            isReady = true;
            waitingQueue.forEach(this::compile);
            waitingQueue.clear();
        }
        Maho.debug("JITCompiler: Ready!");
    }
    
}
