package amadeus.maho.util.logging.progress;

import java.io.Console;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.control.Interrupt;
import amadeus.maho.util.misc.Environment;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProgressBar<T> implements AutoCloseable {
    
    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class RenderThread extends Thread {
        
        AtomicBoolean shutdown = { false };
        
        CountDownLatch latch = { 1 };
        
        public RenderThread() {
            super("progressbar-renderer");
            setDaemon(true);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                assert console != null && progressBars.nonEmpty();
                shutdown.set(true);
                interrupt();
                Interrupt.doInterruptible(latch::await);
                console.writer().print(CLEAR);
                console.flush();
            }));
            start();
        }
        
        @Override
        public void run() {
            assert console != null;
            while (true)
                try {
                    if (progressBars.nonEmpty())
                        render();
                    sleep(updateInterval);
                } catch (final InterruptedException e) {
                    interrupted();
                    if (shutdown.get())
                        break;
                }
            latch.countDown();
        }
        
    }
    
    private static final String
            SAVE_CURSOR     = "\u001b[s",
            RESTORE_CURSOR  = "\u001b[u",
            CARRIAGE_RETURN = "\r",
            ERASE_DISPLAY   = "\u001b[0J",
            CLEAR           = ERASE_DISPLAY,
            PRINT           = STR."\{SAVE_CURSOR}\{CARRIAGE_RETURN}%s\{RESTORE_CURSOR}";
    
    private static final long updateInterval = Environment.local().lookup("amadeus.maho.progress.bar.update.interval", 50L);
    
    private static final @Nullable Console console = System.console();
    
    @Getter
    private static final boolean supported = Environment.local().lookup("amadeus.maho.progress.bar.enable", true) && console != null && (Privilege) Console.istty();
    
    private static final ReentrantLock renderLock = { };
    
    private static final @Nullable Thread renderThread = supported() ? new RenderThread() : null;
    
    private static final CopyOnWriteArrayList<ProgressBar<?>> progressBars = { };
    
    private static void add(final ProgressBar<?> progressBar) {
        if (!supported())
            return;
        renderLock.lock();
        try {
            progressBars << progressBar;
            updateRenderTask();
        } finally { renderLock.unlock(); }
    }
    
    private static void remove(final ProgressBar<?> progressBar) {
        if (!supported())
            return;
        renderLock.lock();
        try {
            progressBars -= progressBar;
            updateRenderTask();
        } finally { renderLock.unlock(); }
    }
    
    private static void updateRenderTask() {
        if (renderThread == null)
            return;
        assert console != null && renderLock.isHeldByCurrentThread();
        if (progressBars.isEmpty()) {
            console.writer().print(CLEAR);
            console.flush();
        } else
            renderThread.interrupt();
    }
    
    public static void render(final @Nullable Supplier<String> insert = null) {
        if (!supported())
            return;
        assert console != null;
        renderLock.lock();
        try {
            console.writer().print(CLEAR);
            if (insert != null)
                console.writer().print(~insert);
            if (progressBars.nonEmpty()) {
                final int lines = progressBars.stream().map(ProgressBar::buffer).mapToInt(it -> Math.toIntExact(it.codePoints().filter(c -> c == '\n').count() + 1)).sum();
                console.writer().print("\n".repeat(lines));
                console.writer().print(STR."\u001b[\{lines}A");
                console.writer().print(PRINT.formatted(progressBars.stream().map(ProgressBar::buffer).collect(Collectors.joining("\n", "\n", ""))));
            }
            console.flush();
        } finally { renderLock.unlock(); }
    }
    
    @Getter
    final Function<T, String> renderer;
    
    @Setter(AccessLevel.PROTECTED)
    @Getter
    @Default
    volatile T progress;
    
    @Setter(AccessLevel.PROTECTED)
    @Getter
    volatile String buffer;
    
    { update(); }
    
    { add(this); }
    
    public synchronized void update(final T progress = progress()) {
        progress(progress);
        if (supported())
            buffer(renderer().apply(progress()));
    }
    
    public synchronized void update(final Function<T, T> updater) = update(updater[progress()]);
    
    public synchronized void update(final Consumer<T> updater) = update(progress().let(updater));
    
    @Override
    public void close() throws Exception = remove(this);
    
}
