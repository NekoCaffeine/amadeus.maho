package amadeus.maho.transform.handler;

import java.lang.annotation.Annotation;
import java.security.ProtectionDomain;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeAnnotationNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.ClassTransformer;
import amadeus.maho.transform.TransformerManager;
import amadeus.maho.transform.handler.base.BaseTransformer;
import amadeus.maho.transform.mark.Patch;
import amadeus.maho.transform.mark.base.InvisibleType;
import amadeus.maho.util.annotation.AnnotationHandler;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.Bytecodes;
import amadeus.maho.util.bytecode.ComputeType;
import amadeus.maho.util.bytecode.context.TransformContext;
import amadeus.maho.util.bytecode.remap.RemapHandler;
import amadeus.maho.util.bytecode.tree.DynamicVarInsnNode;
import amadeus.maho.util.bytecode.tree.NodeCopier;
import amadeus.maho.util.runtime.ObjectHelper;

import static org.objectweb.asm.Opcodes.*;

@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class PatchTransformer extends BaseTransformer<Patch> implements ClassTransformer.Limited {
    
    String target;
    
    {
        if (handler.isNotDefault(Patch::value))
            target = handler.<Type>lookupSourceValue(Patch::value).getClassName();
        else
            target = annotation.target();
        if (target.isEmpty())
            throw new IllegalArgumentException("Unable to determine target class, missing required fields('value' or 'target').");
    }
    
    @Override
    public ClassNode doTransform(final TransformContext context, final ClassNode node, final @Nullable ClassLoader loader, final @Nullable Class<?> clazz, final @Nullable ProtectionDomain domain) {
        TransformerManager.transform("patch", "%s\n->  %s".formatted(ASMHelper.sourceName(node.name), ASMHelper.sourceName(sourceClass.name)));
        final ClassNode copy = ASMHelper.newClassNode(sourceClass);
        final ClassNode patch = handler.isNotDefault(Patch::remap) ? new RemapTransformer(manager, annotation.remap(), copy).transformWithoutContext(copy, loader) : copy;
        patch(context, manager.remapper(), patch, node, annotation.metadata().remap());
        context.markModified();
        return node;
    }
    
    public static void patch(final TransformContext context, final RemapHandler.ASMRemapper remapper, final ClassNode patch, final ClassNode node, final boolean remap) {
        InvisibleType.Transformer.transform(remapper, patch);
        patch.methods.removeIf(methodNode -> !checkMethodNode(methodNode, node));
        patch.fields.removeIf(methodNode -> !checkFieldNode(methodNode, node));
        final String patchName = patch.name;
        final String clazzName = node.name;
        final String superName = node.superName;
        patch.methods.forEach(methodNode -> patchMethod(methodNode, clazzName, superName, true));
        patch.methods.forEach(methodNode -> patchMethod(methodNode, patchName, clazzName, false));
        final Map<MethodNode, MethodNode> mapping = new LinkedHashMap<>();
        final List<MethodNode> sources = new LinkedList<>();
        for (final MethodNode method : node.methods)
            for (Iterator<MethodNode> iterator = patch.methods.iterator(); iterator.hasNext(); ) {
                final MethodNode patchMethod = iterator.next();
                final boolean inline = ASMHelper.hasAnnotation(patchMethod, Patch.Inline.class);
                if (method.name.equals(patchMethod.name) && method.desc.equals(patchMethod.desc))
                    if (!method.name.startsWith("<")) {
                        MethodNode copy = null;
                        final List<MethodInsnNode> inlineTargets = new LinkedList<>();
                        for (final AbstractInsnNode insn : patchMethod.instructions) {
                            if (insn instanceof MethodInsnNode methodInsn) {
                                if (ASMHelper.corresponding(patchMethod, node.name, methodInsn))
                                    if (inline)
                                        inlineTargets += methodInsn;
                                    else {
                                        if (copy == null) {
                                            method.accept(copy = { method.access, method.name, method.desc, method.signature, method.exceptions.toArray(String[]::new) });
                                            copy.access = ASMHelper.changeAccess(copy.access, ACC_PRIVATE);
                                            copy.name = getMethodName(node, copy.name);
                                            for (final AbstractInsnNode copyInsn : copy.instructions)
                                                if (copyInsn instanceof MethodInsnNode copyMethodInsn)
                                                    if (ASMHelper.corresponding(method, node.name, copyMethodInsn))
                                                        copyMethodInsn.name = copy.name;
                                        }
                                        methodInsn.name = copy.name;
                                    }
                                if (methodInsn.name.equals(patchMethod.name) && methodInsn.desc.equals(patchMethod.desc) && patchMethod.visibleTypeAnnotations != null) {
                                    final Type args[] = Type.getArgumentTypes(methodInsn.desc);
                                    for (int i = 0; i < patchMethod.visibleTypeAnnotations.size(); i++) {
                                        final TypeAnnotationNode ann = patchMethod.visibleTypeAnnotations.get(i);
                                        if (ASMHelper.corresponding(ann, Patch.Generic.class)) {
                                            final Patch.Generic generic = AnnotationHandler.make(Patch.Generic.class, ann.values);
                                            final Type genericType = Type.getType(generic.value());
                                            args[i] = remap ? remapper.mapType(genericType) : genericType;
                                        }
                                    }
                                    methodInsn.desc = Type.getMethodDescriptor(Type.getReturnType(methodInsn.desc), args);
                                }
                            }
                        }
                        if (copy != null)
                            sources += copy;
                        if (inline) {
                            context.markCompute(patchMethod, ComputeType.MAX, ComputeType.FRAME);
                            final MethodNode copyRef = { method.access, method.name, method.desc, method.signature, method.exceptions.toArray(String[]::new) };
                            method.accept(copyRef);
                            final int baseStackSize = ASMHelper.baseStackSize(ASMHelper.anyMatch(copyRef.access, ACC_STATIC), copyRef.desc);
                            inlineTargets.forEach(inlineTarget -> {
                                final LabelNode labelNode = { };
                                final MethodNode tmpCopy = { copyRef.access, copyRef.name, copyRef.desc, copyRef.signature, copyRef.exceptions.toArray(String[]::new) };
                                copyRef.accept(tmpCopy);
                                for (final AbstractInsnNode insn : tmpCopy.instructions) {
                                    if (insn instanceof VarInsnNode && ((VarInsnNode) insn).var >= baseStackSize)
                                        tmpCopy.instructions.set(insn, new DynamicVarInsnNode(insn.getOpcode(), ((VarInsnNode) insn).var));
                                    else if (Bytecodes.isReturn(insn.getOpcode()))
                                        tmpCopy.instructions.set(insn, new JumpInsnNode(GOTO, labelNode));
                                }
                                tmpCopy.instructions.add(labelNode);
                                patchMethod.instructions.insert(inlineTarget, tmpCopy.instructions);
                                ASMHelper.removeInvoke(patchMethod.instructions, inlineTarget, true);
                            });
                            DynamicVarInsnNode.normalizationInsnList(patchMethod.instructions);
                        }
                        mapping.put(method, patchMethod);
                    } else {
                        context.markCompute(method, ComputeType.MAX, ComputeType.FRAME);
                        final ListIterator<AbstractInsnNode> insnListIterator = method.instructions.iterator(method.instructions.size());
                        for (AbstractInsnNode insn = insnListIterator.previous(); insnListIterator.hasPrevious(); ) {
                            insnListIterator.remove();
                            if (insn instanceof InsnNode)
                                break;
                            insn = insnListIterator.previous();
                        }
                        if (method.name.equals(ASMHelper._INIT_)) {
                            if (!method.desc.equals(patchMethod.desc))
                                continue;
                            MethodInsnNode superCall = ASMHelper.findSuperCall(patchMethod, superName);
                            if (superCall != null)
                                for (Iterator<AbstractInsnNode> insnIterator = patchMethod.instructions.iterator(); insnIterator.hasNext(); ) {
                                    final AbstractInsnNode insn = insnIterator.next();
                                    insnIterator.remove();
                                    if (insn == superCall)
                                        break;
                                }
                            if (patchMethod.visibleAnnotations != null)
                                if (ASMHelper.hasAnnotation(patchMethod, Patch.Replace.class)) {
                                    superCall = ASMHelper.findSuperCall(method, superName);
                                    if (superCall != null) {
                                        boolean flag = false;
                                        for (final Iterator<AbstractInsnNode> insnIterator = method.instructions.iterator(); insnIterator.hasNext(); ) {
                                            final AbstractInsnNode insn = insnIterator.next();
                                            if (flag)
                                                insnIterator.remove();
                                            else if (insn == superCall)
                                                flag = true;
                                        }
                                    }
                                }
                        }
                        method.instructions = NodeCopier.merge(method.instructions, patchMethod.instructions);
                        iterator.remove();
                    }
            }
        for (final MethodNode method : patch.methods)
            if (method.name.equals(ASMHelper._INIT_) && ASMHelper.hasAnnotation(method, Patch.Super.class)) {
                final MethodInsnNode superCall = ASMHelper.findSuperCall(method, clazzName);
                if (superCall != null)
                    superCall.owner = replace(superCall.owner, clazzName, superName);
            }
        for (final Map.Entry<MethodNode, MethodNode> entry : mapping.entrySet()) {
            final int index = node.methods.indexOf(entry.getKey());
            node.methods.remove(index);
            node.methods.add(index, entry.getValue());
            patch.methods -= entry.getValue();
        }
        node.methods *= sources;
        node.methods *= patch.methods;
        patch.fields.forEach(newField -> node.fields.removeIf(oldField -> ObjectHelper.equals(newField.name, oldField.name)));
        node.fields *= patch.fields;
        node.interfaces *= patch.interfaces;
        node.interfaces -= node.name;
        ASMHelper.delAllAnnotation(node, Stream.of(Patch.class.getDeclaredClasses())
                .filterAssignableFrom(Annotation.class)
                .collect(Collectors.toSet()));
        ASMHelper.requestMinVersion(node, patch.version);
    }
    
    public static String getMethodName(final ClassNode node, final String name) {
        String newName = "$runtime_source$_" + name;
        for (final MethodNode method : node.methods)
            if (method.name.equals(newName))
                newName = getMethodName(node, newName);
        return newName;
    }
    
    public static boolean checkFieldNode(final FieldNode field, final ClassNode node) {
        if (field.visibleAnnotations == null)
            return true;
        if (ASMHelper.hasAnnotation(field, Patch.Exception.class))
            return false;
        if (ASMHelper.hasAnnotation(field, Patch.Remove.class)) {
            node.fields.removeIf(target -> ASMHelper.corresponding(field, target));
            return false;
        }
        if (ASMHelper.hasAnnotation(field, Patch.Spare.class))
            for (final FieldNode nowField : node.fields)
                if (field.name.equals(nowField.name) && field.desc.equals(nowField.desc))
                    return false;
        if (ASMHelper.hasAnnotation(field, Patch.Specific.class)) {
            for (final FieldNode nowField : node.fields)
                if (field.name.equals(nowField.name) && field.desc.equals(nowField.desc))
                    return true;
            return false;
        }
        return true;
    }
    
    public static boolean checkMethodNode(final MethodNode method, final ClassNode node) {
        if (method.visibleAnnotations == null)
            return true;
        if (ASMHelper.hasAnnotation(method, Patch.Exception.class))
            return false;
        if (ASMHelper.hasAnnotation(method, Patch.Remove.class)) {
            node.methods.removeIf(target -> ASMHelper.corresponding(method, target));
            return false;
        }
        if (ASMHelper.hasAnnotation(method, Patch.Spare.class))
            for (final MethodNode nowMethod : node.methods)
                if (method.name.equals(nowMethod.name) && method.desc.equals(nowMethod.desc))
                    return false;
        if (ASMHelper.hasAnnotation(method, Patch.Specific.class)) {
            for (final MethodNode nowMethod : node.methods)
                if (method.name.equals(nowMethod.name) && method.desc.equals(nowMethod.desc))
                    return true;
            return false;
        }
        return true;
    }
    
    public static void patchMethod(final MethodNode methodNode, final String patchName, final String clazzName, final boolean isSuper) {
        methodNode.desc = methodNode.desc.replace(patchName, clazzName);
        for (final AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof TypeInsnNode type)
                type.desc = replace(type.desc, patchName, clazzName);
            else if (insn instanceof FieldInsnNode) {
                if (!isSuper) {
                    final FieldInsnNode field = (FieldInsnNode) insn;
                    field.owner = replace(field.owner, patchName, clazzName);
                }
            } else if (insn instanceof MethodInsnNode method) {
                final boolean flag = !isSuper || method.getOpcode() == INVOKESPECIAL;
                if (flag)
                    method.owner = replace(method.owner, patchName, clazzName);
            } else if (insn instanceof InvokeDynamicInsnNode invokeDynamicInsn) {
                final String patchDesc = ASMHelper.classDesc(patchName), clazzDesc = ASMHelper.classDesc(clazzName);
                invokeDynamicInsn.desc = invokeDynamicInsn.desc.replace(patchDesc, clazzDesc);
                invokeDynamicInsn.bsm = {
                        invokeDynamicInsn.bsm.getTag(),
                        replace(invokeDynamicInsn.bsm.getOwner(), patchName, clazzName),
                        invokeDynamicInsn.bsm.getName(),
                        invokeDynamicInsn.bsm.getDesc().replace(patchDesc, clazzDesc),
                        invokeDynamicInsn.bsm.isInterface()
                };
                for (int i = 0; i < invokeDynamicInsn.bsmArgs.length; i++)
                    if (invokeDynamicInsn.bsmArgs[i] instanceof Handle handle)
                        invokeDynamicInsn.bsmArgs[i] = new Handle(handle.getTag(), replace(handle.getOwner(), patchName, clazzName),
                                handle.getName(), handle.getDesc().replace(patchDesc, clazzDesc), ((Handle) invokeDynamicInsn.bsmArgs[i]).isInterface());
            } else if (insn instanceof FrameNode frame) {
                if (frame.local != null)
                    frame.local.replaceAll(o -> o instanceof String ? replace((String) o, patchName, clazzName) : o);
                if (frame.stack != null)
                    frame.stack.replaceAll(o -> o instanceof String ? replace((String) o, patchName, clazzName) : o);
            }
        }
    }
    
    private static String replace(final String src, final String patch, final String clazz) = src.equals(patch) ? clazz : src;
    
    @Override
    public Set<String> targets() = Set.of(target);
    
}
