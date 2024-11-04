package amadeus.maho.lang.javac.multithreaded;

import java.util.function.Function;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.Extension;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.util.runtime.DebugHelper;

@NoArgsConstructor
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PUBLIC)
public class CompileTaskProgress {
    
    public record Style(String left, String right, String block, String space) {
        
        public static final Style DEFAULT = { "[", "]", "=", " " };
        
    }
    
    private static final int STATES[] = { '|', '/', '-', '\\' };
    
    public static final Function<CompileTaskProgress, String> renderer = progress -> {
        final float percent = (float) progress.compiled / progress.total;
        final int step = Math.min(Math.max((int) (progress.granularity * percent), 0), progress.granularity);
        return progress.name +
               '\n' +
               ' ' +
               (char) STATES[progress.paused ? 0 : (int) ((System.currentTimeMillis() - progress.startTime) % 1000 / 250)] +
               ' ' +
               progress.style.left +
               progress.style.block.repeat(step) +
               progress.style.space.repeat(progress.granularity - step) +
               progress.style.right +
               ' ' +
               "%.2f%%".formatted(percent * 100) +
               ' ' +
               progress.compiled +
               '/' +
               progress.total;
    };
    
    @Default
    String name = "Compiling";
    
    Style style = Style.DEFAULT;
    
    int granularity = 50;
    
    @Default
    long total = 1L;
    
    volatile long compiled = 0L;
    
    long startTime = System.currentTimeMillis();
    
    boolean paused = false;
    
    { DebugHelper.breakpointWhen(total == 0); }
    
    public synchronized void update(final long value) = compiled = value;
    
    @Extension.Operator("_++")
    public synchronized void step() = compiled++;
    
}
