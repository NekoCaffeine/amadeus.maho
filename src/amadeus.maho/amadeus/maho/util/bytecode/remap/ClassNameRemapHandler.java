package amadeus.maho.util.bytecode.remap;

import java.util.Map;

public interface ClassNameRemapHandler extends RemapHandler {
    
    Map<String, String> mapping();
    
    @Override
    default String mapInternalName(final String name) = mapping().getOrDefault(name, name);
    
    static ClassNameRemapHandler of(final Map<String, String> mapping) = () -> mapping;
    
}
