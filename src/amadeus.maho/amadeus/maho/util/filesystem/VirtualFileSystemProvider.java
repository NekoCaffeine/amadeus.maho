package amadeus.maho.util.filesystem;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import java.util.Set;

import jdk.internal.foreign.MappedMemorySegmentImpl;
import jdk.internal.foreign.ResourceScopeImpl;

import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

@RequiredArgsConstructor
@TransformProvider
public abstract class VirtualFileSystemProvider extends FileSystemProvider {
    
    @Hook(value = MappedMemorySegmentImpl.class, isStatic = true)
    private static Hook.Result makeMappedSegment(final Path path, final long bytesOffset, final long bytesSize, final FileChannel.MapMode mapMode, final ResourceScopeImpl scope) {
        if (path instanceof VirtualPath virtualPath)
            return Hook.Result.VOID; // todo impl
        return Hook.Result.VOID;
    }
    
    @Override
    public abstract String getScheme();
    
    @Override
    public FileSystem newFileSystem(final URI uri, final Map<String, ?> env) throws IOException {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public FileSystem getFileSystem(final URI uri) {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public Path getPath(final URI uri) {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public SeekableByteChannel newByteChannel(final Path path, final Set<? extends OpenOption> options, final FileAttribute<?>... attrs) throws IOException {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public DirectoryStream<Path> newDirectoryStream(final Path dir, final DirectoryStream.Filter<? super Path> filter) throws IOException {
        return null;
    }
    
    @Override
    public boolean isSameFile(final Path path1, final Path path2) throws IOException = path1 == path2;
    
    @Override
    public boolean isHidden(final Path path) throws IOException = false;
    
    @Override
    public @Nullable FileStore getFileStore(final Path path) throws IOException = null;
    
    @Override
    public void checkAccess(final Path path, final AccessMode... modes) throws IOException { }
    
    @Override
    public <V extends FileAttributeView> @Nullable V getFileAttributeView(final Path path, final Class<V> type, final LinkOption... options) = null;
    
    @Override
    public <A extends BasicFileAttributes> @Nullable A readAttributes(final Path path, final Class<A> type, final LinkOption... options) throws IOException = null;
    
    @Override
    public @Nullable Map<String, Object> readAttributes(final Path path, final String attributes, final LinkOption... options) throws IOException = null;
    
    @Override
    public void setAttribute(final Path path, final String attribute, final Object value, final LinkOption... options) throws IOException { }
    
}
