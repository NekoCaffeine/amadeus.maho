package amadeus.maho.util.concurrent;

import java.util.Queue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.function.Supplier;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TaskQueue {
    
    DoubleBuffering<Queue<Runnable>> buffering;
    
    public TaskQueue(final Supplier<? extends Queue<Runnable>> supplier) = buffering = { supplier };
    
    public void add(final Runnable task) = buffering.fast(queue -> queue += task);
    
    public boolean offer(final Runnable task) = new boolean[]{ false }.let(it -> buffering.fast(queue -> it[0] = queue.offer(task)))[0];
    
    public void work() = buffering.slow(queue -> {
        if (!queue.isEmpty()) {
            queue.forEach(Runnable::run);
            queue.clear();
        }
    });
    
    public static TaskQueue of() = { LinkedTransferQueue::new };
    
}
