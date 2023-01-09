package amadeus.maho.util.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import amadeus.maho.lang.Extension;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;

@Extension
public interface PropertiesHelper {
    
    interface Overrider {
        
        String OVERRIDE_PROPERTIES = "override.properties";
        
        @SneakyThrows
        static void override(final Path path = Path.of(System.getProperty(OVERRIDE_PROPERTIES, OVERRIDE_PROPERTIES))) = override(new Properties(System.getProperties()).let(it -> it.load(Files.newInputStream(path))));
        
        static void override(final Properties properties) = System.setProperties(properties);
        
    }
    
    static @Nullable String GET(final Properties properties, final String key) = properties.getProperty(key);
    
    static @Nullable String PUT(final Properties properties, final String key, final @Nullable String value) = properties.setProperty(key, value) instanceof String oldValue ? oldValue : null;
    
}
