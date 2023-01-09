package amadeus.maho.util.annotation;

import java.lang.annotation.Annotation;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;

import amadeus.maho.core.Maho;
import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.dynamic.ClassLocal;
import amadeus.maho.util.dynamic.Wrapper;
import amadeus.maho.util.runtime.ArrayHelper;
import amadeus.maho.util.runtime.MethodHandleHelper;
import amadeus.maho.util.runtime.TypeHelper;
import amadeus.maho.util.runtime.UnsafeHelper;
import amadeus.maho.vm.transform.mark.HotSpotJIT;

import static amadeus.maho.util.function.FunctionHelper.lazy;

@HotSpotJIT
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AnnotationHandler<T> {
    
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
    public static class BaseAnnotation implements Annotation {
        
        @Getter
        @SneakyThrows
        private static final VarHandle handlerHandle = MethodHandleHelper.lookup().findVarHandle(BaseAnnotation.class, "handler", AnnotationHandler.class);
        
        @Getter
        AnnotationHandler<?> handler;
        
        @SneakyThrows
        public static <T> AnnotationHandler<T> handler(final Annotation annotation) = (AnnotationHandler<T>) handlerHandle().get(annotation);
        
        @SneakyThrows
        public static void handler(final Annotation annotation, final AnnotationHandler<?> handler) = handlerHandle().set(annotation, handler);
        
        @Override
        public Class<? extends Annotation> annotationType() = handler.annotationType();
        
        @Override
        public String toString() = handler.toStringImpl();
        
        @Override
        public boolean equals(final Object target) = handler.equalsImpl(target);
        
        @Override
        public int hashCode() = handler.hashCodeImpl();
        
    }
    
    @Getter
    private static final ClassLocal<Map<String, Object>> defaultValues = {
            type -> Maho.getClassNodeFromClassNonNull(type).methods.stream()
                    .filter(methodNode -> methodNode.annotationDefault != null)
                    .map(methodNode -> Map.entry(methodNode.name, methodNode.annotationDefault))
                    .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue))
    };
    
    // Used for early initialization to avoid failure, see TransformerManager
    public static void initDefaultValue(final Class<?> clazz) {
        defaultValues().get(clazz);
        Stream.of(clazz.getDeclaredMethods())
                .map(Method::getReturnType)
                .filter(Class::isAnnotation)
                .forEach(AnnotationHandler::initDefaultValue);
    }
    
    public static final Type TYPE_BASE_ANNOTATION = Type.getType(BaseAnnotation.class), TYPE_ANNOTATION_HANDLER = Type.getType(AnnotationHandler.class);
    
    public static final org.objectweb.asm.commons.Method lookupValue = { "lookupValue", ASMHelper.TYPE_OBJECT, new Type[]{ ASMHelper.TYPE_STRING } };
    
    public static final String HANDLER_NAME = "handler";
    
    private static final String skipMethodNames[] = Stream.of(Annotation.class, Object.class)
            .map(Class::getDeclaredMethods)
            .flatMap(Stream::of)
            .map(Method::getName)
            .distinct()
            .toArray(String[]::new);
    
    @Getter
    private static final ClassLocal<Class<?>> annotationWrapper = {
            type -> {
                final Wrapper<BaseAnnotation> wrapper = {
                        type.getClassLoader() ?? AnnotationHandler.class.getClassLoader(),
                        BaseAnnotation.class,
                        type.getClassLoader() == null ? BaseAnnotation.class : type,
                        type.getClassLoader() == null ? type.getCanonicalName().replace('.', '_') : "BaseAnnotation",
                        type
                };
                wrapper.inheritableUniqueSignatureMethods()
                        .filter(method -> method.getParameters().length == 0)
                        .filter(method -> !ArrayHelper.contains(skipMethodNames, method.getName()))
                        .map(wrapper::wrap)
                        .forEach(generator -> {
                            generator.loadThis();
                            generator.getField(TYPE_BASE_ANNOTATION, HANDLER_NAME, TYPE_ANNOTATION_HANDLER);
                            generator.push(generator.name);
                            generator.invokeVirtual(TYPE_ANNOTATION_HANDLER, lookupValue);
                            generator.broadCast(ASMHelper.TYPE_OBJECT, generator.returnType);
                            generator.returnValue();
                            generator.visitMaxs(2, 1);
                            generator.endMethod();
                        });
                return wrapper.defineHiddenWrapperClass();
            }
    };
    
    protected static <T> T wrapper(final Class<T> type) = UnsafeHelper.allocateInstance(annotationWrapper()[type]);
    
    public static <T extends Annotation> T make(final Class<T> type, final ClassLoader contextLoader = type.getClassLoader(), final @Nullable List<Object> objects) = make(type, contextLoader, valueToMap(objects));
    
    public static <T extends Annotation> T make(final Class<T> clazz, final ClassLoader contextLoader, final Map<String, Object> sourceMemberValues)
            = wrapper(clazz).let(it -> BaseAnnotation.handler(it, new AnnotationHandler<>(clazz, contextLoader, sourceMemberValues)));
    
    @Nullable
    public static <T extends Annotation> AnnotationHandler<T> asOneOfUs(final T object) = BaseAnnotation.handler(object);
    
    public static <K, V> Map<K, V> valueToMap(final @Nullable List<?> list) {
        if (list == null || list.size() == 0)
            return new HashMap<>();
        if (list.size() % 2 != 0)
            throw new RuntimeException("objects.size() % 2 != 0");
        final Map<K, V> result = new HashMap<>();
        K key = null;
        for (final Object obj : list)
            if (key == null)
                key = (K) obj;
            else {
                result[key] = (V) obj;
                key = null;
            }
        return result;
    }
    
    @Getter
    Class<? extends Annotation> annotationType;
    
    @Getter
    Class<T> type;
    
    @Getter
    ClassLoader contextLoader;
    
    Map<String, Object> sourceMemberValues;
    
    Map<String, Object> defaultMemberValues;
    
    Map<String, Supplier<Object>> memberValueGetters;
    
    @Getter
    AnnotationType annotationData;
    
    public Map<String, Object> sourceMemberValues() = new HashMap<>(sourceMemberValues);
    
    public Stream<Map.Entry<String, Object>> values() = memberValueGetters.entrySet().stream().map(entry -> Map.entry(entry.getKey(), entry.getValue().get()));
    
    public static <T> String name(final Function<T, ?> getter) = getter.getClass().constantPool().lastMethodWithoutBoxed().getName();
    
    public Method method(final String name) = annotationData.members()[name];
    
    public <R> R defaultValue(final Function<T, R> getter) = defaultValue(name(getter));
    
    public <R> R defaultValue(final String name) = (R) defaultMemberValues[name];
    
    public boolean isDefault(final Function<T, Object> getter) = isDefault(name(getter));
    
    public boolean isDefault(final String name) = !isNotDefault(name);
    
    public boolean isNotDefault(final Function<T, Object> getter) = isNotDefault(name(getter));
    
    public boolean isNotDefault(final String name) = sourceMemberValues.containsKey(name);
    
    public <R> R lookupValue(final Function<T, R> getter) = lookupValue(name(getter));
    
    public <R> R lookupValue(final String name) {
        final @Nullable Supplier<Object> getter = memberValueGetters[name];
        return getter != null ? (R) getter.get() : (R) defaultValue(name);
    }
    
    public <R> @Nullable R lookupSourceValue(final Function<T, Object> getter) = lookupSourceValue(name(getter));
    
    public <R> @Nullable R lookupSourceValue(final String name) = (R) sourceMemberValues[name];
    
    public void changeValue(final Function<T, Object> getter, final Object value) = changeValue(name(getter), value);
    
    public void changeValue(final String name, final Object value) {
        sourceMemberValues[name] = value;
        memberValueGetters[name] = getter(value, method(name));
    }
    
    protected AnnotationHandler(final Class<T> type, final ClassLoader loader, final Map<String, Object> sourceMemberValues) {
        annotationType = findAnnotationClass(this.type = type);
        contextLoader = loader;
        this.sourceMemberValues = new ConcurrentHashMap<>(sourceMemberValues);
        annotationData = AnnotationType.instance(annotationType);
        memberValueGetters = sourceMemberValues.entrySet().stream()
                .collect(Collectors.toConcurrentMap(Map.Entry::getKey, entry -> getter(entry.getValue(), method(entry.getKey()))));
        defaultMemberValues = defaultValues()[annotationType].entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), getter(entry.getValue(), method(entry.getKey())).get()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
    
    @SneakyThrows
    protected Supplier<Object> getter(final Object value, final Method method) = switch (value) {
        case Supplier<?> supplier -> (Supplier<Object>) supplier;
        case Type type -> lazy(() -> ASMHelper.loadType(type, false, contextLoader));
        case Type[] types -> lazy(() -> ASMHelper.loadTypes(Stream.of(types), false, contextLoader));
        case AnnotationNode annotationNode -> lazy(() -> make((Class<? extends Annotation>) Class.forName(ASMHelper.sourceName(annotationNode.desc), false, contextLoader), contextLoader, annotationNode.values));
        case List<?> list -> Enum[].class.isAssignableFrom(method.getReturnType()) ?
                lazy(() -> ((List<String[]>) list).stream()
                        .map(args -> Enum.valueOf((Class<? extends Enum>) Class.forName(ASMHelper.sourceName(args[0]), true, contextLoader), args[1]))
                        .toArray(TypeHelper.arrayConstructor(method.getReturnType().getComponentType()))) :
                lazy(() -> ArrayHelper.toPrimitive(list.stream()
                        .map(element -> getter(element, method).get())
                        .toArray(lookupArrayMapper(method))));
        case String[] args && args.length == 2 && Enum.class.isAssignableFrom(method.getReturnType()) -> lazy(() -> Enum.valueOf((Class<? extends Enum>) Class.forName(ASMHelper.sourceName(args[0]), true, contextLoader), args[1]));
        case null, default -> () -> value;
    };
    
    protected IntFunction<Object[]> lookupArrayMapper(final Method method) {
        if (!method.getReturnType().isArray())
            throw new IncompatibleClassChangeError(method + " return type is not an array.");
        return size -> (Object[]) Array.newInstance(TypeHelper.boxClass(method.getReturnType().getComponentType()), size);
    }
    
    public static Class<? extends Annotation> findAnnotationClass(final Class<?> clazz) {
        if (!clazz.isInterface())
            throw new IllegalArgumentException(clazz.getName());
        if (isAnnotationClass(clazz))
            return (Class<? extends Annotation>) clazz;
        Class<?> result = null;
        final Class<?>[] interfaces = clazz.getInterfaces();
        for (final Class<?> i : interfaces) {
            final Class<?> temp = findAnnotationClass(i);
            if (temp != null)
                if (result == null)
                    result = temp;
                else
                    throw new IllegalArgumentException(clazz.getName());
        }
        if (result != null)
            return (Class<? extends Annotation>) result;
        throw new IllegalArgumentException(clazz.getName());
    }
    
    public static boolean isAnnotationClass(final Class<?> clazz) = clazz.isAnnotation() && List.of(clazz.getInterfaces()).contains(Annotation.class);
    
    @SneakyThrows
    protected boolean equalsImpl(final Object obj) {
        if (this == obj)
            return true;
        if (!annotationType().isInstance(obj))
            return false;
        final @Nullable AnnotationHandler<?> handler = asOneOfUs((Annotation) obj);
        return annotationData().members().values().stream().allMatch(method -> ArrayHelper.deepEquals(lookupValue(method.getName()), handler != null ? handler.lookupValue(method.getName()) : method.invoke(obj)));
    }
    
    protected String toStringImpl() = "@%s(%s)".formatted(type().getName(), values().map(entry -> "%s = %s".formatted(entry.getKey(), entry.getValue())).collect(Collectors.joining(", ")));
    
    protected int hashCodeImpl() = new int[]{ 0 }.let(it -> values().forEach(entry -> it[0] = 127 * entry.getKey().hashCode() ^ ArrayHelper.deepHashCode(entry.getValue())))[0];
    
}
