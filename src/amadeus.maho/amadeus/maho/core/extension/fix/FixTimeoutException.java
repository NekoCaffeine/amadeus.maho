package amadeus.maho.core.extension.fix;

import java.util.concurrent.TimeoutException;

import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.transform.mark.Patch;

@Patch(value = TimeoutException.class, onlyFirstTime = true)
@NoArgsConstructor(on = @Patch.Exception)
public class FixTimeoutException extends TimeoutException {
    
    @Override
    public Throwable fillInStackTrace() = this;
    
}
