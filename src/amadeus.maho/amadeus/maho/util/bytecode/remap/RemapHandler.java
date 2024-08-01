package amadeus.maho.util.bytecode.remap;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.bytecode.ASMHelper;

public interface RemapHandler {
    
    String PRIMITIVE_TYPE_PREFIX = "~";
    
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
    class ASMRemapper extends Remapper {
        
        RemapHandler remapHandler;
        
        public Type mapType(final Type type) = switch (type.getSort()) {
            case Type.ARRAY  -> Type.getType("[".repeat(type.getDimensions()) + mapType(type.getElementType()).getDescriptor());
            case Type.METHOD -> Type.getMethodType(mapMethodDesc(type.getDescriptor()));
            default          -> {
                final String internalName = type.getSort() == Type.OBJECT ? type.getInternalName() : PRIMITIVE_TYPE_PREFIX + type.getInternalName(), result = map(internalName);
                yield result.startsWith(PRIMITIVE_TYPE_PREFIX) ? Type.getType(result.substring(1)) : Type.getObjectType(result);
            }
        };
        
        @Override
        public String mapDesc(final String descriptor) = mapType(Type.getType(descriptor)).getDescriptor();
        
        @Override
        public @Nullable String mapType(final @Nullable String internalName) = internalName == null ? null : mapType(Type.getObjectType(internalName)).getInternalName();
        
        @Override
        public String mapMethodDesc(final String methodDescriptor) {
            if ("()V".equals(methodDescriptor))
                return methodDescriptor;
            final StringBuilder builder = { "(" };
            for (final Type argumentType : Type.getArgumentTypes(methodDescriptor))
                builder.append(mapType(argumentType).getDescriptor());
            final Type returnType = Type.getReturnType(methodDescriptor);
            if (returnType == Type.VOID_TYPE)
                builder.append(")V");
            else
                builder.append(')').append(mapType(returnType).getDescriptor());
            return builder.toString();
        }
        
        @Override
        public Object mapValue(final Object value) = switch (value) {
            case Type type                       -> mapType(type);
            case Handle handle                   -> new Handle(
                    handle.getTag(),
                    mapType(handle.getOwner()),
                    mapMethodName(handle.getOwner(), handle.getName(), handle.getDesc()),
                    handle.getTag() <= Opcodes.H_PUTSTATIC
                            ? mapDesc(handle.getDesc())
                            : mapMethodDesc(handle.getDesc()),
                    handle.isInterface());
            case ConstantDynamic constantDynamic -> {
                final int bootstrapMethodArgumentCount = constantDynamic.getBootstrapMethodArgumentCount();
                final Object remappedBootstrapMethodArguments[] = new Object[bootstrapMethodArgumentCount];
                for (int i = 0; i < bootstrapMethodArgumentCount; i++)
                    remappedBootstrapMethodArguments[i] = mapValue(constantDynamic.getBootstrapMethodArgument(i));
                final String descriptor = constantDynamic.getDescriptor();
                yield new ConstantDynamic(
                        mapInvokeDynamicMethodName(constantDynamic.getName(), descriptor),
                        mapDesc(descriptor),
                        (Handle) mapValue(constantDynamic.getBootstrapMethod()),
                        remappedBootstrapMethodArguments);
            }
            default                              -> value;
        };
        
        @Override
        public String mapInvokeDynamicMethodName(final String name, final String descriptor) = remapHandler.mapMethodName(".", name, descriptor);
        
        @Override
        public String mapMethodName(final String owner, final String name, final String descriptor) = remapHandler.mapMethodName(owner, name, descriptor);
        
        @Override
        public String mapFieldName(final String owner, final String name, final String descriptor) = remapHandler.mapFieldName(owner, name);
        
        @Override
        public String mapPackageName(final String name) {
            final String result = remapHandler.mapPackage(name);
            return result.equals(".") ? "" : result;
        }
        
        @Override
        public String mapModuleName(final String name) {
            final String result = remapHandler.mapPackage(name);
            return result.equals(".") ? "" : result;
        }
        
        @Override
        public String map(final String internalName) = remapHandler.mapInternalName(internalName);
        
    }
    
    class LocalNodeRemapper extends ClassRemapper {
        
        public LocalNodeRemapper(final ClassVisitor classVisitor, final Remapper remapper) = super(ASMHelper.asm_api_version, classVisitor, remapper);
        
        public String className() = className;
        
        public self className(final String value) {
            className = value;
            return this;
        }
        
    }
    
    // If it is empty, please use "." to represent it.
    // The result is the same.
    default String mapModule(final String name) = name;
    
    // If it is empty, please use "." to represent it.
    // The result is the same.
    default String mapPackage(final String name) = name;
    
    default String mapType(final String name) = name.contains(".") ? mapClassName(name) : mapInternalName(name);
    
    default String mapClassName(final String name) = ASMHelper.sourceName(mapInternalName(ASMHelper.className(name)));
    
    default String mapInternalName(final String name) = name;
    
    default String mapFieldName(final String owner, final String name) = name;
    
    default String mapMethodName(final String owner, final String name, final @Nullable String descriptor) = name;
    
    default ClassNode mapClassNode(final ClassNode node) {
        final ClassNode result = { };
        node.accept(new ClassRemapper(result, remapper()));
        return result;
    }
    
    default FieldNode mapFieldNode(final String owner, final FieldNode node) {
        final ClassNode result = { };
        node.accept(new LocalNodeRemapper(result, remapper()).className(owner));
        return result.fields.get(0);
    }
    
    default MethodNode mapMethodNode(final String owner, final MethodNode node) {
        final ClassNode result = { };
        node.accept(new LocalNodeRemapper(result, remapper()).className(owner));
        return result.methods.get(0);
    }
    
    default RemapHandler reverse() = this;
    
    default ASMRemapper remapper() = { this };
    
}
