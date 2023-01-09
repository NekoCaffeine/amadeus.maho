package amadeus.maho.util.language;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public interface Compiler<T> {
    
    interface Owner<T> {
        
        Compiler<T> compiler();
        
    }
    
    Language owner();
    
    void compile(String source, OutputStream output);
    
    T load(InputStream input);
    
    default T compileAndLoad(final String source) = load(new ByteArrayInputStream(new ByteArrayOutputStream(1 << 12).let(it -> compile(source, it)).toByteArray()));
    
}
