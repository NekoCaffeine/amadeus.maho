package amadeus.maho.util.logging;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import jdk.internal.logger.LoggerFinderLoader;

import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.TransformTarget;
import amadeus.maho.transform.mark.base.TransformMetadata;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.bytecode.context.TransformContext;
import amadeus.maho.util.dynamic.CallerContext;
import amadeus.maho.util.misc.Environment;
import amadeus.maho.util.runtime.ObjectHelper;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static amadeus.maho.core.MahoExport.*;
import static amadeus.maho.util.concurrent.AsyncHelper.*;
import static java.nio.file.StandardOpenOption.*;
import static org.objectweb.asm.Opcodes.*;

@TransformProvider
public interface LoggerHelper {
    
    String DISABLE_THREAD_GROUP_PRINT_THREAD_CONTEXT = "ThreadGroup.uncaughtException.dont_print_thread";
    
    @TransformTarget(targetClass = ThreadGroup.class, selector = "uncaughtException", metadata = @TransformMetadata(enable = DISABLE_THREAD_GROUP_PRINT_THREAD_CONTEXT))
    private static void uncaughtException(final TransformContext context, final ClassNode classNode, final MethodNode methodNode) {
        boolean flag = false;
        for (final Iterator<AbstractInsnNode> iterator = methodNode.instructions.iterator(); iterator.hasNext(); ) {
            final AbstractInsnNode insn = iterator.next();
            if (insn instanceof FieldInsnNode fieldInsnNode && insn.getOpcode() == GETSTATIC && fieldInsnNode.name.equals("err")) {
                context.markModified();
                flag = true;
            }
            if (flag)
                iterator.remove();
            if (insn instanceof MethodInsnNode methodInsnNode && insn.getOpcode() == INVOKEVIRTUAL && methodInsnNode.name.equals("print"))
                return;
        }
    }
    
    @Hook(value = LoggerFinderLoader.class, isStatic = true)
    private static Hook.Result getLoggerFinder() = Hook.Result.nullToVoid(loggerFinder());
    
    @SneakyThrows
    static AsyncLogger makeAsyncLogger(final String name, final boolean logFile = true, final Function<LogRecord, String> formatter = LoggerFormatter::format) {
        final LogLevel level = LogLevel.findLogLevel(Environment.local().lookup(MAHO_LOGS_LEVEL, LogLevel.DEBUG.name()));
        final AsyncLogger logger = { };
        final PrintStream sysout = makeStdOut(FileDescriptor.out);
        final Charset charset = Charset.forName(Environment.local().lookup(MAHO_LOGS_ENCODE, "UTF-8"));
        final @Nullable SeekableByteChannel channel = logFile ? makeLogFileHandler(formatter) : null;
        logger.addCloseableConsumer(record -> {
            final boolean console = record.level().compareTo(level) >= 0;
            if (!console && channel == null)
                return;
            final String message = formatter.apply(record);
            if (console) {
                sysout.print(message);
                sysout.flush();
            }
            if (channel != null)
                channel.write(ByteBuffer.wrap(formatter.apply(record).getBytes(charset)));
        }, channel);
        logger.start();
        return logger;
    }
    
    static void wrapperStdOut(final @Nullable AsyncLogger logger) {
        if (logger != null) {
            System.setProperty(DISABLE_THREAD_GROUP_PRINT_THREAD_CONTEXT, Boolean.TRUE.toString());
            final BiConsumer<LogLevel, String> mergedLogger = mergeContext(logger);
            System.setOut(makeWrapperStdOut(FileDescriptor.out, msg -> mergedLogger.accept(LogLevel.INFO, msg)));
            System.setErr(makeWrapperStdOut(FileDescriptor.err, msg -> mergedLogger.accept(LogLevel.ERROR, msg)));
        }
    }
    
    static boolean hasWrapper() = System.out instanceof WrapperPrintStream || System.err instanceof WrapperPrintStream;
    
    static void resetStdOut() {
        System.setOut(makeStdOut(FileDescriptor.out));
        System.setErr(makeStdOut(FileDescriptor.err));
    }
    
    static void resetStdOutIfHasWrapper() {
        if (System.out instanceof WrapperPrintStream wrapper && wrapper.source() instanceof PrintStream source)
            System.setOut(source);
        if (System.err instanceof WrapperPrintStream wrapper && wrapper.source() instanceof PrintStream source)
            System.setErr(source);
    }
    
    static boolean ignoreContext(final String name) = name.startsWith("java.") || name.contains("/") || name.startsWith(LoggerHelper.class.getPackageName().concat("."));
    
