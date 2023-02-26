package amadeus.maho.util.filesystem;

import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.Iterator;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.RequiredArgsConstructor;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VirtualDirectoryStream implements DirectoryStream<Path> {
    
    VirtualFileSystem fileSystem;
    
    @Override
    public Iterator<Path> iterator() = (Iterator<Path>) (Iterator) fileSystem.trie().values().iterator();
    
    @Override
    public void close() { }
    
}
