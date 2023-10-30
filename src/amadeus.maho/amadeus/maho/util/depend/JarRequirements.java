package amadeus.maho.util.depend;

import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.ToString;

@ToString
@EqualsAndHashCode
public record JarRequirements(boolean classes, boolean sources, boolean javadoc) {
    
    public static final JarRequirements
            ALL          = { true, true, true },
            WITHOUT_DOC  = { true, true, false },
            ONLY_CLASSES = { true, false, false },
            ONLY_SOURCES = { false, true, false },
            ONLY_DOC     = { false, false, true };
    
}
