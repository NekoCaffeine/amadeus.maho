package amadeus.maho.transform.handler;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.security.ProtectionDomain;
import java.util.ListIterator;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.ClassTransformer;
import amadeus.maho.transform.TransformerManager;
import amadeus.maho.transform.handler.base.MethodTransformer;
import amadeus.maho.transform.mark.TransformTarget;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.MethodDescriptor;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.context.TransformContext;
import amadeus.maho.util.bytecode.remap.RemapContext;
import amadeus.maho.util.runtime.MethodHandleHelper;

import static org.objectweb.asm.Opcodes.ACC_STATIC;

@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class TargetTransformer extends MethodTransformer<TransformTarget> implements ClassTransformer.Limited, RemapContext {
    
    private static final Type
            TYPE_TRANSFORM_CONTEXT = Type.getType(TransformContext.class),
            TYPE_CLASS_NODE        = Type.getType(ClassNode.class),
            TYPE_FIELD_NODE        = Type.getType(FieldNode.class),
            TYPE_METHOD_NAME       = Type.getType(MethodNode.class);
    
    String target;
    
    boolean
            uncheckName = annotation.selector().equals(At.Lookup.WILDCARD),
            uncheckDesc = handler.isDefault(TransformTarget::desc);
    
    @Nullable String desc = uncheckDesc ? null : MethodDescriptor.Mapper.methodDescriptor(annotation.desc(), manager.remapper(), annotation.metadata().remap());
    
    {
        if (handler.isNotDefault(TransformTarget::targetClass))
            target = handler.<Type>lookupSourceValue(TransformTarget::targetClass).getClassName();
        else
            target = annotation.target();
        if (target.isEmpty())
            throw new IllegalArgumentException("Unable to determine target class, missing required fields('targetClass' or 'target').");
    }
    
    BiFunction<TransformContext, ClassNode, ClassNode> transformHandler[] = new BiFunction[1]; // doTransform disallow class loading
    
    @Override
    public void contextClassLoader(final ClassLoader loader) {
        super.contextClassLoader(loader);
        transformHandler[0] = makeHandler(loader);
    }
    
    @Override
    public ClassNode doTransform(final TransformContext context, final ClassNode node, final @Nullable ClassLoader loader, final @Nullable Class<?> clazz, final @Nullable ProtectionDomain domain) = transformHandler[0].apply(context, node);
    
    @SneakyThrows
    private BiFunction<TransformContext, ClassNode, ClassNode> makeHandler(final ClassLoader loader) {
        final Class<?> targetType = lookupTargetType(sourceMethod);
        final Class<?> providerClass = ASMHelper.loadType(Type.getObjectType(sourceClass.name), false, loader);
        final MethodType methodType = ASMHelper.loadMethodType(sourceMethod, false, loader);
        final MethodHandle handle = MethodHandleHelper.lookup().findStatic(providerClass, sourceMethod.name, methodType);
        final boolean hasReturn = methodType.returnType() != void.class;
        if (targetType == ClassNode.class)
            return (context, node) -> {
                TransformerManager.transform("transform", "%s\n->  %s#%s%s".formatted(ASMHelper.sourceName(node.name), ASMHelper.sourceName(sourceClass.name), sourceMethod.name, sourceMethod.desc));
                if (hasReturn)
                    return (ClassNode) handle.invoke(context, node) ?? node;
                handle.invoke(context, node);
                return node;
            };
        final Predicate<String> selector = At.Lookup.selector(annotation.selector(), sourceMethod.name);
        if (targetType == FieldNode.class) {
            final String fieldDesc = Type.getMethodType(desc).getReturnType().getDescriptor();
            return (context, node) -> {
                for (final ListIterator<FieldNode> iterator = node.fields.listIterator(); iterator.hasNext(); ) {
                    final FieldNode fieldNode = iterator.next();
                    if ((uncheckName || selector.test(fieldNode.name)) &&
                            (uncheckDesc || fieldDesc.equals(fieldNode.desc))) {
                        TransformerManager.transform("transform", "%s#%s%s\n->  %s#%s%s".formatted(ASMHelper.sourceName(node.name), fieldNode.name, fieldNode.desc, ASMHelper.sourceName(sourceClass.name), sourceMethod.name, sourceMethod.desc));
                        if (hasReturn) {
                            final @Nullable FieldNode result = (FieldNode) handle.invoke(context, node, fieldNode);
                            if (result == null)
                                iterator.remove();
                            else if (result != fieldNode)
                                iterator.set(result);
                        } else
                            handle.invoke(context, node, fieldNode);
                    }
                }
                return node;
            };
        }
        if (targetType == MethodNode.class)
            return (context, node) -> {
                for (final ListIterator<MethodNode> iterator = node.methods.listIterator(); iterator.hasNext(); ) {
                    final MethodNode methodNode = iterator.next();
                    if ((uncheckName || selector.test(methodNode.name)) &&
                            (uncheckDesc || desc.equals(methodNode.desc))) {
                        TransformerManager.transform("transform", "%s#%s%s\n->  %s#%s%s".formatted(ASMHelper.sourceName(node.name), methodNode.name, methodNode.desc, ASMHelper.sourceName(sourceClass.name), sourceMethod.name, sourceMethod.desc));
                        if (hasReturn) {
                            final @Nullable MethodNode result = (MethodNode) handle.invoke(context, node, methodNode);
                            if (result == null)
                                iterator.remove();
                            else if (result != methodNode)
                                iterator.set(result);
                        } else
                            handle.invoke(context, node, methodNode);
                    }
                }
                return node;
            };
        final boolean needIterator = Type.getArgumentTypes(sourceMethod.desc).length > 4;
        if (AbstractInsnNode.class.isAssignableFrom(targetType))
            return (context, node) -> {
                for (final MethodNode methodNode : node.methods) {
                    if ((uncheckName || selector.test(methodNode.name)) &&
                            (uncheckDesc || desc.equals(methodNode.desc))) {
                        TransformerManager.transform("transform", "%s#%s%s\n->  %s#%s%s".formatted(ASMHelper.sourceName(node.name), methodNode.name, methodNode.desc, ASMHelper.sourceName(sourceClass.name), sourceMethod.name, sourceMethod.desc));
                        for (final ListIterator<AbstractInsnNode> insnIterator = methodNode.instructions.iterator(); insnIterator.hasNext(); ) {
                            final AbstractInsnNode insn = insnIterator.next();
                            if (targetType.isInstance(insn))
                                if (hasReturn) {
                                    final @Nullable AbstractInsnNode result = needIterator ?
                                            (AbstractInsnNode) handle.invoke(context, node, methodNode, insn, insnIterator) :
                                            (AbstractInsnNode) handle.invoke(context, node, methodNode, insn);
                                    if (result == null)
                                        insnIterator.remove();
                                    else if (result != insn)
                                        insnIterator.set(result);
                                } else if (needIterator)
                                    handle.invoke(context, node, methodNode, insn, insnIterator);
                                else
                                    handle.invoke(context, node, methodNode, insn);
                        }
                    }
                }
                return node;
            };
        throw new AssertionError("Unsupported target target");
    }
    
    private static Class<?> lookupTargetType(final MethodNode methodNode) {
        if (ASMHelper.noneMatch(methodNode.access, ACC_STATIC))
            throw new IllegalArgumentException("Provider must be static");
        final Type args[] = Type.getArgumentTypes(methodNode.desc);
        if (args.length < 2 || args.length > 5)
            throw new IllegalArgumentException("Incorrect parameter length");
        if (!args[0].equals(TYPE_TRANSFORM_CONTEXT))
            throw new IllegalArgumentException("The first parameter must be TransformContext");
        if (!args[1].equals(TYPE_CLASS_NODE))
            throw new IllegalArgumentException("The second parameter must be ClassNode");
        final Type result = switch (args.length) {
            case 2    -> TYPE_CLASS_NODE;
            case 3    -> {
                if (!args[2].equals(TYPE_FIELD_NODE) && !args[2].equals(TYPE_METHOD_NAME))
                    throw new IllegalArgumentException("The third parameter must be FieldNode or MethodNode");
                yield args[2];
            }
            case 4, 5 -> {
                if (!args[2].equals(TYPE_METHOD_NAME) || !AbstractInsnNode.class.isAssignableFrom(ASMHelper.loadType(args[3],
                        false, TargetTransformer.class.getClassLoader())))
                    throw new IllegalArgumentException("If the parameter list length is four, then the third and fourth parameters can only be MethodNode and subclasses of AbstractInsnNode");
                if (args.length == 5 && !args[4].equals(Type.getType(ListIterator.class)))
                    throw new IllegalArgumentException("When trying to match an instruction in a method body, the fifth argument must be of type %s.".formatted(ListIterator.class.getCanonicalName()));
                yield args[3];
            }
            default   -> throw new IllegalArgumentException("Number of illegal parameters: args.length = " + args.length);
        };
        final Type returnType = Type.getReturnType(methodNode.desc);
        if (returnType != Type.VOID_TYPE)
            if (AbstractInsnNode.class.isAssignableFrom(ASMHelper.loadType(result, false, TargetTransformer.class.getClassLoader()))) {
                if (!AbstractInsnNode.class.isAssignableFrom(ASMHelper.loadType(returnType, false, TargetTransformer.class.getClassLoader())))
                    throw new IllegalArgumentException("The return target must be void or matched target(" + AbstractInsnNode.class.getName() + ")");
            } else if (!returnType.equals(result))
                throw new IllegalArgumentException("The return target must be void or matched target(" + result.getClassName() + ")");
        return ASMHelper.loadType(result, true, TargetTransformer.class.getClassLoader());
    }
    
    @Override
    public Set<String> targets() = Set.of(target);
    
    @Override
    public String lookupOwner(final String name) = ASMHelper.className(target);
    
    @Override
    public String lookupDescriptor(final String name) = desc;
    
}
