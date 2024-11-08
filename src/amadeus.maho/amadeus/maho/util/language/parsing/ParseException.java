package amadeus.maho.util.language.parsing;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.throwable.ExtraInformationThrowable;

@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ParseException extends RuntimeException {
    
    @Nullable String debugSource;
    
    int pos;
    
    {
        if (debugSource() != null)
            addSuppressed(new ExtraInformationThrowable(debugSource()));
    }
    
}
