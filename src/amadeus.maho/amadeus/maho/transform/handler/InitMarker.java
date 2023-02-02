package amadeus.maho.transform.handler;

import java.lang.invoke.MethodType;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.transform.TransformerManager;
import amadeus.maho.transform.handler.base.marker.BaseMarker;
import amadeus.maho.transform.mark.Init;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.runtime.MethodHandleHelper;
import amadeus.maho.util.runtime.ReflectionHelper;

@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class InitMarker extends BaseMarker<Init> {
    
    @Override
    @SneakyThrows
    public void onMark(final TransformerManager.Context context) = manager.addSetupCallback(() -> {
        TransformerManager.transform("init", ASMHelper.sourceName(sourceClass.name));
        init(annotation, Class.forName(ASMHelper.sourceName(sourceClass.name), false, contextClassLoader()));
    });
    
    @SneakyThrows
    public static void init(final Init annotation, final Class<?> clazz) {
        if (annotation.initialized())
            ~clazz;
        if (annotation.reflectionData())
            ReflectionHelper.initReflectionData(clazz);
        if (!annotation.invokeMethod().isEmpty())
            try {
                MethodHandleHelper.lookup().findVirtual(clazz, annotation.invokeMethod(), MethodType.methodType(void.class)).invoke(clazz.defaultInstance());
            } catch (final IncompatibleClassChangeError | ReflectiveOperationException e) {
                MethodHandleHelper.lookup().findStatic(clazz, annotation.invokeMethod(), MethodType.methodType(void.class)).invoke();
            }
    }
    
}
