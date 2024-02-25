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
import amadeus.maho.util.concurrent.ConcurrentWeakIdentityHashMap;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OverrideMap {
    
    ConcurrentWeakIdentityHashMap<List<?>, ConcurrentWeakIdentityHashMap<Method, List<?>>> map = { };
    
    @Extension.Operator("GET")
    public <T> Function<Method, List<T>> with(final List<T> instances) = method -> (List<T>) map().computeIfAbsent(instances, it -> new ConcurrentWeakIdentityHashMap<>())
            .computeIfAbsent(method, base -> base == null ? instances : instances.stream().filter(instance -> {
                final @Nullable Method target = (Privilege) instance.getClass().getMethod0(base.getName(), base.getParameterTypes());
                return target != null && target.getDeclaringClass() != base.getDeclaringClass();
            }).toList());
    
}
