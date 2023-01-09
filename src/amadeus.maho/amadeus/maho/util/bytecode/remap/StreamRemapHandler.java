package amadeus.maho.util.bytecode.remap;

import java.util.stream.Stream;

public interface StreamRemapHandler extends RemapHandler {
    
    Stream<RemapHandler> remapHandlers();
    
    boolean hasRemapHandlers();
    
    @Override
    default String mapModule(final String name) {
        if (hasRemapHandlers()) {
            final String closure[] = { name };
            remapHandlers().forEach(remapHandler -> closure[0] = remapHandler.mapModule(closure[0]));
            return closure[0];
        }
        return name;
    }
    
    @Override
    default String mapPackage(final String name) {
        if (hasRemapHandlers()) {
            final String closure[] = { name };
            remapHandlers().forEach(remapHandler -> closure[0] = remapHandler.mapPackage(closure[0]));
            return closure[0];
        }
        return name;
    }
    
    @Override
    default String mapInternalName(final String name) {
        if (hasRemapHandlers()) {
            final String closure[] = { name };
            remapHandlers().forEach(remapHandler -> closure[0] = remapHandler.mapInternalName(closure[0]));
            return closure[0];
        }
        return name;
    }
    
    @Override
    default String mapFieldName(final String owner, final String name) {
        if (hasRemapHandlers()) {
            final String closure[] = { name };
            remapHandlers().forEach(remapHandler -> closure[0] = remapHandler.mapFieldName(owner, closure[0]));
            return closure[0];
        }
        return name;
    }
    
    @Override
    default String mapMethodName(final String owner, final String name, final String descriptor) {
        if (hasRemapHandlers()) {
            final String closure[] = { name };
            remapHandlers().forEach(remapHandler -> closure[0] = remapHandler.mapMethodName(owner, closure[0], descriptor));
            return closure[0];
        }
        return name;
    }
    
}
