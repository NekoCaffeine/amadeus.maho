package amadeus.maho.util.filesystem;

import java.io.IOException;
import java.net.URI;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.runtime.ArrayHelper;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VirtualPath implements Path {
    
    VirtualFileSystem fileSystem;
    
    String path[];
    
    @Override
    public VirtualFileSystem getFileSystem() = fileSystem;
    
    @Override
    public boolean isAbsolute() = false;
    
    @Override
    public @Nullable VirtualPath getRoot() = null;
    
    @Override
    public VirtualPath getFileName() = fileSystem.path(ArrayHelper.sub(path, path.length - 1));
    
    @Override
    public VirtualPath getParent() = fileSystem.path(ArrayHelper.sub(path, 0, path.length - 1));
    
    @Override
    public int getNameCount() = path.length;
    
    @Override
    public VirtualPath getName(final int index) = fileSystem.path(path[index]);
    
    @Override
    public VirtualPath subpath(final int beginIndex, final int endIndex) = fileSystem.path(ArrayHelper.sub(path, beginIndex, endIndex));
    
    @Override
    public boolean startsWith(final Path other) = ArrayHelper.startsWith(path, check(other).path);
    
    @Override
    public boolean endsWith(final Path other) = ArrayHelper.endsWith(path, check(other).path);
    
    @Override
    public VirtualPath normalize() = this;
    
    @Override
    public VirtualPath resolve(final Path other) = fileSystem.path(ArrayHelper.addAll(path, check(other).path));
    
    @Override
    public Path relativize(final Path other) {
        final VirtualPath checked = check(other);
        if (ArrayHelper.startsWith(checked.path, path))
            return fileSystem.path(ArrayHelper.sub(checked.path, path.length));
        throw new IllegalStateException();
    }
    
    @Override
    public URI toUri() {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public VirtualPath toAbsolutePath() = this;
    
    @Override
    public VirtualPath toRealPath(final LinkOption... options) throws IOException = this;
    
    @Override
    public WatchKey register(final WatchService watcher, final WatchEvent.Kind<?>[] events, final WatchEvent.Modifier... modifiers) throws IOException {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public int compareTo(final Path other) = ArrayHelper.compareTo(path, check(other).path);
    
    public static VirtualPath check(final Path other) = switch (other) {
        case VirtualPath mahoPath -> mahoPath;
        case null, default        -> throw new ProviderMismatchException();
    };
    
}
