package amadeus.maho.agent;

import java.lang.instrument.Instrumentation;

// use Maho
public class LiveInjector {
    
    public static /*@Nullable*/ String agentArgs;
    
    public static /*@Nullable*/ Instrumentation instrumentation;
    
    public static void agentmain(final String agentArgs, final Instrumentation instrumentation) {
        LiveInjector.agentArgs = agentArgs;
        LiveInjector.instrumentation = instrumentation;
    }
    
}
