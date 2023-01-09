package amadeus.maho.intercept;

import java.lang.invoke.MethodType;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.TransformRange;

public interface TransformInterceptor extends Interceptor, TransformRange {
    
    @Setter
    @Getter
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    class Base<I extends Interceptor> implements TransformInterceptor {
        
        @Default
        @Nullable Supplier<I> supplier = null;
        
        final BiPredicate<ClassLoader, String> predicate;
        
        @Override
        public boolean isTarget(final @Nullable ClassLoader loader, final String name) = !name.contains("$Lambda$") && predicate().test(loader, name);
        
        @Nullable I interceptor() = supplier()?.get() ?? null;
        
        @Override
        public void enter(final Class<?> clazz, final String name, final MethodType methodType, final Object... args) = interceptor()?.enter(clazz, name, methodType, args);
        
        @Override
        public void exit() = interceptor()?.exit();
        
    }
    
}
