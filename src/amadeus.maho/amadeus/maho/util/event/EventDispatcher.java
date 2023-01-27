package amadeus.maho.util.event;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.inspection.Nullable;

@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
public class EventDispatcher {
    
    @ToString
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
    public static final class OrdinalWrapper implements Comparable<OrdinalWrapper> {
        
        Listener.Ordinal ordinal;
        MethodHandle     handle;
        
        @Override
        public int compareTo(final self target) = ordinal.ordinal() - target.ordinal.ordinal();
        
    }
    
    @Getter
    @Nullable EventDispatcher parent;
    @Getter
    MethodHandle throwableHandler;
    
    MutableCallSite callChain;
    
    MethodHandle methodHandle;
    
    ConcurrentLinkedQueue<OrdinalWrapper> listeners = { };
    
    public EventDispatcher(final @Nullable EventDispatcher parent, final Class<? extends Event> eventType, final MethodHandle throwableHandler) {
        this.parent = parent;
        this.throwableHandler = throwableHandler;
        callChain = { MethodType.methodType(void.class, eventType) };
        callChain.setTarget(MethodHandles.empty(callChain.type()));
        MethodHandle dynamicInvoker = callChain.dynamicInvoker();
        if (parent() != null)
            dynamicInvoker = MethodHandles.foldArguments(dynamicInvoker, MethodHandles.explicitCastArguments(parent().methodHandle(), callChain.type()));
        methodHandle = MethodHandles.explicitCastArguments(dynamicInvoker, dynamicInvoker.type().changeParameterType(0, Event.class));
    }
    
    public Stream<OrdinalWrapper> listeners() = listeners.stream();
    
    public Class<? extends Event> eventType() = (Class<? extends Event>) methodHandle.type().parameterType(0);
    
    public MethodHandle methodHandle() = methodHandle;
    
    public void addListener(final OrdinalWrapper wrapper) {
        listeners += wrapper;
        rebuildCallChain();
    }
    
    public void addListener(final Listener.Ordinal ordinal, final MethodHandle listener) = addListener(new OrdinalWrapper(ordinal, listener));
    
    public boolean removeListener(final MethodHandle listener) { try { return listeners.removeIf(wrapper -> wrapper.handle == listener); } finally { rebuildCallChain(); } }
    
    public synchronized void rebuildCallChain() {
        listeners.sort();
        final MethodHandle p_result[] = { null };
        listeners().forEach(wrapper -> p_result[0] = p_result[0] == null ? catchThrowable(wrapper.handle) : MethodHandles.foldArguments(catchThrowable(wrapper.handle), p_result[0]));
        updateCallChainTarget(p_result[0]);
    }
    
    protected void updateCallChainTarget(final MethodHandle handle) = callChain.setTarget(handle);
    
    protected MethodHandle catchThrowable(final MethodHandle handle) = MethodHandles.catchException(handle, Throwable.class, throwableHandler);
    
    @SneakyThrows
    public void dispatch(final Event event) = methodHandle.invokeExact(event);
    
}
