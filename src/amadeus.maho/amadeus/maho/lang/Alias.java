package amadeus.maho.lang;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.util.stream.Stream;

import amadeus.maho.lang.inspection.Nullable;

@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface Alias {
    
    interface Helper {
        
        static <T> @Nullable Constructor<T> lookup(final Class<T> owner, final String... names) = (Constructor<T>) ~Stream.of(owner.getDeclaredConstructors()).filter(constructor -> match(constructor, names));
        
        static boolean match(final Constructor<?> constructor, final String... names) {
            final Parameter parameters[] = constructor.getParameters();
            if (parameters.length != names.length)
                return false;
            for (int i = 0; i < parameters.length; i++)
                if (!(names[i].equals(parameters[i].getName()) || names[i].equals(parameters[i].getAnnotation(Alias.class)?.value() ?? null)))
                    return false;
            return true;
        }
        
    }
    
    String value();
    
}
