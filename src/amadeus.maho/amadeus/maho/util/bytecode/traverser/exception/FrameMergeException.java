package amadeus.maho.util.bytecode.traverser.exception;

import java.util.List;
import java.util.stream.Collectors;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.util.bytecode.traverser.Frame;
import amadeus.maho.util.bytecode.traverser.TypeOwner;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FrameMergeException extends IllegalArgumentException {
    
    Frame a, b;
    
    public FrameMergeException(final String message, final Frame a, final Frame b) {
        super(STR."""
\{message}
a: \{frame(a)}
b: \{frame(b)}""");
        this.a = a;
        this.b = b;
    }
    
    public static String frame(final Frame frame) = STR."""
            \tlocal: \{objects(frame.locals())}
            \tstack: \{objects(frame.stack())}""";
    
    public static String objects(final List<TypeOwner> list)
            = list == null ? "<empty>" : list.stream().map(TypeOwner::toString).collect(Collectors.joining(", ", "[", "]"));
    
}
