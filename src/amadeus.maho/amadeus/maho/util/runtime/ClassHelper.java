package amadeus.maho.util.runtime;

import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Manifest;

import amadeus.maho.core.Maho;
import amadeus.maho.lang.Extension;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.dynamic.CallerContext;
import amadeus.maho.util.dynamic.ClassLoaderLocal;
import amadeus.maho.util.dynamic.ClassLocal;

@Extension
public interface ClassHelper {
    
    ClassLoaderLocal<ConcurrentHashMap<String, Class<?>>> fastLookupLocal = { loader -> new ConcurrentHashMap<>() };
    
    ClassLocal<Long> instanceSizeLocal = { clazz -> Maho.instrumentation().getObjectSize(UnsafeHelper.allocateInstance(clazz)) };
    
    static long instanceSize(final Class<?> clazz) = instanceSizeLocal[clazz];
    
    static void TILDE(final Class<?> $this) = UnsafeHelper.unsafe().ensureClassInitialized($this);
    
    @SneakyThrows
    static <T> T defaultInstance(final Class<T> $this) {
        ~$this;
        final MethodHandles.Lookup lookup = MethodHandleHelper.lookup();
        try {
            try {
                return (T) lookup.findStatic($this, "instance", MethodType.methodType($this)).invoke();
            } catch (final Throwable e) { return (T) lookup.findStaticVarHandle($this, "INSTANCE", $this).get(); }
        } catch (final Throwable ex) { return (T) lookup.findConstructor($this, MethodType.methodType(void.class)).invoke(); }
    }
    
    static @Nullable Class<?> tryLoad(final ClassLoader loader = CallerContext.caller().getClassLoader(), final String name, final boolean initialize = true) {
        try { return Class.forName(name, initialize, loader); } catch (final ClassNotFoundException e) { return null; }
    }
    
    static @Nullable Class<?> fastLookup(final @Nullable ClassLoader loader, final String name) = fastLookupLocal[loader].computeIfAbsent(name, it -> tryLoad(loader, name, true));
    
    @Getter
    @SneakyThrows
    ClassLocal<MethodHandle> noArgConstructorLocal = { type -> {
        try {
            return MethodHandleHelper.lookup().findConstructor(type, MethodType.methodType(void.class));
        } catch (NoSuchMethodException e) { return null; }
    }, true };
    
    @SneakyThrows
    static <T> T tryInstantiationOrAllocate(final Class<T> $this) {
        final @Nullable MethodHandle handle = noArgConstructorLocal[$this];
        return handle != null ? (T) handle.invoke() : UnsafeHelper.allocateInstanceOfType($this);
    }
    
    @SneakyThrows
    static <T> T tryInstantiation(final Class<T> $this) {
        final @Nullable MethodHandle handle = noArgConstructorLocal[$this];
        if (handle == null)
            throw DebugHelper.breakpointBeforeThrow(new UnsupportedOperationException());
        return (T) handle.invoke();
    }
    
    static String asDebugName(final Class<?> $this) {
        final String name = $this.getCanonicalName() ?? $this.getName();
        final int index = name.indexOf('/');
        return (index == -1 ? name : name.substring(0, index)).replace('.', '_');
    }
    
}
