package amadeus.maho.lang.javac.multithreaded;

import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;

public interface ThreadSafeTaskListener extends TaskListener {
    
    record Synchronized(TaskListener listener) implements ThreadSafeTaskListener {
        
        @Override
        public synchronized void started(final TaskEvent event) = listener.started(event);
        
        @Override
        public synchronized void finished(final TaskEvent event) = listener.finished(event);
        
    }
    
    static TaskListener safely(final TaskListener listener) = listener instanceof ThreadSafeTaskListener ? listener : new Synchronized(listener);
    
}
