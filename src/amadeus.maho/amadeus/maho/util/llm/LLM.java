package amadeus.maho.util.llm;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.util.function.Consumer;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.dynamic.DynamicObject;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LLM {
    
    @Target({ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE, ElementType.FIELD})
    @Retention(RetentionPolicy.RUNTIME)
    @interface Description {
        
        String value();
        
    }
    
    interface Accessor {
        
        static void ifDescriptionPresent(final AnnotatedElement element, final Consumer<String> consumer) {
            final @Nullable Description description = element.getAnnotation(Description.class);
            if (description != null)
                consumer.accept(description.value());
        }
        
    }
    
    @FunctionalInterface
    interface ParametersProvider extends Consumer<DynamicObject> {
        
        LLM.ParametersProvider empty = _ -> { };
        
    }
    
    String model() default "";
    
    Class<? extends ParametersProvider> parameter() default ParametersProvider.class;
    
    String value() default "";
    
}
