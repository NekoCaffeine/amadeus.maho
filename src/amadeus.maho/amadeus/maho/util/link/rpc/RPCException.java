package amadeus.maho.util.link.rpc;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.RequiredArgsConstructor;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
public class RPCException extends Exception {
    
    int code;
    
    @Override
    public String getMessage() = STR."error code: \{code}, message: \{super.getMessage()}";
    
}
