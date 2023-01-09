package amadeus.maho.util.dynamic;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Stream;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.build.Javac;

import static amadeus.maho.util.dynamic.NIOExtendedWatchModifier.*;
import static java.nio.file.StandardWatchEventKinds.*;

@SneakyThrows
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FileSystemWatcher implements Runnable {
    
    protected static final WatchEvent.Kind<?> KINDS[] = { ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY };
    
    protected static final WatchEvent.Modifier
            FILE_TREE_MODIFIERS[]        = modifiers(FILE_TREE, SENSITIVITY_HIGH),
            SENSITIVITY_ONLY_MODIFIERS[] = modifiers(SENSITIVITY_HIGH);
    
    protected static WatchEvent.Modifier[] modifiers(final NIOExtendedWatchModifier... modifiers) = Stream.of(modifiers).map(NIOExtendedWatchModifier::modifier).toArray(WatchEvent.Modifier[]::new);
    
    WatchService watcher = FileSystems.getDefault().newWatchService();
    
    ConcurrentHashMap<WatchKey, Path> roots = { };
    
    public void watch(final Path root, final Predicate<Class<?>> predicate = _ -> true) {
        try {
            roots[root.register(watcher, KINDS, FILE_TREE_MODIFIERS)] = root;
        } catch (final UnsupportedOperationException e) {
            Files.walkFileTree(root, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
                    roots[dir.register(watcher, KINDS, SENSITIVITY_ONLY_MODIFIERS)] = root;
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
    
    protected void processEvents() {
        final @Nullable WatchKey key = watcher.poll(50, TimeUnit.MILLISECONDS);
        if (key != null) {
            key.pollEvents().stream()
                    .filter(event -> event.kind() != OVERFLOW)
                    .map(WatchEvent::context)
                    .cast(Path.class)
                    .filter(path -> path.toString().endsWith(Javac.CLASS_SUFFIX))
                    .forEach(path -> {
                    
                    });
        }
    }
    
    @Override
    public void run() {
    
    }
    
    public Thread asyncWatch() {
        final Thread watchThread = { this };
        watchThread.setName("FileSystem Watcher");
        watchThread.setDaemon(true);
        watchThread.start();
        return watchThread;
    }
    
}
