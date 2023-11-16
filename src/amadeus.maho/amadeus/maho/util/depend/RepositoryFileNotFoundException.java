package amadeus.maho.util.depend;

import java.io.IOException;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RepositoryFileNotFoundException extends IOException {
    
    Repository repository;
    
    @Override
    public Throwable fillInStackTrace() = this;
    
}
