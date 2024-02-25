package amadeus.maho.lang.javac.multithreaded.concurrent;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;

public interface ConcurrentHelper {
    
    @SneakyThrows
    static void overrideSuperFields(final Object instance) {
        final Class<?> instanceClass = instance.getClass(), superClass = instanceClass.getSuperclass();
        final Map<String, Field> instanceFields = collectFields(instanceClass), superFields = collectFields(superClass);
        instanceFields.forEach((name, instanceField) -> {
            final @Nullable Field superField = superFields[name];
            if (superField != null && superField.getType().isAssignableFrom(instanceField.getType())) {
                superField.setAccessible(true);
                superField.set(instance, instanceField.get(instance));
            }
        });
    }
    
    private static Map<String, Field> collectFields(final Class<?> instanceClass)
            = Stream.of(instanceClass.getDeclaredFields())
            .filter(field -> !Modifier.isStatic(field.getModifiers()))
            .collect(Collectors.toMap(Field::getName, Function.identity()));
    
}
