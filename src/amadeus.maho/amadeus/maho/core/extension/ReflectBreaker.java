package amadeus.maho.core.extension;

import java.lang.reflect.AccessibleObject;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;

import jdk.internal.reflect.Reflection;
import jdk.internal.reflect.UnsafeFieldAccessorImpl;

import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.transform.AOTTransformer;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformMetadata;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.dynamic.CallerContext;

@SneakyThrows
public class ReflectBreaker {
    
    @TransformProvider
    private interface Layer {
        
        @Hook(metadata = @TransformMetadata(aotLevel = AOTTransformer.Level.RUNTIME))
        private static Hook.Result checkAccess(final AccessibleObject $this, final Class<?> caller, final Class<?> memberClass, final Class<?> targetClass, final int modifiers)
                = Hook.Result.falseToVoid(accessFlag() || breakModules.contains(caller.getModule()));
        
        @Hook(forceReturn = true, metadata = @TransformMetadata(aotLevel = AOTTransformer.Level.RUNTIME))
        private static void throwFinalFieldIllegalAccessException(final UnsafeFieldAccessorImpl $this, final String attemptedType, final String attemptedValue) { }
        
    }
    
    private static final Set<Module> breakModules = Collections.newSetFromMap(new WeakHashMap<>());
    
    @Getter
    @Setter
    private static volatile boolean accessFlag;
    
    public static void jailbreak() {
        accessFlag(true);
        resetReflection();
    }
    
    public static synchronized void doBreak(final Module... modules = CallerContext.caller().getModule()) = breakModules *= List.of(modules);
    
    public static synchronized void resetReflection(final BiConsumer<Map<Class<?>, Set<String>>, Map<Class<?>, Set<String>>> consumer = (fieldFilterMap, methodFilterMap) -> {
        fieldFilterMap.clear();
        methodFilterMap.clear();
    }) {
        final HashSet<Class<?>> set = { };
        final Map<Class<?>, Set<String>> fieldFilterMap = (Privilege) Reflection.fieldFilterMap, methodFilterMap = (Privilege) Reflection.methodFilterMap;
        set *= fieldFilterMap.keySet();
        set *= methodFilterMap.keySet();
        consumer.accept(fieldFilterMap, methodFilterMap);
        set *= fieldFilterMap.keySet();
        set *= methodFilterMap.keySet();
        set.forEach(clazz -> (Privilege) (clazz.reflectionData = null));
    }
    
}
