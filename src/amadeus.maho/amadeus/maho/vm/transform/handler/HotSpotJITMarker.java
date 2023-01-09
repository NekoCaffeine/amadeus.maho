package amadeus.maho.vm.transform.handler;

import java.util.stream.Stream;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.TransformerManager;
import amadeus.maho.transform.handler.base.marker.BaseMarker;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.vm.tools.hotspot.jit.JITCompiler;
import amadeus.maho.vm.transform.mark.HotSpotJIT;

@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class HotSpotJITMarker extends BaseMarker<HotSpotJIT> {
    
    @Override
    @SneakyThrows
    public void onMark() = mark(contextClassLoader());
    
    @SneakyThrows
    public void mark(final @Nullable ClassLoader loader) {
        TransformerManager.transform("hotspot.jit", ASMHelper.sourceName(sourceClass.name));
        final Class<?> clazz = Class.forName(ASMHelper.sourceName(sourceClass.name), false, loader);
        Stream.concat(Stream.of(clazz.getDeclaredConstructors()), Stream.of(clazz.getDeclaredMethods()))
                .forEach(executable -> JITCompiler.instance().compile(executable, (executable.getAnnotation(HotSpotJIT.class) ?? annotation).value()));
    }
    
}
