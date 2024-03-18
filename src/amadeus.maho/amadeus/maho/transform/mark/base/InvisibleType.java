package amadeus.maho.transform.mark.base;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

import org.objectweb.asm.Type;
import org.objectweb.asm.TypeReference;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeAnnotationNode;

import amadeus.maho.transform.TransformerManager;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.remap.RemapHandler;
import amadeus.maho.util.bytecode.type.TypePathFilter;

@Target({ ElementType.TYPE_USE, ElementType.TYPE_PARAMETER, ElementType.LOCAL_VARIABLE })
@Retention(RetentionPolicy.RUNTIME)
public @interface InvisibleType {
    
    class Transformer {
        
        public static void transform(final RemapHandler.ASMRemapper remapper, final ClassNode node) {
            node.fields.forEach(field -> transform(remapper, field));
            node.methods.forEach(method -> transform(remapper, method));
        }
        
        public static void transform(final RemapHandler.ASMRemapper remapper, final FieldNode node) = node.desc = transformType(remapper, node).getDescriptor();
    
        public static void transform(final RemapHandler.ASMRemapper remapper, final MethodNode node) = node.desc = transformType(remapper, node).getDescriptor();
    
        public static Type transformType(final RemapHandler.ASMRemapper remapper, final FieldNode node) = transformType(remapper, node.visibleTypeAnnotations, Type.getMethodType(node.desc));
        
        public static Type transformType(final RemapHandler.ASMRemapper remapper, final MethodNode node) = transformType(remapper, node.visibleTypeAnnotations, Type.getMethodType(node.desc));
        
        public static Type transformType(final RemapHandler.ASMRemapper remapper, final List<TypeAnnotationNode> typeAnnotationNodes, final Type methodType) {
            if (typeAnnotationNodes == null)
                return methodType;
            Type returnType = methodType.getReturnType();
            final Type argsTypes[] = methodType.getArgumentTypes();
            boolean changed = false;
            {
                final InvisibleType invisibleType = ASMHelper.findAnnotation(ASMHelper.filterTypeReference(typeAnnotationNodes,
                        TypeReference.newTypeReference(TypeReference.METHOD_RETURN), TypePathFilter.of()), InvisibleType.class, InvisibleType.class.getClassLoader());
                if (invisibleType != null) {
                    returnType = Type.getObjectType(ASMHelper.className(invisibleType.value()));
                    changed = true;
                }
            }
            for (int i = 0; i < argsTypes.length; i++) {
                final InvisibleType invisibleType = ASMHelper.findAnnotation(ASMHelper.filterTypeReference(typeAnnotationNodes,
                        TypeReference.newFormalParameterReference(i), TypePathFilter.of()), InvisibleType.class, InvisibleType.class.getClassLoader());
                if (invisibleType != null) {
                    argsTypes[i] = Type.getObjectType(ASMHelper.className(invisibleType.value()));
                    changed = true;
                }
            }
            return changed ? TransformerManager.runtime().remapper().mapType(Type.getMethodType(returnType, argsTypes)) : methodType;
        }
        
        public static String transformDescriptor(final RemapHandler.ASMRemapper remapper, final MethodNode node) = transformType(remapper, node).getDescriptor();
        
        public static String transformDescriptor(final RemapHandler.ASMRemapper remapper, final List<TypeAnnotationNode> typeAnnotationNodes, final String methodDescriptor)
                = transformType(remapper, typeAnnotationNodes, Type.getMethodType(methodDescriptor)).getDescriptor();
    
    }
    
    String value();
    
}
