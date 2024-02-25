package amadeus.maho.lang;

import amadeus.maho.util.runtime.DebugHelper;

public interface CompileTimeConstants {
    
    interface Fallback {
        
        { DebugHelper.breakpoint(); }
        
        long timeMillis = System.currentTimeMillis();
        
    }
    
    static long compilingTimeMillis() = Fallback.timeMillis;
    
}
