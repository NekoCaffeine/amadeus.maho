package amadeus.maho.vm;

import java.lang.management.ManagementFactory;
import java.util.stream.Stream;

import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.inspection.Nullable;

public interface JDWP {
    
    sealed interface IDECommand {
    
        record Notification(Type type, String title, String content) implements IDECommand {
            
            enum Type {
                
                INFORMATION,
                WARNING,
                ERROR;
                
            }
            
        }
    
    }
    
    interface MessageQueue {
        
        static void send(final IDECommand command) {
            breakpoint(); // set breakpoint here to enable message send
        }
        
        private static void breakpoint() { }
        
    }
    
    // libjdwp jdk.jdwp.agent\share\native\libjdwp\transport.c#L639 : transport_startTransport
    /*
         // Start the transport loop in a separate thread
         (void)strcpy(threadName, "JDWP Transport Listener: ");
         (void)strcat(threadName, name);
     */
    static boolean isJDWPThread(final Thread thread = Thread.currentThread()) = thread.getName().startsWith("JDWP Transport Listener: ");
    
    static @Nullable Thread runningJDWPThread() = Stream.of((Privilege) Thread.getThreads()).filter(JDWP::isJDWPThread).findFirst().orElse(null);
    
    static boolean isJDWPEnable() = ManagementFactory.getRuntimeMXBean().getInputArguments().stream().anyMatch(argument -> argument.startsWith("-agentlib:jdwp"));
    
}
