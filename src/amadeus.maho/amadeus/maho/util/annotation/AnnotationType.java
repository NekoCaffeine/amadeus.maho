package amadeus.maho.util.annotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import amadeus.maho.util.dynamic.ClassLocal;

public record AnnotationType(
        Class<?> annotationClass,
        Map<String, Class<?>> memberTypes,
        Map<String, Object> memberDefaults,
        Map<String, Method> members,
        RetentionPolicy retention,
        boolean inherited,
        boolean repeatable,
        ElementType target[]) {
    
    private static final ElementType EMPTY_TARGET[] = { };
    
    private static final ClassLocal<AnnotationType> annotationTypeLocal = {
            annotationClass -> {
                if (!annotationClass.isAnnotation())
                    throw new IllegalArgumentException("Not an annotation type: " + annotationClass);
                final Method methods[] = annotationClass.getDeclaredMethods();
                return new AnnotationType(
                        annotationClass,
                        Stream.of(methods)
                                .map(method -> Map.entry(method.getName(), method.getReturnType()))
                                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)),
                        Stream.of(methods)
                                .filter(method -> method.getDefaultValue() != null)
                                .map(method -> Map.entry(method.getName(), method.getDefaultValue()))
                                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)),
                        Stream.of(methods)
                                .map(method -> Map.entry(method.getName(), method))
                                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)),
                        annotationClass.getAnnotation(Retention.class)?.value() ?? RetentionPolicy.CLASS,
                        annotationClass.getAnnotation(Inherited.class) != null,
                        annotationClass.getAnnotation(Repeatable.class) != null,
                        annotationClass.getAnnotation(Target.class)?.value() ?? EMPTY_TARGET
                );
            }
    };
    
    public static AnnotationType instance(final Class<? extends Annotation> annotationType) = annotationTypeLocal[annotationType];
    
    @Override
    public int hashCode() = annotationClass().hashCode();
    
    @Override
    public boolean equals(final Object object) = object instanceof AnnotationType annotationType && annotationType.annotationClass() == annotationClass();
    
}
