package amadeus.maho.vm.tools.hotspot;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.function.Predicate;
import java.util.stream.Stream;

import amadeus.maho.core.Maho;
import amadeus.maho.lang.Getter;
import amadeus.maho.util.concurrent.ConcurrentWeakIdentityHashMap;
import amadeus.maho.util.misc.Environment;
import amadeus.maho.vm.tools.hotspot.WhiteBox;

import static java.lang.Boolean.TRUE;

public enum JIT {
    
    @Getter
    instance;
    
    public interface Compiler {
        
        WhiteBox WB = WhiteBox.instance();
        
        static boolean isC2OrJVMCIIncluded() = WB.isC2OrJVMCIIncluded();
        
        static boolean isJITEnabled() = WB.getBooleanVMFlag("UseCompiler") == TRUE;
        
        static boolean isJVMCIEnabled() = WB.getBooleanVMFlag("EnableJVMCI") == TRUE;
        
        static boolean isTieredCompilationEnabled() = WB.getBooleanVMFlag("TieredCompilation") == TRUE;
        
        static int tieredStopAtLevel() = Math.toIntExact(WB.getIntxVMFlag("TieredStopAtLevel"));
        
        static boolean isProfileInterpreterEnabled() = WB.getBooleanVMFlag("ProfileInterpreter") == TRUE;
        
        static boolean isGraalEnabled() = isJITEnabled() && WB.getBooleanVMFlag("UseJVMCICompiler") == TRUE && !(isTieredCompilationEnabled() && tieredStopAtLevel() <= 3);
        
        static boolean isJVMCINativeEnabled() = isGraalEnabled() && WB.getBooleanVMFlag("UseJVMCINativeLibrary") == TRUE;
        
        static boolean isC2Enabled() = isJITEnabled() && isProfileInterpreterEnabled() && (isTieredCompilationEnabled() || tieredStopAtLevel() > 3) && !isGraalEnabled();
        
        static boolean isC1Enabled() = isJITEnabled() && (!isProfileInterpreterEnabled() || isTieredCompilationEnabled());
        
    }
    
    public enum Level {
        DONT, NONE, SIMPLE, LIMITED_PROFILE, FULL_PROFILE, FULL_OPTIMIZATION
    }
    
    @Getter
    private static final Level defautLevel = Level.valueOf(Environment.local().lookup("maho.jit.level.default", Level.FULL_OPTIMIZATION.name()));
    
    private volatile boolean isReady = false;
    
    private final ConcurrentWeakIdentityHashMap<Executable, Level> waitingQueue = { };
    
    public void compile(final Executable executable, final Level level = defautLevel()) {
        if (!isReady)
            synchronized (waitingQueue) {
                if (!isReady) {
                    waitingQueue[executable] = level;
                    return;
                }
            }
        if (level == Level.DONT)
            WhiteBox.instance().makeMethodNotCompilable(executable, 4);
        else if (WhiteBox.instance().getMethodCompilationLevel(executable) < level.ordinal() - 1 && WhiteBox.instance().isMethodCompilable(executable, level.ordinal()))
            WhiteBox.instance().enqueueMethodForCompilation(executable, level.ordinal() - 1);
    }
    
    public void compile(final Class<?> clazz, final Level level = defautLevel()) {
        for (final Method method : clazz.getDeclaredMethods())
            compile(method, level);
    }
    
    public void compileLoaded(final Predicate<? super Class> predicate, final Level level = defautLevel()) = Stream.of(Maho.instrumentation().getAllLoadedClasses()).filter(predicate).forEach(clazz -> compile(clazz, level));
    
    public void compileLoaded(final String prefix) = compileLoaded(clazz -> clazz.getName().startsWith(prefix));
    
    public void compileLoaded(final Package pkg) = compileLoaded(pkg.getName() + ".");
    
    public synchronized void ready() {
        synchronized (waitingQueue) {
            isReady = true;
            waitingQueue.forEach(this::compile);
            waitingQueue.clear();
        }
        Maho.debug("JITCompiler: Ready!");
    }
}
