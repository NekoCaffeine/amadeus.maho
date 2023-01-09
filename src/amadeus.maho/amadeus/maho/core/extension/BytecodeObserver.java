package amadeus.maho.core.extension;

import java.lang.instrument.UnmodifiableClassException;
import java.security.ProtectionDomain;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

import org.objectweb.asm.tree.ClassNode;

import amadeus.maho.core.Maho;
import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.ClassTransformer;
import amadeus.maho.transform.mark.base.Transformer;
import amadeus.maho.util.bytecode.context.TransformContext;
import amadeus.maho.util.concurrent.ConcurrentWeakIdentityHashMap;

@Transformer
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum BytecodeObserver implements ClassTransformer {
    
    @Getter
    instance;
    
    ConcurrentWeakIdentityHashMap<Class<?>, String> targets = { };
    
    @Getter
    CopyOnWriteArrayList<BiConsumer<Class<?>, ClassNode>> observers = { };
    
    @Override
    public boolean isTarget(final Class<?> clazz) = targets.containsKey(clazz);
    
    @Override
    public ClassNode transform(final TransformContext context, final @Nullable ClassNode node, final @Nullable ClassLoader loader, final @Nullable Class<?> clazz, final @Nullable ProtectionDomain domain) {
        if (clazz != null && node != null && targets.containsKey(clazz) && context instanceof TransformContext.WithSource source) {
            final @Nullable String cache = targets[clazz], now = source.md5();
            if (cache == null || !cache.equals(now)) {
                observers.forEach(consumer -> consumer.accept(clazz, node));
                targets[clazz] = now;
            }
        }
        return node;
    }
    
    private static final String PLACEHOLDER = "?";
    
    @SneakyThrows
    public void observe(final Class<?> target) throws UnmodifiableClassException {
        final String cache = targets.putIfAbsent(target, PLACEHOLDER);
        if (cache == null)
            Maho.instrumentation().retransformClasses(target);
        else if (cache == PLACEHOLDER)
            while (targets[target] == PLACEHOLDER)
                Thread.sleep(1L); // The spin interval of 1ms is reasonable compared to the overhead of `retransformClasses`.
    }
    
}
