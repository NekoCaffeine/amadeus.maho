package amadeus.maho.util.throwable;

import java.nio.file.Path;

import amadeus.maho.lang.NoArgsConstructor;

@NoArgsConstructor
public class AbnormalFormatException extends IllegalArgumentException {
    
    public AbnormalFormatException(final String msg, final Path path) = this(STR."""
\{msg}
path: \{path.toString()}""");
    
    public AbnormalFormatException(final String msg, final Path path, final int line) = this(STR."""
\{msg}, line: \{line}
path: \{path.toString()}""");
    
    public AbnormalFormatException(final String msg, final Path path, final int line, final int section) = this(STR."""
\{msg}, line: \{line}, section: \{section}
path: \{path.toString()}""");
    
}
