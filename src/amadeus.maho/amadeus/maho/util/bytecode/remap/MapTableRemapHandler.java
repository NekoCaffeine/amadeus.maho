package amadeus.maho.util.bytecode.remap;

import amadeus.maho.util.container.MapTable;

@FunctionalInterface
public interface MapTableRemapHandler extends RemapHandler {
    
    MapTable<String, String, String> mapperTable();
    
    @Override
    default String mapModule(final String name) = mapperTable().getOrDefault(name, null, name);
    
    @Override
    default String mapPackage(final String name) = mapperTable().getOrDefault(name, null, name);
    
    @Override
    default String mapInternalName(final String name) = mapperTable().getOrDefault(name, null, name);
    
    @Override
    default String mapFieldName(final String owner, final String name) = mapperTable().getOrDefault(owner, name, name);
    
    @Override
    default String mapMethodName(final String owner, final String name, final String descriptor) = mapperTable().getOrDefault(owner, name + descriptor, name);
    
}
