package amadeus.maho.transform.handler;

import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Set;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import amadeus.maho.core.extension.MagicAccessor;
import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.ClassTransformer;
import amadeus.maho.transform.TransformerManager;
import amadeus.maho.transform.handler.base.MethodTransformer;
import amadeus.maho.transform.mark.Proxy;
import amadeus.maho.transform.mark.base.InvisibleType;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.ComputeType;
import amadeus.maho.util.bytecode.context.TransformContext;
import amadeus.maho.util.bytecode.generator.MethodGenerator;
import amadeus.maho.util.bytecode.remap.RemapContext;

import static amadeus.maho.core.extension.DynamicLookupHelper.*;
import static org.objectweb.asm.Opcodes.*;

@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class ProxyTransformer extends MethodTransformer<Proxy> implements ClassTransformer.Limited, RemapContext {
    
    Type targetType;
    
    String selector, desc, realDesc;
    
    {
        selector = annotation.selector().isEmpty() ? sourceMethod.name : annotation.selector();
        realDesc = InvisibleType.Transformer.transformDescriptor(manager.remapper(), sourceMethod.visibleTypeAnnotations, sourceMethod.desc);
        if (handler.isNotDefault(Proxy::targetClass))
            targetType = handler.lookupSourceValue(Proxy::targetClass);
        else if (!annotation.target().isEmpty())
            targetType = Type.getObjectType(ASMHelper.className(annotation.target()));
        else
            switch (annotation.value()) {
                case INVOKEVIRTUAL,
                     INVOKESPECIAL,
                     INVOKEINTERFACE,
                     GETFIELD,
                     PUTFIELD,
                     INSTANCEOF    -> targetType = Type.getArgumentTypes(realDesc)[0];
                case NEW           -> targetType = Type.getReturnType(realDesc);
                default            -> throw new IllegalArgumentException("Unable to determine target class, missing required fields('targetClass' or 'target').");
            }
        desc = InvisibleType.Transformer.transformDescriptor(manager.remapper(), sourceMethod.visibleTypeAnnotations, mapDesc(sourceMethod.desc, annotation.value()));
    }
    
    @Override
    public ClassNode doTransform(final TransformContext context, final ClassNode node, final @Nullable ClassLoader loader, final @Nullable Class<?> clazz, final @Nullable ProtectionDomain domain) {
        for (final MethodNode methodNode : node.methods)
            if (methodNode.name.equals(sourceMethod.name) && methodNode.desc.equals(sourceMethod.desc)) {
                TransformerManager.transform("proxy", STR."\{ASMHelper.sourceName(node.name)}#\{methodNode.name}\{methodNode.desc}\n->  \{ASMHelper.sourceName(targetType.getInternalName())}#\{selector}\{desc}");
                ASMHelper.rollback(methodNode, contextMethodNode -> {
                    final MethodGenerator generator = MethodGenerator.fromMethodNode(contextMethodNode);
                    if (annotation.useHandle() && (!checkPrivilegedHolding(node) || handler.isNotDefault(Proxy::useHandle))) {
                        final String desc = InvisibleType.Transformer.transformType(manager.remapper(), sourceMethod).getDescriptor();
                        generator.loadArgs();
                        if (annotation.reverse())
                            generator.invokeDynamic(
                                    selector, sourceMethod.desc,
                                    makeSiteByNameWithBoot,
                                    annotation.value(),
                                    targetType.getClassName(),
                                    sourceMethod.desc.equals(desc) ? "" : desc
                            );
                        else
                            generator.invokeDynamic(
                                    selector, sourceMethod.desc,
                                    makeSiteByName,
                                    annotation.value(),
                                    submit(contextClassLoader()),
                                    targetType.getClassName(),
                                    sourceMethod.desc.equals(desc) ? "" : desc
                            );
                        ASMHelper.requestMinVersion(node, V1_8);
                    } else {
                        final Method targetMethod = { selector, desc };
                        final Type argsTypes[] = Type.getArgumentTypes(realDesc);
                        switch (annotation.value()) {
                            case INVOKESTATIC    -> {
                                generator.loadArgs(argsTypes);
                                generator.invokeStatic(targetType, targetMethod, annotation.itf());
                            }
                            case INVOKEVIRTUAL   -> {
                                generator.loadArgs(argsTypes);
                                generator.invokeVirtual(targetType, targetMethod);
                            }
                            case INVOKESPECIAL   -> {
                                generator.loadArgs(argsTypes);
                                generator.invokeSpecial(targetType, targetMethod, annotation.itf());
                            }
                            case INVOKEINTERFACE -> {
                                generator.loadArgs(argsTypes);
                                generator.invokeInterface(targetType, targetMethod);
                            }
                            case NEW             -> {
                                generator.newInstance(targetType);
                                generator.dup();
                                generator.loadArgs(argsTypes);
                                generator.invokeConstructor(targetType, new Method(ASMHelper._INIT_, targetMethod.getDescriptor()));
                            }
                            case GETSTATIC,
                                 PUTSTATIC,
                                 GETFIELD,
                                 PUTFIELD       -> {
                                generator.loadArgs(argsTypes);
                                generator.fieldInsn(annotation.value(), targetType, selector, targetMethod.getReturnType());
                            }
                            case INSTANCEOF      -> {
                                generator.loadArg(0);
                                generator.instanceOf(targetType);
                            }
                            default              -> throw new IllegalArgumentException(STR."""
                                    Unsupported set: \{ASMHelper.sourceName(sourceClass.name)}#\{sourceMethod.name}\{sourceMethod.desc} [\{annotation.value()}
                                    -> \{ASMHelper.OPCODE_LOOKUP.lookupFieldName(annotation.value())}]""");
                        }
                    }
                    generator.returnValue();
                });
                context.markModified().markCompute(methodNode);
                break;
            }
        return node;
    }
    
    private static String mapDesc(final String desc, final int opcode) = switch (opcode) {
        case INVOKEVIRTUAL,
             INVOKESPECIAL,
             INVOKEINTERFACE    -> {
            final Type args[] = Type.getArgumentTypes(desc);
            yield Type.getMethodDescriptor(Type.getReturnType(desc), Arrays.copyOfRange(args, 1, args.length));
        }
        case NEW                -> Type.getMethodDescriptor(Type.VOID_TYPE, Type.getArgumentTypes(desc));
        default                 -> desc;
    };
    
    private static boolean checkPrivilegedHolding(final ClassNode node) = node.superName.equals(MagicAccessor.Bridge);
    
    public Set<String> targets() = Set.of(ASMHelper.sourceName(sourceClass.name));
    
    @Override
    public String lookupOwner(final String name) = targetType.getInternalName();
    
    @Override
    public String lookupDescriptor(final String name) = desc;
    
}
