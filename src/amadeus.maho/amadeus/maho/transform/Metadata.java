package amadeus.maho.transform;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.tree.AnnotationNode;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.annotation.AnnotationHandler;
import amadeus.maho.util.bytecode.ASMHelper;

@Target({ ElementType.TYPE, ElementType.FIELD, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Metadata {
    
    interface Guard {
        
        String METADATA_DESCRIPTOR = ASMHelper.classDesc(Metadata.class);
        
        static boolean missing(final List<AnnotationNode> annotationNodes, final Class<?> identity) {
            final String name = identity.getName();
            final AnnotationNode metadata = ASMHelper.findAnnotationNode(annotationNodes, Metadata.class) ?? new AnnotationNode(METADATA_DESCRIPTOR).let(annotationNodes::add);
            final Map<Object, Object> map = AnnotationHandler.valueToMap(metadata.values);
            final @Nullable List<Object> value = (List<Object>) map["value"];
            if (value != null) {
                if (value[name])
                    return false;
                value += name;
            } else
                // noinspection Convert2Diamond
                metadata.values = new LinkedList<Object>() << "value" << (new LinkedList<>() << name);
            return true;
        }
        
    }
    
    String[] value() default { };
    
}
