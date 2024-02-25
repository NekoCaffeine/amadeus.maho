package amadeus.maho.util.build;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import amadeus.maho.core.Maho;
import amadeus.maho.core.MahoExport;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.control.Interrupt;
import amadeus.maho.util.logging.LogLevel;
import amadeus.maho.vm.JDWP;

import static amadeus.maho.util.build.ScriptHelper.D_PAIR;
import static amadeus.maho.vm.JDWP.IDECommand.Notification.Type.*;

public interface HotSwap {
    
    interface Logger {
        
        @Nullable BiConsumer<LogLevel, String> logger = MahoExport.logger()?.namedLogger("HotSwap") ?? null;
        
    }
    
    record Spy(Set<Class<?>> classes, Class<?> last[] = { null }) implements ClassFileTransformer {
        
        @Override
        public @Nullable byte[] transform(final @Nullable ClassLoader loader, final @Nullable String name, final @Nullable Class<?> clazz, final @Nullable ProtectionDomain domain, final @Nullable byte bytecode[]) {
            if (clazz != null && classes[clazz])
                Logger.logger.accept(LogLevel.TRACE, STR."HotSwap: \{(last[0] = clazz).getName()} \{STR."(\{loader})"}");
            return null;
        }
        
    }
    
    String
            DEBUG_WATCH_SESSION_KEY   = "amadeus.maho.debug.watch.session",
            DEBUG_WATCH_CLASSES_KEY   = "amadeus.maho.debug.watch.classes",
            DEBUG_WATCH_ALGORITHM_KEY = "amadeus.maho.debug.watch.algorithm";
    
    static List<String> addWatchProperty(final List<String> args, final String session, final String classes, final String algorithm = "MD5") {
        args += D_PAIR.formatted(DEBUG_WATCH_SESSION_KEY, session);
        args += D_PAIR.formatted(DEBUG_WATCH_CLASSES_KEY, classes);
        args += D_PAIR.formatted(DEBUG_WATCH_ALGORITHM_KEY, algorithm);
        return args;
    }
    
    static List<String> addWatchProperty(final List<String> args, final Workspace workspace, final Module module) {
        final Path classes = workspace.output(Javac.CLASSES_DIR, module);
        return addWatchProperty(args, (classes / Javac.SESSION).toAbsolutePath() | "/", classes.toAbsolutePath() | "/");
    }
    
    @SneakyThrows
    static void watch() {
        final @Nullable String session = System.getProperty(DEBUG_WATCH_SESSION_KEY), classes = System.getProperty(DEBUG_WATCH_CLASSES_KEY);
        if (session != null && classes != null) {
            final Path sessionFile = Path.of(session), classesDir = Path.of(classes);
            final String algorithm = System.getProperty(DEBUG_WATCH_ALGORITHM_KEY, "MD5");
            if (Files.isDirectory(classesDir)) {
                new Thread(() -> {
                    final HashMap<String, String> hash = { };
                    Files.list(classesDir)
                            .filter(Files::isDirectory)
                            .forEach(root -> Files.walk(root)
                                    .filter(path -> path.toString().endsWith(Javac.CLASS_SUFFIX))
                                    .forEach(path -> hash[name(root, path)] = path.checksum(algorithm)));
                    long time = Files.isRegularFile(sessionFile) ? Files.getLastModifiedTime(sessionFile).toMillis() : -1L;
                    while (true) {
                        if (Files.isRegularFile(sessionFile) && Files.isDirectory(classesDir)) {
                            final long now = Files.getLastModifiedTime(sessionFile).toMillis();
                            if (time != now) {
                                final HashMap<String, Path> map = { };
                                Files.list(classesDir)
                                        .filter(Files::isDirectory)
                                        .forEach(root -> Files.walk(root)
                                                .filter(path -> path.toString().endsWith(Javac.CLASS_SUFFIX))
                                                .forEach(path -> {
                                                    final String checksum = path.checksum(algorithm);
                                                    final String name = name(root, path);
                                                    if (!checksum.equals(hash[name])) {
                                                        hash[name] = checksum;
                                                        map[name] = path;
                                                    }
                                                }));
                                if (!map.isEmpty())
                                    try {
                                        final Instrumentation instrumentation = Maho.instrumentation();
                                        final ClassDefinition definitions[] = Stream.of(instrumentation.getAllLoadedClasses())
                                                .map(loadedClass -> {
                                                    final String name = loadedClass.getName();
                                                    final @Nullable Path path = map[name];
                                                    if (path != null) {
                                                        final ClassDefinition definition = { loadedClass, Files.readAllBytes(path) };
                                                        return definition;
                                                    }
                                                    return null;
                                                })
                                                .nonnull()
                                                .toArray(ClassDefinition[]::new);
                                        final Spy spy = { Stream.of(definitions).map(ClassDefinition::getDefinitionClass).collect(Collectors.toSet()) };
                                        instrumentation.addTransformer(spy, true);
                                        try {
                                            instrumentation.redefineClasses(definitions);
                                            final JDWP.IDECommand.Notification notification = { INFORMATION, "HotSwap successful", STR."reloaded \{definitions.length} classes" };
                                            JDWP.MessageQueue.notify(notification, Logger.logger);
                                        } catch (final Throwable throwable) {
                                            final JDWP.IDECommand.Notification notification = { WARNING, "HotSwap redefine failed", STR."\{throwable.getClass().getName()}: \{throwable.getMessage()}, class: \{spy.last()[0]}" };
                                            JDWP.MessageQueue.notify(notification, Logger.logger);
                                        } finally { instrumentation.removeTransformer(spy); }
                                    } catch (final Throwable throwable) {
                                        final JDWP.IDECommand.Notification notification = { WARNING, "HotSwap failed", STR."\{throwable.getClass().getName()}: \{throwable.getMessage()}" };
                                        JDWP.MessageQueue.notify(notification, Logger.logger);
                                    }
                                time = now;
                            }
                        }
                        Interrupt.doInterruptible(() -> Thread.sleep(1000));
                    }
                }, "HotSwap Watcher").start();
                final JDWP.IDECommand.Notification notification = { INFORMATION, "HotSwap watching", STR."Session File: \{sessionFile.toAbsolutePath() | "/"}<br>Classes Dir: \{classesDir.toAbsolutePath() | "/"}" };
                JDWP.MessageQueue.send(notification);
            }
            
        }
    }
    
    private static String name(final Path root, final Path path) = (root % path | "/").replace(Javac.CLASS_SUFFIX, "").replace('/', '.');
    
}
