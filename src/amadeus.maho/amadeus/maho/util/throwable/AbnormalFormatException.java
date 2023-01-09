package amadeus.maho.util.throwable;

import java.nio.file.Path;

import amadeus.maho.lang.NoArgsConstructor;

@NoArgsConstructor
public class AbnormalFormatException extends IllegalArgumentException {
    
    public AbnormalFormatException(final String msg, final Path path) = this(msg + "\npath: " + path.toString());
    
    public AbnormalFormatException(final String msg, final Path path, final int line) = this(msg + ", line: " + line + "\npath: " + path.toString());
    
    public AbnormalFormatException(final String msg, final Path path, final int line, final int section) = this(msg + ", line: " + line + ", section: " + section + "\npath: " + path.toString());
    
}
