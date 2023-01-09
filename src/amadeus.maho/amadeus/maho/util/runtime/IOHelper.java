package amadeus.maho.util.runtime;

import java.io.Closeable;
import java.io.IOException;

public interface IOHelper {
    
    static void close(final Closeable... closeables) {
        for (final Closeable closeable : closeables)
            try { closeable.close(); } catch (final IOException ignored) { }
    }
    
}
