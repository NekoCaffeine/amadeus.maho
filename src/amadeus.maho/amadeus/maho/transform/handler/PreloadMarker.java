package amadeus.maho.transform.handler;

import java.lang.invoke.MethodType;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.transform.TransformerManager;
import amadeus.maho.transform.handler.base.marker.BaseMarker;
import amadeus.maho.transform.mark.Preload;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.runtime.MethodHandleHelper;
import amadeus.maho.util.runtime.ReflectionHelper;

@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class PreloadMarker extends BaseMarker<Preload> {
    
    @Override
    @SneakyThrows
    public void onMark() {
        TransformerManager.transform("preload", ASMHelper.sourceName(sourceClass.name));
        preload(annotation, Class.forName(ASMHelper.sourceName(sourceClass.name), false, contextClassLoader()));
    }
    
    @SneakyThrows
    public static void preload(final Preload annotation, final Class<?> clazz) {
        if (annotation.initialized())
            ~clazz;
        if (annotation.reflectionData())
            ReflectionHelper.initReflectionData(clazz);
        if (!annotation.invokeMethod().isEmpty())
            if (clazz.isEnum())
                MethodHandleHelper.lookup().findVirtual(clazz, annotation.invokeMethod(), MethodType.methodType(void.class))
                        .invoke(MethodHandleHelper.lookup().findStaticVarHandle(clazz, "INSTANCE", clazz).get());
            else
                MethodHandleHelper.lookup().findStatic(clazz, annotation.invokeMethod(), MethodType.methodType(void.class)).invoke();
    }
    
    static {
        TransformerManager.Patcher.needRetransformFilter().add(target -> target
                .filter(retransformTarget -> TransformerManager.runtime().context() != null)
                .filter(retransformTarget -> retransformTarget.isAnnotationPresent(Preload.class))
                .map(_ -> Boolean.FALSE));
    }
    
}
