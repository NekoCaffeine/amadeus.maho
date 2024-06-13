package amadeus.maho.vm;

import java.lang.management.ManagementFactory;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import amadeus.maho.core.MahoExport;
import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.annotation.mark.IndirectCaller;
import amadeus.maho.util.logging.LogLevel;

public interface JDWP {
    
    sealed interface IDECommand {
    
        record Notification(Type type, String title, String content) implements IDECommand {
            
            @RequiredArgsConstructor(AccessLevel.PRIVATE)
            @FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
            public enum Type {
                
                INFORMATION(LogLevel.INFO),
                WARNING(LogLevel.WARNING),
                ERROR(LogLevel.ERROR);
                
                LogLevel level;
                
            }
            
            public String asString() = STR."\{title}: \{content}";
            
        }
    
    }
    
    interface MessageQueue {
        
        @SuppressWarnings("Assign")
        static void send(final IDECommand command) {
            breakpoint(); // set breakpoint here to enable message send
        }
        
        @IndirectCaller
        static void notify(final IDECommand.Notification notification, final BiConsumer<LogLevel, String> logger = MahoExport.namedLogger()) {
            logger[notification.type.level] = notification.asString();
            send(notification);
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
