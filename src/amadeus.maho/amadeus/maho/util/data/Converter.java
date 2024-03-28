package amadeus.maho.util.data;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Stream;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.dynamic.ClassLocal;
import amadeus.maho.util.dynamic.LookupHelper;
import amadeus.maho.util.runtime.MethodHandleHelper;
import amadeus.maho.util.runtime.ReflectionHelper;
import amadeus.maho.util.runtime.TypeHelper;
import amadeus.maho.util.tuple.Tuple;
import amadeus.maho.util.tuple.Tuple3;

public interface Converter {
    
    @Getter
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    class ArrayAgent {
        
        public static final MethodHandle append = LookupHelper.<LinkedList, Object>methodHandleV2(List::add);
        
        public static final Map<Class<?>, Supplier<Collection>> defaultSupplier = new HashMap<>(Map.of(
                Set.class, HashSet::new,
                SortedSet.class, TreeSet::new,
                NavigableSet.class, TreeSet::new,
                Collection.class, ArrayList::new,
                Queue.class, LinkedList::new,
                Deque.class, LinkedList::new
        ));
        
        Type genericType;
        
        Class<?> erasedType = TypeHelper.erase(genericType);
        
        Type innerGenericType = inferInnerType(genericType);
        
        Class<?> erasedInnerType = TypeHelper.erase(innerGenericType);
        
        @Default
        List<Object> list = new LinkedList<>();
        
        protected Type inferInnerType(final Type genericType) = switch (genericType) {
            case GenericArrayType genericArrayType   -> genericArrayType.getGenericComponentType();
            case ParameterizedType parameterizedType -> parameterizedType.getActualTypeArguments()[0];
            default                                  -> throw new IllegalStateException(STR."Unexpected value: \{genericType}");
        };
        
        public void append(final String value) = set(list, erasedInnerType, append, value);
        
        public void append(final Object value) = list += value;
        
        public Object result() {
            if (erasedType.isInstance(list))
                return list;
            if (erasedType.isArray())
                return list.toArray(TypeHelper.arrayConstructor(erasedType));
            return (defaultSupplier[erasedType]?.get() ?? (Collection) erasedType.tryInstantiation()).let(it -> it.addAll(list));
        }
        
        public static Object agent(final @Nullable Object instance = null, final Type type) {
            final Class<?> erasedType = TypeHelper.erase(type);
            return erasedType.isArray() || Collection.class.isAssignableFrom(erasedType) ? new ArrayAgent(type) : instance;
        }
        
    }
    
    @Getter
    @SneakyThrows
    ClassLocal<Map<String, Tuple3<Field, MethodHandle, MethodHandle>>> handle = {
            it -> {
                if (it.isPrimitive())
                    throw new IllegalArgumentException(STR."Class: \{it}");
                if (it == Object.class)
                    return Map.of();
                final MethodHandles.Lookup lookup = MethodHandleHelper.lookup();
                final LinkedHashMap<String, Tuple3<Field, MethodHandle, MethodHandle>> fieldHandles = { };
                final LinkedList<Class<?>> types = { };
                Class<?> owner = it;
                do {
                    types.push(owner);
                } while ((owner = owner.getSuperclass()) != Object.class);
                types.stream()
                        .map(Class::getDeclaredFields)
                        .flatMap(Stream::of)
                        .filter(ReflectionHelper.noneMatch(Modifier.STATIC | Modifier.TRANSIENT))
                        .peek(field -> {
                            if (fieldHandles.containsKey(field.getName()))
                                throw new UnsupportedOperationException(STR."Duplicate key: \{field.getName()}");
                        })
                        .forEach(field -> fieldHandles.putIfAbsent(field.getName(), Tuple.tuple(field, lookup.unreflectSetter(field), lookup.unreflectGetter(field))));
                return Collections.unmodifiableMap(fieldHandles);
            }, true
    };
    
    @SneakyThrows
    static void visitKeyValue(final Object layer, final String key, final String value) {
        final @Nullable Tuple3<Field, MethodHandle, MethodHandle> tuple = handle()[layer.getClass()][key];
        if (tuple != null)
            set(layer, tuple.v1.getType(), tuple.v2, value);
    }
    
    @SneakyThrows
    static void set(final Object layer, final Class<?> type, final MethodHandle setter, final String value) {
        if (value.equals("null"))
            setter.invoke(layer, null);
        else {
            if (type == String.class)
                setter.invoke(layer, value);
            else if (type == boolean.class || type == Boolean.class)
                setter.invoke(layer, Boolean.parseBoolean(value));
            else if (type == char.class || type == Character.class) {
                if (value.length() == 1)
                    setter.invoke(layer, value.charAt(0));
                else
                    throw new IllegalArgumentException(STR."Try set invalid type: \{type}, to: \{value}");
            } else {
                final String newValue = value.replace("_", "");
                if (type == float.class || type == Float.class)
                    setter.invoke(layer, Float.parseFloat(newValue));
                else if (type == double.class || type == Double.class)
                    setter.invoke(layer, Double.parseDouble(newValue));
                else {
                    final int
                            radix = newValue.startsWith("0x") ? 16 : newValue.startsWith("0b") ? 2 : newValue.startsWith("0") ? 8 : 10,
                            start = newValue.startsWith("0x") ? 2 : newValue.startsWith("0b") ? 2 : newValue.startsWith("0") ? 1 : 0;
                    if (type == int.class || type == Integer.class)
                        setter.invoke(layer, Integer.parseInt(newValue.substring(start), radix));
                    else if (type == long.class || type == Long.class)
                        setter.invoke(layer, Long.parseLong(newValue.substring(start), radix));
                    else if (type == byte.class || type == Byte.class)
                        setter.invoke(layer, Byte.parseByte(newValue.substring(start), radix));
                    else if (type == short.class || type == Short.class)
                        setter.invoke(layer, Short.parseShort(newValue.substring(start), radix));
                    else
                        throw new IllegalArgumentException(STR."Try set invalid type: \{type}, to: \{newValue}");
                }
            }
        }
    }
    
    void read(String source, Object data, final Type type = data.getClass(), String debugInfo) throws IOException;
    
    void read(InputStream input, Object data, final Type type = data.getClass(), String debugInfo, final Charset charset = StandardCharsets.UTF_8) throws IOException;
    
    default void read(final Path path, final Object data, final Type type = data.getClass(), final String debugInfo, final Charset charset = StandardCharsets.UTF_8) throws IOException
            = read(Files.newInputStream(path), data, type, debugInfo, charset);
    
    void write(OutputStream output, @Nullable Object data, final Charset charset = StandardCharsets.UTF_8) throws IOException;
    
    default void write(final Path path, @Nullable final Object data, final Charset charset = StandardCharsets.UTF_8) throws IOException = write(Files.newOutputStream(path), data, charset);
    
    @SneakyThrows
    default String write(final @Nullable Object data, final Charset charset = StandardCharsets.UTF_8) = new ByteArrayOutputStream(1 << 12).let(output -> write(output, data, charset)).toString(charset);
    
}
