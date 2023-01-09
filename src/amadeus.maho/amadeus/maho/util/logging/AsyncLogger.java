package amadeus.maho.util.logging;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.PrintStream;
import java.lang.ref.WeakReference;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.util.control.Interrupt;
import amadeus.maho.util.dynamic.CallerContext;
import amadeus.maho.util.function.CloseableConsumer;
import amadeus.maho.util.function.FunctionHelper;
import amadeus.maho.util.misc.Environment;
import amadeus.maho.util.runtime.DebugHelper;
import amadeus.maho.vm.tools.hotspot.WhiteBox;
import amadeus.maho.vm.transform.mark.HotSpotJIT;

import static amadeus.maho.core.MahoExport.MAHO_LOGS_FORCED_INTERRUPTION;

@HotSpotJIT
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AsyncLogger extends Thread implements AutoCloseable {
    
    protected static final PrintStream syserr = LoggerHelper.makeStdOut(FileDescriptor.err);
    
    // avoid allocating memory on the heap after an OutOfMemoryError has occurred
    protected static final LogRecord oomRecord = { "Logging", LogLevel.FATAL, "OutOfMemoryError" };
    
    @Getter(AccessLevel.PROTECTED)
    final CopyOnWriteArrayList<Consumer<LogRecord>> consumers = { };
    
    @Getter
    final LinkedBlockingQueue<LogRecord> records = { };
    
    @Getter
    final Thread shutdownHook = initShutdownHook();
    
    volatile boolean closed = false;
    
    {
        setName("logging");
        setDaemon(true);
        Runtime.getRuntime().addShutdownHook(shutdownHook());
    }
    
    @SneakyThrows
    protected Thread initShutdownHook() {
        final WeakReference<self> reference = { this };
        return { () -> reference.get()?.close(), "logging-shutdownHook" };
    }
    
    public void addConsumer(final Consumer<LogRecord> consumer) = consumers() += consumer;
    
    public void addCloseableConsumer(final Consumer<LogRecord> consumer, final Closeable closeable) = consumers() += CloseableConsumer.of(consumer, closeable);
    
    @Override
    @SneakyThrows
    public void run() {
        while (!closed)
            try {
                Stream.generate(records()::take).forEach(this::forEach);
            } catch (final Throwable throwable) {
                if (throwable instanceof OutOfMemoryError)
                    onOutOfMemoryError();
                if (nonFatalThrowable(throwable))
                    continue;
                if (!(throwable instanceof InterruptedException) && !(throwable instanceof ClosedChannelException) || !closed) {
                    LoggerHelper.resetStdOutIfHasWrapper();
                    DebugHelper.breakpoint();
                    System.err.println("Unexpected interruption!");
                    throwable.printStackTrace();
                    throw throwable;
                }
            }
    }
    
    public void onOutOfMemoryError() {
        WhiteBox.instance().fullGC(); // force full gc
        forEach(oomRecord);
    }
    
    protected boolean nonFatalThrowable(final Throwable throwable) = throwable instanceof OutOfMemoryError;
    
    protected void forEach(final LogRecord record) { try { consumers().forEach(consumer -> consumer.accept(record)); } catch (final Exception e) { if (!(e instanceof ClosedChannelException)) e.printStackTrace(syserr); } }
    
    public void publish(final LogRecord record) = records() += record;
    
    public void log(final String name, final LogLevel level, final String message) = publish(new LogRecord(name, level, message));
    
    public BiConsumer<LogLevel, String> namedLogger(final String name = CallerContext.caller().getSimpleName()) = (level, message) -> log(name, level, message);
    
    @SneakyThrows
    public synchronized void close() throws SecurityException {
        if (!closed) {
            if (isAlive() && !Environment.local().lookup(MAHO_LOGS_FORCED_INTERRUPTION, false))
                while (!records.isEmpty())
                    Interrupt.doInterruptible(() -> Thread.sleep(10L));
            closed = true;
            interrupt();
            consumers().stream()
                    .cast(Closeable.class)
                    .forEach(FunctionHelper.ignored(Closeable::close));
        }
    }
    
    static { FunctionHelper.class.getDeclaredClasses(); } // Avoid class loading errors caused by module reader being closed.
    
}