    static @Nullable StackWalker.StackFrame caller() = CallerContext.Stack.walker().walk(frames -> frames
            .dropWhile(frame -> frame.getDeclaringClass() != WrapperPrintStream.class)
            .dropWhile(frame -> frame.getDeclaringClass() == WrapperPrintStream.class)
            .findFirst()
            .orElse(null));
    
    static BiConsumer<LogLevel, String> mergeContext(final AsyncLogger logger) = (level, message) -> {
        final @Nullable StackWalker.StackFrame caller = caller();
        if (caller != null && !ignoreContext(caller.getClassName()))
            message = "[" + caller.getClassName() + "." + caller.getMethodName() + "#" + caller.getByteCodeIndex() + "(" + caller.getFileName() + ":" + caller.getLineNumber() + ")] : " + message;
        logger.publish(new LogRecord(level == LogLevel.INFO ? "STDOUT" : "STDERR", level, message, Instant.now(), caller));
    };
    
    @SneakyThrows
    static PrintStream newPrintStream(final FileOutputStream outputStream, final @Nullable Charset charset) {
        final BufferedOutputStream out = { outputStream, 128 };
        if (charset != null)
            return { out, true, charset };
        return { out, true };
    }
    
    @SneakyThrows
    static WrapperPrintStream newWrapperPrintStream(final FileDescriptor fileDescriptor, final Consumer<String> logger, final Charset charset) {
        if (charset != null)
            try {
                return { makeStdOut(fileDescriptor), logger, charset };
            } catch (final UnsupportedCharsetException ignored) { }
        return { makeStdOut(fileDescriptor), logger };
    }
    
    static Charset consoleCharset() = System.console()?.charset() ?? Charset.defaultCharset();
    
    static PrintStream makeStdOut(final FileDescriptor fileDescriptor) = newPrintStream(new FileOutputStream(fileDescriptor), consoleCharset());
    
    static WrapperPrintStream makeWrapperStdOut(final FileDescriptor fileDescriptor, final Consumer<String> logger) = newWrapperPrintStream(fileDescriptor, logger, consoleCharset());
    
    DateTimeFormatter LOG_FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss-SSS").withZone(ZoneId.systemDefault());
    
    @SneakyThrows
    static @Nullable SeekableByteChannel makeLogFileHandler(final Function<LogRecord, String> formatter) {
        final @Nullable String contextInstance = Environment.local().lookup(MAHO_LOGS_INSTANCE);
        try {
            Path logsPath = workDirectory() / "logs";
            if (contextInstance != null)
                logsPath = logsPath / contextInstance;
            final Path logPath = ~logsPath / "latest.log";
            if (Files.isRegularFile(logPath)) {
                final BasicFileAttributes attributes = Files.getFileAttributeView(logPath, BasicFileAttributeView.class).readAttributes();
                final String rename = LocalDateTime.ofInstant(attributes.lastModifiedTime().toInstant(), ZoneId.systemDefault()).format(LOG_FILE_NAME_FORMATTER);
                Path renameTarget = logPath < rename + ".log";
                if (Files.exists(renameTarget))
                    renameTarget = logPath < rename + "-" + System.currentTimeMillis() + ".log";
                try { logPath >>> renameTarget; } catch (final IOException e) { e.printStackTrace(); }
            }
           return Files.newByteChannel(logPath, WRITE, CREATE_NEW);
        } catch (final IOException e) { e.printStackTrace(); }
        return null;
    }
    
    @SneakyThrows
    static Process redirect(final Process process, final Charset charset = Charset.defaultCharset(), final BiConsumer<LogLevel, String> consumer) {
        final ProcessHandle.Info info = process.info();
        consumer.accept(LogLevel.DEBUG, "Process IO redirect: <%d> %s".formatted(process.pid(), info.command().orElse("?")));
        handleInput(process, info, charset, consumer, LogLevel.INFO, process.getInputStream());
        handleInput(process, info, charset, consumer, LogLevel.ERROR, process.getErrorStream());
        return process;
    }
    
    @SneakyThrows
    private static void handleInput(final Process process, final ProcessHandle.Info info, final Charset charset, final BiConsumer<LogLevel, String> consumer, final LogLevel level, final InputStream input) = async(() -> {
        final BufferedReader reader = { new InputStreamReader(input, charset) };
        Stream.generate(reader::readLine)
                .takeWhile(ObjectHelper::nonNull)
                .forEach(line -> consumer.accept(level, line));
    }, newThreadExecutor("Process IO redirect: <%d> %s".formatted(process.pid(), info.command().map(Path::of).map(Path::fileName).orElse("?")), true));
    
}
