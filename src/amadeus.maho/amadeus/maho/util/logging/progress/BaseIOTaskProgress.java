package amadeus.maho.util.logging.progress;

import java.util.function.Function;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.RequiredArgsConstructor;

@NoArgsConstructor
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PUBLIC)
public class BaseIOTaskProgress {
    
    public record Style(String left, String right, String block, String space) {
        
        public static final Style DEFAULT = { "[", "]", "=", " " };
        
    }
    
    private static final int STATES[] = { '|', '/', '-', '\\' };
    
    private static final int DECIMAL = 10, MASK = (1 << DECIMAL) - 1;
    
    private static final String UNIT[] = { "B", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "BB" };
    
    static String unitText(final long size) {
        if (size < 0L)
            return "0B/s";
        int layer = 0, surplus = 0;
        long top = size;
        while (top > 1 << DECIMAL) {
            surplus = (int) (top & MASK);
            top >>= DECIMAL;
            layer++;
        }
        return "%.3f %s".formatted(top + surplus / (float) (1 << DECIMAL), UNIT[layer]);
    }
    
    public static final Function<BaseIOTaskProgress, String> renderer = progress -> {
        final float percent = (float) progress.progress / progress.total;
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
               unitText(progress.progress) +
               '/' +
               unitText(progress.total) +
               ' ' +
               unitText(progress.bytesPerSecond) +
               '/' + 's';
    };
    
    @Default
    String name = "Progress";
    
    Style style = Style.DEFAULT;
    
    int granularity = 50;
    
    @Default
    long total = 1L;
    
    long progress = 0L, lastProgress = 0L;
    
    long startTime = System.currentTimeMillis(), lastTime = System.currentTimeMillis();
    
    long bytesPerSecond = 0L;
    
    boolean paused = false;
    
    public synchronized void update(final long value) {
        final long current = System.currentTimeMillis();
        if (current != lastTime) {
            bytesPerSecond = (value - lastProgress) / (current - lastTime) * 1000;
            lastProgress = value;
            lastTime = current;
        }
        progress = value;
    }
    
}
