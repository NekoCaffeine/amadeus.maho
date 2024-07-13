package amadeus.maho.util.control;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Function;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Extension;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.container.MapTable;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OverrideMap {
    
    MapTable<List<?>, Method, List<?>> map = MapTable.ofConcurrentWeakIdentityHashMapTable();
    
    @Extension.Operator("GET")
    public <T> Function<Method, List<T>> with(final List<T> instances) = method -> (List<T>) map()[instances].computeIfAbsent(method, base -> instances.stream().filter(instance -> {
        final @Nullable Method target = (Privilege) instance.getClass().getMethod0(base.getName(), base.getParameterTypes());
        return target != null && target.getDeclaringClass() != base.getDeclaringClass();
    }).toList());
    
}
