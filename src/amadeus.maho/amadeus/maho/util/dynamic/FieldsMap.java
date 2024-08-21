package amadeus.maho.util.dynamic;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.util.control.LinkedIterator;
import amadeus.maho.util.runtime.MethodHandleHelper;
import amadeus.maho.util.runtime.ReflectionHelper;

public interface FieldsMap {
    
    @EqualsAndHashCode(reverse = true)
    record Info(@EqualsAndHashCode.Mark Field field, VarHandle handle, MethodHandle getter, MethodHandle setter) {
        
        @Override
        public String toString() = field().toString();
        
        @SneakyThrows
        public static Info of(final MethodHandles.Lookup lookup = MethodHandleHelper.lookup(), final Field field)
            = { field, lookup.unreflectVarHandle(field), lookup.unreflectGetter(field), lookup.unreflectSetter(field) };
        
    }
    
    @Getter
    @SneakyThrows
    ClassLocal<Map<String, Info>>
            fieldsMapLocal = { it -> { return fieldsInfo(it, true); }, true },
            uniqueFieldsMapLocal = { it -> { return fieldsInfo(it, false); }, false };
    
    private static Map<String, Info> fieldsInfo(final Class<?> it, final boolean allowDuplicate) {
        if (it.isPrimitive())
            throw new IllegalArgumentException(STR."Class: \{it}");
        if (it == Object.class)
            return Map.of();
        final MethodHandles.Lookup lookup = MethodHandleHelper.lookup();
        final LinkedHashMap<String, Info> map = { };
        final Stream<Field> stream = LinkedIterator.<Class>of(Class::getSuperclass, it).stream(true)
                .map(Class::getDeclaredFields)
                .flatMap(Stream::of)
                .filter(ReflectionHelper.noneMatch(Modifier.STATIC | Modifier.TRANSIENT));
        (allowDuplicate ? stream : stream.peek(field -> {
            if (map.containsKey(field.getName()))
                throw new UnsupportedOperationException(STR."Duplicate key: \{field.getName()}");
        })).forEach(field -> map.putIfAbsent(field.getName(), Info.of(lookup, field)));
        return Collections.unmodifiableMap(map);
    }
    
}
