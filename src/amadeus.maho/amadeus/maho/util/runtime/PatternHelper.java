package amadeus.maho.util.runtime;

import java.util.Map;
import java.util.regex.Pattern;

import amadeus.maho.lang.Extension;
import amadeus.maho.transform.mark.Proxy;
import amadeus.maho.transform.mark.base.TransformProvider;

import static amadeus.maho.util.bytecode.Bytecodes.INVOKEVIRTUAL;

@Extension
@TransformProvider
public interface PatternHelper {
    
    @Proxy(value = INVOKEVIRTUAL, selector = "namedGroups")
    static Map<String, Integer> namedGroupsIndex(final Pattern $this) = Map.of();
    
}
