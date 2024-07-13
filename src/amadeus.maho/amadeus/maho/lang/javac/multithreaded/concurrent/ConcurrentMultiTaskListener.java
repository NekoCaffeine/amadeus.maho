package amadeus.maho.lang.javac.multithreaded.concurrent;

import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.MultiTaskListener;

import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.javac.multithreaded.ThreadSafeTaskListener;

@NoArgsConstructor
public class ConcurrentMultiTaskListener extends MultiTaskListener {
    
    @Override
    public void add(final TaskListener listener) = super.add(ThreadSafeTaskListener.safely(listener));
    
}
