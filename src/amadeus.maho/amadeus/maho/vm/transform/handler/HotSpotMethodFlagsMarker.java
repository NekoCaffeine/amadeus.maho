package amadeus.maho.vm.transform.handler;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.TransformerManager;
import amadeus.maho.transform.handler.base.marker.MethodMarker;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.vm.reflection.hotspot.InstanceKlass;
import amadeus.maho.vm.transform.mark.HotSpotMethodFlags;

@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class HotSpotMethodFlagsMarker extends MethodMarker<HotSpotMethodFlags> {
    
    @Override
    @SneakyThrows
    public void onMark() = mark(contextClassLoader());
    
    @SneakyThrows
    public void mark(final @Nullable ClassLoader loader) {
        TransformerManager.transform("hotspot.method_flags", ASMHelper.sourceName(sourceClass.name));
        InstanceKlass.klassLocals()[Class.forName(ASMHelper.sourceName(sourceClass.name), false, loader)].methods()
                .filter(method -> method.constMethod.name.equals(sourceMethod.name) && method.constMethod.signature.equals(sourceMethod.desc))
                .findFirst()
                .ifPresent(method -> method.flags((short) (method.flags() & annotation.mask() | annotation.value())));
    }
    
}
