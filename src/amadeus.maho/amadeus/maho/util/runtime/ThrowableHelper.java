package amadeus.maho.util.runtime;

import java.io.PrintWriter;
import java.io.StringWriter;

import amadeus.maho.lang.Extension;

@Extension
public interface ThrowableHelper {
    
    static String dump(final Throwable $this) = new StringWriter().let(writer -> $this.printStackTrace(new PrintWriter(writer))).toString();
    
}
