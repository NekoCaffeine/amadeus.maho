package amadeus.maho.util.misc;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.runtime.ObjectHelper;
import amadeus.maho.util.runtime.ReflectionHelper;

public class ConstantLookup {
    
    protected Map<Field, Object> constantMapping = new HashMap<>();
    
    public Map<Field, Object> constantMapping() = constantMapping;
    
    public self recording(final Predicate<Field> predicate = _ -> true, final Class<?>... classes) {
        Stream.of(classes).forEach(clazz -> recording(predicate, clazz));
        return this;
    }
    
    @SneakyThrows
    public self recording(final Predicate<Field> predicate, final Class<?> clazz) {
        Stream.of(clazz.getDeclaredFields())
                .filter(field -> !ConstantLookup.class.isAssignableFrom(field.getType()))
                .filter(ReflectionHelper.allMatch(ReflectionHelper.STATIC | ReflectionHelper.FINAL))
                .filter(predicate)
                .forEach(field -> constantMapping.put(field, ReflectionHelper.setAccessible(field).get(null)));
        return this;
    }
    
    public Stream<Field> lookupFields(final @Nullable Object value) = constantMapping().entrySet().stream()
            .filter(e -> ObjectHelper.equals(e.getValue(), value))
            .map(Map.Entry::getKey);
    
    public Stream<String> lookupFieldNames(final @Nullable Object value) = lookupFields(value).map(Field::getName);
    
    public String lookupFieldName(final @Nullable Object value) = lookupFieldNames(value).findFirst().orElse("?");
    
    public String lookupFieldName(final @Nullable Object value, final Predicate<String> predicate) = lookupFieldNames(value).filter(predicate).findFirst().orElse("?");
    
    public @Nullable Object lookupValue(final Predicate<Field> predicate) = constantMapping.entrySet().stream()
            .filter(e -> predicate.test(e.getKey()))
            .findAny()
            .map(Map.Entry::getValue)
            .orElse(null);
    
    public @Nullable Object lookupValue(final String name) = lookupValue(field -> field.getName().equals(name));
    
}
