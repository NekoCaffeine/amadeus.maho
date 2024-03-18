package amadeus.maho.util.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public interface Gitignore {
    
    String SUFFIX = ".gitignore";
    
    static List<Path> listDir(final Path root) {
        final Path gitignore = root / SUFFIX;
        if (Files.exists(gitignore))
            try {
                return Files.lines(gitignore)
                        .filter(line -> line.startsWith("/"))
                        .map(line -> root / line.substring(1))
                        .filter(Files::isDirectory)
                        .toList();
            } catch (final IOException _) { }
        return List.of();
    }
    
}
