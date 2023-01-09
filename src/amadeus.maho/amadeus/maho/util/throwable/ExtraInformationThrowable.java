package amadeus.maho.util.throwable;

import amadeus.maho.lang.NoArgsConstructor;

@NoArgsConstructor
public class ExtraInformationThrowable extends Throwable {
    
    @Override
    public Throwable fillInStackTrace() = this;
    
}
