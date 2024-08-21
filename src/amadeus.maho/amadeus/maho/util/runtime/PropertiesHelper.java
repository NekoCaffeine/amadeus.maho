package amadeus.maho.util.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import amadeus.maho.core.MahoExport;
import amadeus.maho.lang.Extension;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;

@Extension
public interface PropertiesHelper {
    
    interface Overrider {
        
        String OVERRIDE_PROPERTIES = "override.properties";
        
        static void overrideByMahoHome() {
            final @Nullable String home = System.getenv(MahoExport.MAHO_HOME_VARIABLE);
            if (home != null)
                override(Path.of(home) / OVERRIDE_PROPERTIES);
        }
        
        @SneakyThrows
        static void override(final Path path = Path.of(OVERRIDE_PROPERTIES)) {
            if (Files.isRegularFile(path))
                override(load(path));
        }
        
        static void override(final Map<String, String> properties) = System.getProperties().putAll(properties);
        
        @SneakyThrows
        static Map<String, String> load(final Path path) = Files.readAllLines(path).stream().map(line -> line.split("=", 2)).collect(Collectors.toMap(pair -> pair[0], pair -> pair[1]));
        
    }
    
    static @Nullable String GET(final Properties properties, final String key) = properties.getProperty(key);
    
    static @Nullable String PUT(final Properties properties, final String key, final @Nullable String value) = properties.setProperty(key, value) instanceof String oldValue ? oldValue : null;
    
}
