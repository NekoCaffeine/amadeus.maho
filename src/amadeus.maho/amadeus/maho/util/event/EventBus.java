package amadeus.maho.util.event;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Extension;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.concurrent.ConcurrentWeakIdentityHashMap;
import amadeus.maho.util.dynamic.LambdaHelper;
import amadeus.maho.util.function.FunctionHelper;
import amadeus.maho.util.runtime.MethodHandleHelper;
import amadeus.maho.util.runtime.ReflectionHelper;
import amadeus.maho.util.runtime.TypeHelper;
import amadeus.maho.util.type.TypeInferer;
import amadeus.maho.util.type.TypeToken;

@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED)
public class EventBus {
    
    final ConcurrentWeakIdentityHashMap<Class<? extends Event>, EventDispatcher> dispatcherLocal = { };
    
    final ConcurrentHashMap<Object, List<MethodHandle>> registeredMapping = { };
    
    @Setter
    @Getter
    private Consumer<Throwable> throwableHandler = FunctionHelper::rethrow;
    
    protected <E extends Event> EventDispatcher lookupEventDispatcher(final Class<E> eventType) = dispatcherLocal.computeIfAbsent(eventType, it -> {
        final Class<? extends Event> parentType = (Class<? extends Event>) eventType.getSuperclass();
        final @Nullable EventDispatcher parent = isBaseEventType(parentType) ? null : lookupEventDispatcher(parentType);
        return new EventDispatcher(parent, eventType, makeDynamicThrowableHandler());
    });
    
    @Extension.Operator(">>")
    public <E extends Event> E push(final E event) {
        Class<? extends Event> eventType = event.getClass();
        do {
            final @Nullable EventDispatcher dispatcher = dispatcherLocal[eventType];
            if (dispatcher != null) {
                dispatcher.dispatch(event);
                break;
            }
            eventType = (Class<? extends Event>) eventType.getSuperclass();
        } while (!isBaseEventType(eventType));
        return event;
    }
    
    @Extension.Operator("+=")
    public boolean register(final Object target) {
        final boolean p_result[] = { false };
        registeredMapping.computeIfAbsent(target, it -> {
            p_result[0] = true;
            final List<EventDispatcher.OrdinalWrapper> listeners = it instanceof Class<?> clazz ? analyzeStatic(clazz) : analyzeInstance(target);
            listeners.forEach(this::addListenerHandle);
            return listeners.stream().map(wrapper -> wrapper.handle).toList();
        });
        return p_result[0];
    }
    
    @Extension.Operator("-=")
    public boolean unregister(final Object target) {
        final @Nullable List<MethodHandle> listeners = registeredMapping.remove(target);
        if (listeners == null)
            return false;
        listeners.forEach(listener -> dispatcherLocal.get(listener.type().parameterType(0))?.removeListener(listener));
        return true;
    }
    
    public <T extends Event> boolean addListener(final Consumer<T> listener, final Listener.Ordinal ordinal = Listener.Ordinal.NORMAL, final boolean ignoreCanceled = false) = new boolean[]{ false }
            .let(p_result -> registeredMapping.computeIfAbsent(listener, it -> {
                p_result[0] = true;
                final Class<? extends Event> eventType = TypeInferer.infer(TypeToken.<T, Consumer<T>>locate(), listener.getClass()).erasedType();
                if (!Event.class.isAssignableFrom(eventType))
                    throw new IllegalArgumentException("The listener method can only and must have an argument of target Event.\n" + eventType);
                MethodHandle handle = LambdaHelper.lookupFunctionalMethodHandleAndBind(it);
                handle = MethodHandles.explicitCastArguments(handle, MethodType.methodType(void.class, eventType));
                if (ignoreCanceled)
                    handle = checkCanceled(handle);
                addListenerHandle(new EventDispatcher.OrdinalWrapper(ordinal, handle));
                return List.of(handle);
            }))[0];
    
