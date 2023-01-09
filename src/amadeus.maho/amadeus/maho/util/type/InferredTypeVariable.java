package amadeus.maho.util.type;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

public record InferredTypeVariable<D extends GenericDeclaration>(TypeVariable<D> sourceType, Type bounds[]) implements InferredGenericType, TypeVariable<D> {
    
    @Override
    public Type[] getBounds() = bounds.clone();
    
    @Override
    public D getGenericDeclaration() = sourceType.getGenericDeclaration();
    
    @Override
    public String getName() = sourceType.getName();
    
    @Override
    public AnnotatedType[] getAnnotatedBounds() = sourceType.getAnnotatedBounds();
    
    @Override
    public <T extends Annotation> T getAnnotation(final Class<T> annotationClass) = sourceType.getAnnotation(annotationClass);
    
    @Override
    public Annotation[] getAnnotations() = sourceType.getAnnotations();
    
    @Override
    public Annotation[] getDeclaredAnnotations() = sourceType.getDeclaredAnnotations();
    
    @Override
    public <T extends Annotation> T[] getAnnotationsByType(final Class<T> annotationClass) = sourceType.getAnnotationsByType(annotationClass);
    
    @Override
    public <T extends Annotation> T[] getDeclaredAnnotationsByType(final Class<T> annotationClass) = sourceType.getDeclaredAnnotationsByType(annotationClass);
    
    @Override
    public <T extends Annotation> T getDeclaredAnnotation(final Class<T> annotationClass) = sourceType.getDeclaredAnnotation(annotationClass);
    
}
