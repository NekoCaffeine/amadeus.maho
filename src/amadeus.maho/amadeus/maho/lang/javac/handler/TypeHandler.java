package amadeus.maho.lang.javac.handler;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;

import amadeus.maho.transform.mark.Hook;

// @TransformProvider
public class TypeHandler {
    
    @Hook
    private static Hook.Result isReifiable(final Types $this, final Type t) = Hook.Result.TRUE;
    
}