    protected <E extends Event> void addListenerHandle(final EventDispatcher.OrdinalWrapper wrapper) = lookupEventDispatcher((Class<E>) wrapper.handle.type().parameterType(0)).addListener(wrapper);
    
    protected List<EventDispatcher.OrdinalWrapper> analyzeStatic(final Class<?> clazz) = Stream.of(clazz.getDeclaredMethods())
            .filter(ReflectionHelper.anyMatch(ReflectionHelper.STATIC))
            .map(method -> toOrdinalWrapper(method, null))
            .nonnull()
            .toList();
    
    protected List<EventDispatcher.OrdinalWrapper> analyzeInstance(final Object instance) = Stream.of(instance.getClass().getDeclaredMethods())
            .filter(ReflectionHelper.noneMatch(ReflectionHelper.STATIC))
            .map(method -> toOrdinalWrapper(method, instance))
            .nonnull()
            .toList();
    
    @SneakyThrows
    protected @Nullable EventDispatcher.OrdinalWrapper toOrdinalWrapper(final Method method, final @Nullable Object instance) {
        final Listener listener = method.getAnnotation(Listener.class);
        if (listener == null)
            return null;
        if (method.getParameterCount() != 1 || !Event.class.isAssignableFrom(method.getParameterTypes()[0]))
            throw new IllegalArgumentException("The listener method can only and must have an argument of target Event.\n" + method.getDeclaringClass() + method);
        if (method.getReturnType() != void.class)
            throw new IllegalArgumentException("The return target of the listener method must be void.\n" + method.getDeclaringClass() + method);
        MethodHandle handle = MethodHandleHelper.lookup().unreflect(method);
        if (ReflectionHelper.noneMatch(method, ReflectionHelper.STATIC))
            handle = handle.bindTo(instance);
        if (!listener.ignoreCanceled())
            handle = checkCanceled(handle);
        return { listener.value(), processMethodHandle(handle, method) };
    }
    
    protected MethodHandle processMethodHandle(final MethodHandle handle, final Method method) {
        final Class<? extends Event> eventType = (Class<? extends Event>) handle.type().parameterType(0);
        if (eventType.getTypeParameters().length > 0) {
            final Type type = method.getParameters()[0].getParameterizedType();
            if (type instanceof ParameterizedType parameterizedType) {
                final @Nullable MethodHandle filter = TypeHelper.typeParametersFilter(eventType, parameterizedType.getActualTypeArguments());
                if (filter != null)
                    return MethodHandles.guardWithTest(MethodHandles.explicitCastArguments(filter, handle.type().changeReturnType(boolean.class)), handle, MethodHandles.empty(handle.type()));
            }
        }
        return handle;
    }
    
    protected MethodHandle checkCanceled(final MethodHandle handle)
            = MethodHandles.guardWithTest(MethodHandles.explicitCastArguments(CHECK_CANCELED, handle.type().changeReturnType(boolean.class)), handle, MethodHandles.empty(handle.type()));
    
    public static boolean checkCanceled(final Event event) = !(event instanceof Event.Cancellable cancellable) || !cancellable.cancel();
    
    public static boolean isBaseEventType(final Class<? extends Event> eventType) = eventType.isAnnotationPresent(Event.Base.class);
    
    protected MethodHandle makeDynamicThrowableHandler() = MethodHandles.foldArguments(HANDLE_THROWABLE, GET_THROWABLE_HANDLER.bindTo(this));
    
    @SneakyThrows
    public static final MethodHandle
            GET_THROWABLE_HANDLER = MethodHandleHelper.lookup().findGetter(EventBus.class, "throwableHandler", Consumer.class),
            HANDLE_THROWABLE      = MethodHandles.explicitCastArguments(MethodHandleHelper.lookup().findVirtual(Consumer.class, "accept", MethodType.methodType(void.class, Object.class)),
                    MethodType.methodType(void.class, Consumer.class, Throwable.class)),
            CHECK_CANCELED        = MethodHandleHelper.lookup().findStatic(EventBus.class, "checkCanceled", MethodType.methodType(boolean.class, Event.class));
    
}
