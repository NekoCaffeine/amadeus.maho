package amadeus.maho.util.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import jdk.nio.zipfs.ZipFileSystemProvider;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.Extension;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;

import static java.nio.file.StandardOpenOption.APPEND;

@Extension
@SneakyThrows
public interface FileHelper {
    
    class DeleteVisitor extends SimpleFileVisitor<Path> {
        
        @Getter
        private static final DeleteVisitor instance = { };
        
        @Override
        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
            file--;
            return FileVisitResult.CONTINUE;
        }
        
        @Override
        public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
            dir--;
            return FileVisitResult.CONTINUE;
        }
        
    }
    
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    class CopyVisitor extends SimpleFileVisitor<Path> {
        
        Path src, dst;
        
        @Override
        public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
            ~(dst / (src % dir).toString());
            return FileVisitResult.CONTINUE;
        }
        
        @Override
        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
            file >> dst / (src % file).toString();
            return FileVisitResult.CONTINUE;
        }
        
    }
    
    @Extension
    interface Zip {
        ZipFileSystemProvider zipFileSystemProvider = { };
        Map<String, ?> default_zip_env = Map.of("create", true, "encoding", "UTF-8", "compressionMethod", "DEFLATED");
        static FileSystem zipFileSystem(final Path path, final Map<String, ?> env = default_zip_env) = zipFileSystemProvider.newFileSystem(path, env);
        
    }
    
    static void OR(final Path path, final Consumer<Path> consumer) {
        if (Files.isDirectory(path))
            consumer.accept(path);
        else
            try (final FileSystem fileSystem = path.zipFileSystem()) { consumer.accept(fileSystem.getPath("")); }
    }
    
    static <T> T XOR(final Path path, final Function<Path, T> function) {
        if (Files.isDirectory(path))
            return function.apply(path);
        else
            try (final FileSystem fileSystem = path.zipFileSystem()) { return function.apply(fileSystem.getPath("")); }
    }
    
    static Path PREDEC(final Path path) {
        if (Files.isDirectory(path))
            Files.walkFileTree(path, DeleteVisitor.instance());
        else
            path--;
        return path;
    }
    
    static Path POSTDEC(final Path path) {
        Files.deleteIfExists(path);
        return path;
    }
    
    static Path GTGT(final @Nullable Path src, final Path dst) {
        if (src != null)
            if (Files.isDirectory(src))
                Files.walkFileTree(src, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new CopyVisitor(src, dst));
            else if (Files.isDirectory(dst))
                Files.copy(src, dst / src.getFileName().toString(), StandardCopyOption.REPLACE_EXISTING);
            else {
                ~-dst;
                Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            }
        return dst;
    }
    
    static Path GTGTGT(final @Nullable Path src, final Path dst) {
        if (src != null) {
            src >> dst;
            --src;
        }
        return dst;
    }
    
    static String OR(final Path path, final String separator) = path.toString().replace(path.getFileSystem().getSeparator(), separator);
    
    static String XOR(final Path path, final String separator) = "\"" + path.toString().replace(path.getFileSystem().getSeparator(), separator) + "\"";
    
    static Path DIV(final Path head, final Path tail) = head.resolve(tail);
    
    static Path DIV(final Path head, final String tail) = head.resolve(tail);
    
    static Path MOD(final Path path, final Path other) = path.relativize(other);
    
    static Path LTLT(final Path path, final String suffix) = path.resolveSibling(path.getFileName() + suffix);
    
    static Path LT(final Path path, final Path other) = path.resolveSibling(other);
    
    static Path LT(final Path path, final String other) = path.resolveSibling(other);
    
    static Path MINUS(final Path path) = path.getParent();
    
    static Path PLUS(final Path path) = path;
    
    static @Nullable Path TILDE(final @Nullable Path path) {
        if (path != null)
            Files.createDirectories(path);
        return path;
    }
    
    static Path GTGT(final byte data[], final Path path) {
        ~-path;
        Files.write(path, data);
        return path;
    }
    
    static Path GTGTGT(final byte data[], final Path path) {
        ~-path;
        Files.write(path, data, APPEND);
        return path;
    }
    
    static Path GTGT(final CharSequence sequence, final Path path) {
        ~-path;
        Files.writeString(path, sequence, StandardCharsets.UTF_8);
        return path;
    }
    
    static Path GTGTGT(final CharSequence sequence, final Path path) {
        ~-path;
        Files.writeString(path, sequence, StandardCharsets.UTF_8, APPEND);
        return path;
    }
    
    static String fileName(final Path path) {
        final String name = path.getFileName().toString();
        final int index = name.lastIndexOf('.');
        return index == -1 ? name : name.substring(0, index);
    }
    
    static @Nullable String extensionName(final Path path) {
        final String name = path.getFileName().toString();
        final int index = name.lastIndexOf('.');
        return index == -1 ? null : name.substring(index + 1);
    }
    
    static void retryWhenIOE(final Runnable runnable, final @Nullable String logWhenIOE = "Wait for the file occupation to be manually released.", final long millis = 500L) {
        boolean logIOE = logWhenIOE != null;
        while (true)
            try {
                runnable.run();
                break;
            } catch (final IOException e) { // Perhaps another process is holding the file lock so needs to wait for it to be released.
                if (logIOE) {
                    System.out.println(logWhenIOE);
                    logIOE = false;
                }
                Thread.sleep(millis);
            }
    }
    
    static void retryWhenFailed(final Path monitoringPath, final Consumer<Path> action, final Predicate<Throwable> shouldRetry = Exception.class::isInstance, final long millis = 500L) {
        long lastModifiedTime = -1;
        while (true)
            try {
                final long modifiedTime = Files.getLastModifiedTime(monitoringPath).toMillis();
                if (lastModifiedTime != modifiedTime)
                    lastModifiedTime = modifiedTime;
                else {
                    Thread.sleep(millis);
                    continue;
                }
                action.accept(monitoringPath);
                break;
            } catch (final Throwable throwable) {
                if (throwable instanceof IOException || !shouldRetry.test(throwable))
                    throw throwable;
            }
    }
    
    static FileVisitor<? super Path> visitor(final BiConsumer<Path, BasicFileAttributes> consumer, final BiPredicate<Path, BasicFileAttributes> predicate = (_, _) -> true) = new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
            if (predicate.test(file, attrs))
                consumer.accept(file, attrs);
            return FileVisitResult.CONTINUE;
        }
    };
    
    static FileVisitor<? super Path> processor(final BiFunction<Path, BasicFileAttributes, FileVisitResult> function, final BiPredicate<Path, BasicFileAttributes> predicate = (_, _) -> true) = new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException = predicate.test(file, attrs) ? function.apply(file, attrs) : FileVisitResult.CONTINUE;
    };
    
    static void projection(final Path source, final Path target, final BiConsumer<Path, Path> projector = FileHelper::GTGT, final BiPredicate<Path, BasicFileAttributes> predicate = (_, _) -> true)
            = Files.walkFileTree(source, visitor((path, attributes) -> projector.accept(path, target / (source % path).toString()), predicate));
    
}
