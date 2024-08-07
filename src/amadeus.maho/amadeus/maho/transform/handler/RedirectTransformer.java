package amadeus.maho.transform.handler;

import java.security.ProtectionDomain;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.function.Predicate;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.ClassTransformer;
import amadeus.maho.transform.TransformerManager;
import amadeus.maho.transform.handler.base.MethodTransformer;
import amadeus.maho.transform.mark.Redirect;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.Slice;
import amadeus.maho.util.annotation.AnnotationHandler;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.context.TransformContext;
import amadeus.maho.util.bytecode.remap.RemapContext;
import amadeus.maho.util.runtime.DebugHelper;
import amadeus.maho.vm.JDWP;

import static amadeus.maho.core.extension.DynamicLookupHelper.makeSiteByName;
import static amadeus.maho.util.runtime.ObjectHelper.requireNonNull;
import static org.objectweb.asm.Opcodes.*;

@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class RedirectTransformer extends MethodTransformer<Redirect> implements ClassTransformer.Limited, RemapContext {
    
    String target;
    
    {
        if (handler.isNotDefault(Redirect::targetClass))
            target = requireNonNull(handler.<Type>lookupSourceValue(Redirect::targetClass)).getClassName();
        else
            target = annotation.target();
        if (target.isEmpty())
            throw new IllegalArgumentException("Unable to determine target class, missing required fields('targetClass' or 'target').");
    }
    
    @Override
    public ClassNode doTransform(final TransformContext context, final ClassNode node, final @Nullable ClassLoader loader, final @Nullable Class<?> clazz, final @Nullable ProtectionDomain domain) {
        final String selector = annotation.selector().isEmpty() ? sourceMethod.name : annotation.selector();
        Predicate<MethodNode> methodChecker = At.Lookup.WILDCARD.equals(selector) ? _ -> true : method -> method.name.equals(selector);
        methodChecker = methodChecker.and(At.Lookup.WILDCARD.equals(annotation.descriptor()) ? _ -> true : method -> method.desc.equals(annotation.descriptor()));
        final Slice slice = annotation.slice();
        final AnnotationHandler<Slice> sliceHandler = AnnotationHandler.asOneOfUs(slice);
        final At start = slice.value();
        final @Nullable At end = sliceHandler.lookupSourceValue(Slice::end);
        boolean hit = false;
        for (final MethodNode methodNode : node.methods)
            if (methodChecker.test(methodNode)) {
                final List<AbstractInsnNode> targets = At.Lookup.findTargets(start, manager.remapper(), methodNode.instructions);
                if (targets.isEmpty())
                    continue;
                if (end != null) {
                    final List<AbstractInsnNode> endTarget = At.Lookup.findTargets(end, manager.remapper(), methodNode.instructions);
                    if (targets.size() != 1 || endTarget.size() != 1)
                        throw DebugHelper.breakpointBeforeThrow(new IllegalArgumentException("The result of slice matching is not unique"));
                    final AbstractInsnNode startTargetNode = targets.getFirst(), endTargetNode = endTarget.getFirst();
                    boolean flag = false;
                    for (final ListIterator<AbstractInsnNode> iterator = methodNode.instructions.iterator(); iterator.hasNext(); ) {
                        final AbstractInsnNode insn = iterator.next();
                        if (flag) {
                            iterator.remove();
                            if (insn == endTargetNode)
                                break;
                        } else {
                            if (insn == endTargetNode)
                                throw DebugHelper.breakpointBeforeThrow(new IllegalArgumentException("The end of the slice appears before the start"));
                            if (insn == startTargetNode)
                                flag = true;
                        }
                    }
                }
                final boolean lookup = !annotation.direct();
                targets.forEach(insnNode -> methodNode.instructions.set(insnNode,
                        lookup ?
                                new InvokeDynamicInsnNode(
                                        sourceMethod.name,
                                        sourceMethod.desc,
                                        makeSiteByName,
                                        INVOKESTATIC,
                                        context[contextClassLoader()],
                                        ASMHelper.sourceName(sourceClass.name),
                                        ""
                                ) :
                                new MethodInsnNode(
                                        INVOKESTATIC, sourceClass.name, sourceMethod.name, sourceMethod.desc,
                                        ASMHelper.anyMatch(sourceClass.access, ACC_INTERFACE)
                                )));
                ASMHelper.requestMinVersion(node, V1_7);
                context.markModified();
                TransformerManager.transform("redirect", STR."\{ASMHelper.sourceName(node.name)}#\{methodNode.name}\{methodNode.desc}\n->  \{annotation.target()}#\{sourceMethod.name}\{sourceMethod.desc}");
                hit = true;
            }
        if (!hit && important()) {
            final String debugInfo = STR."Missing: \{ASMHelper.sourceName(sourceClass.name)}#\{sourceMethod.name}\{sourceMethod.desc}";
            TransformerManager.transform("redirect", debugInfo);
            final JDWP.IDECommand.Notification notification = { JDWP.IDECommand.Notification.Type.WARNING, getClass().getSimpleName(), debugInfo };
            JDWP.MessageQueue.send(notification);
            DebugHelper.breakpoint();
        }
        return node;
    }
    
    @Override
    public Set<String> targets() = Set.of(target);
    
    @Override
    public String lookupOwner(final String name) = ASMHelper.className(target);
    
    @Override
    public String lookupDescriptor(final String name) = annotation.descriptor();
    
}
