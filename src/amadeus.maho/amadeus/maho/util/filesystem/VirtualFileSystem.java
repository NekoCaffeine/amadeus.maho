package amadeus.maho.util.filesystem;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.Set;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.util.concurrent.ConcurrentTrie;
import amadeus.maho.util.concurrent.ConcurrentWeakIdentityHashMap;
import amadeus.maho.util.runtime.ArrayHelper;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VirtualFileSystem extends FileSystem {
    
    @Getter
    VirtualFileSystemProvider provider;
    
    @Getter(AccessLevel.PROTECTED)
    ConcurrentWeakIdentityHashMap<FileStore, VirtualPath> roots = { };
    
    @Getter(AccessLevel.PROTECTED)
    ConcurrentTrie.Array<String, VirtualPath> trie = { };
    
    @Override
    public void close() { }
    
    @Override
    public boolean isOpen() = true;
    
    @Override
    public boolean isReadOnly() = false;
    
    @Override
    public String getSeparator() = "";
    
    @Override
    public Iterable<Path> getRootDirectories() = (Iterable<Path>) (Iterable) roots.values();
    
    @Override
    public Iterable<FileStore> getFileStores() = roots.keySet();
    
    @Override
    public Set<String> supportedFileAttributeViews() = Set.of();
    
    @Override
    public Path getPath(final String first, final String... more) = path(ArrayHelper.insert(more, first));
    
    public VirtualPath path(final String... paths) = trie().root().reach(paths).getOrCreate(() -> createPath(paths));
    
    protected VirtualPath createPath(final String... paths) = { this, paths };
    
    @Override
    public PathMatcher getPathMatcher(final String syntaxAndPattern) {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public WatchService newWatchService() throws IOException {
        throw new UnsupportedOperationException();
    }
    
}
