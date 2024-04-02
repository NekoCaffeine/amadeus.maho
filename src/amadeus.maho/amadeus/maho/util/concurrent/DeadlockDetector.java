package amadeus.maho.util.concurrent;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.control.Interrupt;
import amadeus.maho.util.dynamic.InvokeContext;
import amadeus.maho.util.runtime.DebugHelper;
import amadeus.maho.vm.tools.hotspot.DiagnosticCommander;

public interface DeadlockDetector {
    
    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    class WatcherThread extends Thread {
        
        AtomicLong counter = { };
        
        { setName("DeadlockDetectorWatcher"); }
        
        { setDaemon(true); }
        
        { start(); }
        
        @Override
        public void run() {
            // noinspection InfiniteLoopStatement
            while (true) {
                if (counter().get() > 0 && !detect().isEmpty()) {
                    DiagnosticCommander.Thread.print();
                    DebugHelper.breakpoint();
                }
                Interrupt.doInterruptible(() -> sleep(1000 * 10));
            }
        }
        
    }
    
    ThreadMXBean bean = ManagementFactory.getThreadMXBean();
    
    WatcherThread watcherThread = { };
    
    InvokeContext watcher = { watcherThread.counter() };
    
    static List<ThreadInfo> detect(final int maxDepth = Integer.MAX_VALUE) {
        final @Nullable long threadIds[] = bean.isThreadContentionMonitoringSupported() ? bean.findDeadlockedThreads() : bean.findMonitorDeadlockedThreads();
        return threadIds == null ? List.of() : List.of(bean.getThreadInfo(threadIds, bean.isObjectMonitorUsageSupported(), bean.isSynchronizerUsageSupported(), maxDepth));
    }
    
}
