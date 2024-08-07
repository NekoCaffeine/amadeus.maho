package amadeus.maho.transform.handler;

import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.commons.TableSwitchGenerator;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

import amadeus.maho.core.Maho;
import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.ClassTransformer;
import amadeus.maho.transform.Metadata;
import amadeus.maho.transform.TransformerManager;
import amadeus.maho.transform.handler.base.DerivedTransformer;
import amadeus.maho.transform.handler.base.MethodTransformer;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.TransformTarget;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.InvisibleType;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.bytecode.Bytecodes;
import amadeus.maho.util.bytecode.context.TransformContext;
import amadeus.maho.util.bytecode.generator.MethodGenerator;
import amadeus.maho.util.bytecode.remap.RemapContext;
import amadeus.maho.util.runtime.ArrayHelper;
import amadeus.maho.util.runtime.DebugHelper;
import amadeus.maho.util.runtime.StringHelper;
import amadeus.maho.vm.JDWP;

import static amadeus.maho.core.extension.DynamicLookupHelper.makeSiteByName;
import static amadeus.maho.util.bytecode.ASMHelper.*;
import static amadeus.maho.util.runtime.ObjectHelper.requireNonNull;
import static org.objectweb.asm.Opcodes.*;

@SneakyThrows
@TransformProvider
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class HookTransformer extends MethodTransformer<Hook> implements ClassTransformer.Limited, RemapContext, DerivedTransformer {
    
    public class ReferenceTransformer implements ClassTransformer.Limited {
        
        @Override
        public Set<String> targets() = Set.of(sourceName(sourceClass.name));
        
        @Override
        public @Nullable ClassNode transform(final TransformContext context, final ClassNode node, final @Nullable ClassLoader loader, final @Nullable Class<?> clazz, final @Nullable ProtectionDomain domain) {
            if (node != null)
                for (final MethodNode methodNode : node.methods)
                    if (methodNode.name.equals(sourceMethod.name) && methodNode.desc.equals(sourceMethod.desc) && Metadata.Guard.missing(methodNode.visibleAnnotations, ReferenceTransformer.class)) {
                        context.markModified().markCompute(methodNode);
                        TransformerManager.transform("hook.reference", STR."\{sourceName(node.name)}#\{methodNode.name}\{methodNode.desc}");
                        for (final AbstractInsnNode insn : methodNode.instructions)
                            if (insn instanceof MethodInsnNode methodInsnNode && methodInsnNode.owner.equals(TYPE_HOOK_RESULT.getInternalName()) && methodInsnNode.name.equals(_INIT_)) {
                                final InsnList shadowList = { };
                                final MethodGenerator generator = MethodGenerator.fromShadowMethodNode(methodNode, shadowList);
                                generator.dup(TYPE_OBJECT); // ..., Result
                                generator.invokeVirtual(TYPE_HOOK_RESULT, STACK_CONTEXT); // ..., Map
                                for (final int index : referenceIndexMark) {
                                    generator.dup(TYPE_OBJECT); // ..., Map, Map
                                    generator.push(index); // ..., Map, Map, I
                                    generator.box(Type.INT_TYPE); // ..., Map, Map, Integer
                                    generator.loadArg(index); // ..., Map, Map, Integer, ?
                                    generator.box(generator.argumentTypes[index]); // ..., Map, Map, Integer, ?
                                    generator.invokeInterface(TYPE_MAP, PUT); // ..., Map, ?
                                    generator.pop(TYPE_OBJECT); // ..., Map
                                }
                                generator.pop(TYPE_OBJECT);
                                methodNode.instructions.insert(insn, shadowList);
                            }
                    }
            return node;
        }
        
    }
    
    @ToString
    @EqualsAndHashCode
    public record LocalVariables(String desc, List<Hook.LocalVar> localVars) { }
    
    @ToString
    @EqualsAndHashCode
    public record ReferenceArgumentInfo(boolean local, boolean store, int index, Type type) { }
    
    private static final Type
            TYPE_HOOK_RESULT             = Type.getObjectType(className("amadeus.maho.transform.mark.Hook$Result")),
            TYPE_DYNAMIC_LINKING_CONTEXT = Type.getObjectType(className("amadeus.maho.core.extension.DynamicLinkingContext"));
    
    private static final Method
            STACK_CONTEXT          = { "stackContext", TYPE_MAP, new Type[0] },
            GET                    = { "get", TYPE_OBJECT, new Type[]{ TYPE_OBJECT } },
            PUT                    = { "put", TYPE_OBJECT, new Type[]{ TYPE_OBJECT, TYPE_OBJECT } },
            CONTAINS_KEY           = { "containsKey", Type.BOOLEAN_TYPE, new Type[]{ TYPE_OBJECT } },
            SHOULD_AVOID_RECURSION = { "shouldAvoidRecursion", Type.BOOLEAN_TYPE, new Type[0] };
    
    private static final String NATIVE_ROLLBACK = "$ROLLBACK$";
    
    @TransformTarget(targetClass = ClassLoader.class, selector = "findNative")
    private static MethodNode remapNativeEntryName(final TransformContext context, final ClassNode node, final MethodNode methodNode) {
        context.markModified().markCompute(methodNode);
        final InsnList insnList = { };
        final MethodGenerator generator = MethodGenerator.fromShadowMethodNode(methodNode, insnList);
        generator.loadArg(1);
        generator.push(NATIVE_ROLLBACK);
        generator.push(StringHelper.EMPTY);
        generator.invokeVirtual(TYPE_STRING, new Method("replace", Type.getMethodDescriptor(TYPE_STRING, TYPE_CHAR_SEQUENCE, TYPE_CHAR_SEQUENCE)));
        generator.storeArg(1);
        methodNode.instructions.insert(insnList);
        return methodNode;
    }
    
    boolean stackFlag, jumpFlag;
    
    String target, desc;
    
    Type captureType, argumentTypes[];
    
    int referenceIndexMark[];
    
    @Nullable
    LinkedHashMap<Integer, ReferenceArgumentInfo> referenceInfos;
    
    @Nullable
    ReferenceTransformer referenceTransformer;
    
    {
        if (noneMatch(sourceMethod.access, ACC_STATIC))
            throw DebugHelper.breakpointBeforeThrow(new IllegalStateException("The hook method must be a static method"));
        final LocalVariables separated = separateLocalVariables(sourceMethod);
        final String realDesc = separated.desc();
        final List<Hook.LocalVar> localVars = separated.localVars();
        if (!localVars.isEmpty() && !annotation.exactMatch())
            throw DebugHelper.breakpointBeforeThrow(new IllegalStateException("Prohibit capturing local variables in the case of non-exact match"));
        if (annotation.capture()) {
            final Type args[] = Type.getArgumentTypes(realDesc);
            if (args.length < 1)
                throw DebugHelper.breakpointBeforeThrow(new IllegalArgumentException("No parameters accepted for capture"));
            desc = Type.getMethodType(Type.getReturnType(realDesc), Arrays.copyOfRange(args, 1, args.length)).getDescriptor();
            captureType = args[0];
        } else {
            desc = realDesc;
            captureType = Type.VOID_TYPE;
        }
        argumentTypes = Type.getArgumentTypes(desc);
        final Type sourceArgumentTypes[] = Type.getArgumentTypes(sourceMethod.desc);
        stackFlag = (referenceTransformer = ArrayHelper.isEmpty(referenceIndexMark = findAnnotatedParameters(sourceMethod, Hook.Reference.class)) ? null : new ReferenceTransformer()) != null;
        if (referenceTransformer != null && referenceIndexMark[0] == 0 && annotation.capture())
            throw DebugHelper.breakpointBeforeThrow(new IllegalStateException("Ineffective capture"));
        if (referenceTransformer != null || !localVars.isEmpty()) {
            final int startOffset = captureType.getSize(), localVarStartIndex = sourceArgumentTypes.length - localVars.size();
            referenceInfos = { };
            for (int index = 0, offset = 0; index < sourceArgumentTypes.length; index++) {
                boolean reference = false;
                final boolean argReference = index < localVarStartIndex;
                if (!argReference || (reference = ArrayHelper.contains(referenceIndexMark, index))) {
                    referenceInfos[index] = { !argReference, reference, argReference ? offset - startOffset : localVars[index - localVarStartIndex].index(), sourceArgumentTypes[index] };
                }
                offset += sourceArgumentTypes[index].getSize();
            }
        } else
            referenceInfos = null;
        jumpFlag = annotation.jump().length > 0;
        if (handler.isNotDefault(Hook::value))
            target = requireNonNull(handler.<Type>lookupSourceValue(Hook::value)).getClassName();
        else
            target = annotation.target().isEmpty() && !annotation.isStatic() && !desc.startsWith("()") ? checkNotPrimitiveType(argumentTypes[0]).getClassName() : annotation.target();
        if (target.isEmpty())
            throw DebugHelper.breakpointBeforeThrow(new IllegalArgumentException("Unable to determine target class, missing required fields('value' or 'target')."));
    }
    
    private Type checkNotPrimitiveType(final Type type) {
        if (!type.getDescriptor().startsWith("L"))
            throw DebugHelper.breakpointBeforeThrow(new IllegalArgumentException(STR."Unable to determine target class, invalid parameter descriptor: \{type.getDescriptor()}"));
        return type;
    }
    
    @Override
    public ClassNode doTransform(final TransformContext context, final ClassNode node, final @Nullable ClassLoader loader, final @Nullable Class<?> clazz, final @Nullable ProtectionDomain domain) {
        Predicate<MethodNode> methodChecker = At.Lookup.methodNodeChecker(annotation.selector(), sourceMethod.name);
        if (annotation.exactMatch())
            methodChecker = methodChecker.and(this::checkMethodNode);
        final ArrayList<MethodNode> methodNodes = { node.methods };
        boolean hit = false;
        for (final MethodNode methodNode : methodNodes)
            if (methodChecker.test(methodNode)) {
                final At redirect[] = annotation.lambdaRedirect();
                if (redirect.length == 0) {
                    TransformerManager.transform("hook", STR."\{sourceName(node.name)}#\{methodNode.name}\{methodNode.desc}\n->  \{sourceName(sourceClass.name)}#\{sourceMethod.name}\{sourceMethod.desc}");
                    hit |= hookMethod(context, node, methodNode);
                } else {
                    final ArrayList<MethodNode> targetMethodNodes = { };
                    targetMethodNodes += methodNode;
                    for (final At at : redirect) {
                        final ArrayList<MethodNode> copy = { targetMethodNodes };
                        targetMethodNodes.clear();
                        copy.forEach(next -> {
                            final List<AbstractInsnNode> targets = At.Lookup.findTargets(at, manager.remapper(), methodNode.instructions);
                            for (final AbstractInsnNode target : targets)
                                if (target instanceof InvokeDynamicInsnNode indy)
                                    if (indy.bsmArgs.length > 1 && indy.bsmArgs[1] instanceof Handle handle)
                                        if (node.name.equals(handle.getOwner()))
                                            lookupMethodNode(node, handle.getName(), handle.getDesc()).ifPresent(targetMethodNodes::add);
                                        else
                                            throw new UnsupportedOperationException(STR."The target anonymous function should be a member of the current class, current: \{node.name}, target: \{handle.getOwner()}.");
                                    else
                                        throw new UnsupportedOperationException("Unable to locate implementation as MethodHandle.");
                                else
                                    throw new UnsupportedOperationException(STR."The target of a lambda Redirect must be invokedynamic. The actual opcode is: \{target.getOpcode()}");
                        });
                        if (targetMethodNodes.isEmpty())
                            break;
                    }
                    for (final MethodNode targetMethodNode : targetMethodNodes) {
                        TransformerManager.transform("hook", STR."""
                                \{sourceName(node.name)}#\{methodNode.name}\{methodNode.desc} => \{targetMethodNode.name}#\{targetMethodNode.desc}
                                ->  \{sourceName(sourceClass.name)}#\{sourceMethod.name}\{sourceMethod.desc}""");
                        hit |= hookMethod(context, node, targetMethodNode);
                    }
                }
            }
        if (!hit && important()) {
            final String debugInfo = STR."Missing: \{sourceName(sourceClass.name)}#\{sourceMethod.name}\{sourceMethod.desc}";
            TransformerManager.transform("hook", debugInfo);
            final JDWP.IDECommand.Notification notification = { JDWP.IDECommand.Notification.Type.WARNING, getClass().getSimpleName(), debugInfo };
            JDWP.MessageQueue.send(notification);
            DebugHelper.breakpoint();
        }
        return node;
    }
    
    private boolean hookMethod(final TransformContext context, final ClassNode node, final MethodNode methodNode) {
        final Type returnType = Type.getReturnType(methodNode.desc);
        final int returnOpcode = returnOpcode(returnType);
        if (anyMatch(methodNode.access, ACC_NATIVE)) {
            final boolean isStatic = anyMatch(methodNode.access, ACC_STATIC);
            methodNode.access &= ~ACC_NATIVE;
            methodNode.instructions = { };
            final MethodGenerator generator = MethodGenerator.fromMethodNode(methodNode);
            node.methods += new MethodNode(changeAccess(methodNode.access, ACC_PRIVATE) | ACC_FINAL, NATIVE_ROLLBACK + methodNode.name, methodNode.desc, methodNode.signature, methodNode.exceptions.toArray(String[]::new));
            if (!isStatic)
                generator.loadThis();
            generator.loadArgs();
            generator.invokeInsn(isStatic ? INVOKESTATIC : INVOKESPECIAL, Type.getObjectType(node.name), new Method(NATIVE_ROLLBACK + methodNode.name, methodNode.desc), anyMatch(node.access, ACC_INTERFACE));
            generator.returnValue();
        }
        final At.Endpoint.Type endpointType = annotation.at().endpoint().value();
        boolean result = false;
        for (final AbstractInsnNode insn : At.Lookup.findTargets(annotation.at(), manager.remapper(), methodNode.instructions)) {
            hookTarget(context, node, methodNode, returnType, returnOpcode, insn);
            result = true;
        }
        if (endpointType == At.Endpoint.Type.FINALLY || endpointType == At.Endpoint.Type.EXCEPTION) {
            hookTarget(context, node, methodNode, returnType, returnOpcode, null);
            result = true;
        }
        return result;
    }
    
    public void hookTarget(final TransformContext context, final ClassNode node, final MethodNode methodNode, final Type returnType, final int returnOpcode, final @Nullable AbstractInsnNode insn) {
        context.markModified().markCompute(methodNode);
        final MethodNode injectMethod = { methodNode.access, methodNode.name, methodNode.desc, methodNode.signature, methodNode.exceptions.toArray(String[]::new) };
        final MethodGenerator generator = MethodGenerator.fromMethodNode(injectMethod);
        @Nullable Label avoidRecursionLabel = null;
        if (annotation.avoidRecursion()) {
            avoidRecursionLabel = { };
            generator.invokeStatic(TYPE_DYNAMIC_LINKING_CONTEXT, SHOULD_AVOID_RECURSION, false);
            generator.ifZCmp(IFNE, avoidRecursionLabel);
        }
        final Type sourceReturnType = Type.getReturnType(sourceMethod.desc);
        if (captureType != Type.VOID_TYPE)
            if (sourceReturnType == Type.VOID_TYPE || sourceReturnType.equals(TYPE_HOOK_RESULT) || annotation.forceReturn())
                generator.dup(captureType);
        if (annotation.exactMatch()) {
            if (!annotation.isStatic())
                generator.loadThis();
            generator.loadArgs();
        } else {
            final Type sourceArgs[] = Type.getArgumentTypes(sourceMethod.desc), targetArgs[] = generator.argumentTypes;
            int offset = captureType != Type.VOID_TYPE ? 1 : 0, length = sourceArgs.length - offset;
            if (length > 0 && noneMatch(methodNode.access, ACC_STATIC)) {
                generator.loadThis();
                offset++;
                length--;
            }
            if (length > 0)
                for (int i = 0; i < length; i++) {
                    if (i < targetArgs.length) {
                        generator.loadArg(i);
                        if (!targetArgs[i].equals(sourceArgs[i + offset]))
                            generator.checkCast(sourceArgs[i + offset]);
                    } else
                        generator.pushDefaultLdc(sourceArgs[i + offset]);
                }
        }
        referenceInfos?.values().stream().filter(ReferenceArgumentInfo::local).forEach(info -> generator.visitVarInsn(info.type().getOpcode(ILOAD), info.index()));
        if (!annotation.direct()) {
            requestMinVersion(node, V1_8);
            generator.invokeDynamic(
                    sourceMethod.name,
                    sourceMethod.desc,
                    makeSiteByName,
                    INVOKESTATIC,
                    context[contextClassLoader()],
                    sourceName(sourceClass.name),
                    ""
            );
        } else {
            final boolean itf = anyMatch(sourceClass.access, ACC_INTERFACE);
            if (itf)
                requestMinVersion(node, V1_8);
            generator.invokeStatic(Type.getObjectType(className(sourceClass.name)), new Method(sourceMethod.name, sourceMethod.desc), itf);
        }
        final int store = annotation.store();
        if (store > -1)
            generator.storeInsn(sourceReturnType, store);
        else if (sourceReturnType.equals(TYPE_HOOK_RESULT)) { // Result
            generator.dup(TYPE_HOOK_RESULT); // Result, Result
            generator.getStatic(TYPE_HOOK_RESULT, "VOID", TYPE_HOOK_RESULT); // Result, Result, Result
            generator.invokeVirtual(TYPE_HOOK_RESULT, MethodGenerator.EQUALS); // Result, Z
            Label label = generator.newLabel();
            generator.ifZCmp(MethodGenerator.NE, label); // Result
            if (returnOpcode != RETURN)
                generator.getField(TYPE_HOOK_RESULT, "result", TYPE_OBJECT); // Object
            switch (returnOpcode) {
                case IRETURN,
                     LRETURN,
                     FRETURN,
                     DRETURN -> {
                    generator.checkCast(boxType(returnType));
                    generator.unbox(returnType);
                }
                case ARETURN -> generator.checkCast(returnType);
                case RETURN  -> generator.pop(TYPE_OBJECT);
            }
            generator.returnValue();
            generator.mark(label); // Result
            if (stackFlag) {
                if (jumpFlag)
                    generator.dup();
                label = generator.newLabel();
                generator.getField(TYPE_HOOK_RESULT, "stackContext", TYPE_MAP); // Map
                generator.dup(TYPE_MAP); // Map, Map
                generator.ifNull(label); // Map
                referenceInfos.forEach((index, info) -> {
                    if (info.store())
                        fillStack(generator, index, info.index(), info.type());
                });
                generator.mark(label); // Map
                generator.pop(TYPE_MAP); // none
            }
            if (jumpFlag) {
                final At jump[] = annotation.jump();
                generator.getField(TYPE_HOOK_RESULT, "jumpIndex", Type.INT_TYPE); // I
                generator.tableSwitch(IntStream.range(0, jump.length).toArray(), new TableSwitchGenerator() {
                    
                    @Override
                    public void generateCase(final int key, final Label end) {
                        final List<AbstractInsnNode> targets = At.Lookup.findTargets(jump[key], manager.remapper(), methodNode.instructions);
                        final int size = targets.size();
                        if (size != 1)
                            throw new IllegalStateException(STR."targets.size() == \{size}");
                        final AbstractInsnNode target = targets[0];
                        if (target instanceof LabelNode labelNode)
                            generator.goTo(labelNode.markLabel());
                        else if (target.getPrevious() instanceof LabelNode labelNode)
                            generator.goTo(labelNode.markLabel());
                        else {
                            final LabelNode labelNode = { };
                            methodNode.instructions.insertBefore(target, labelNode);
                            generator.goTo(labelNode.markLabel());
                        }
                    }
                    
                    @Override
                    public void generateDefault() { } // noop
                    
                }, true);
            } else if (!stackFlag)
                generator.pop(TYPE_HOOK_RESULT); // none
        } else if (annotation.forceReturn()) {
            if (annotation.broadCast())
                generator.broadCast(sourceReturnType, returnType);
            generator.returnValue();
        } else if (sourceReturnType != Type.VOID_TYPE)
            if (annotation.broadCast())
                generator.broadCast(sourceReturnType, captureType);
        if (avoidRecursionLabel != null)
            generator.mark(avoidRecursionLabel);
        final AbstractInsnNode before, after;
        if (insn == null) {
            // noinspection DataFlowIssue
            before = after = null;
            final LabelNode start = { }, end = { };
            final TryCatchBlockNode tryCatchBlock = { start, end, end, captureType != Type.VOID_TYPE && !captureType.equals(TYPE_OBJECT) && !captureType.equals(TYPE_THROWABLE) ? captureType.getInternalName() : null };
            methodNode.tryCatchBlocks += tryCatchBlock;
            methodNode.instructions.insert(start);
            methodNode.instructions.add(end);
            if (!annotation.forceReturn() && annotation.capture() && Type.getReturnType(methodNode.desc).getSort() == Type.VOID)
                methodNode.instructions.add(new InsnNode(DUP));
            methodNode.instructions.add(injectMethod.instructions);
            if (!annotation.forceReturn())
                methodNode.instructions.add(new InsnNode(ATHROW));
        } else if (annotation.before()) {
            before = (after = insn).getPrevious();
            if (before == null && annotation.forceReturn()) {
                methodNode.instructions = injectMethod.instructions;
                methodNode.localVariables?.clear();
                methodNode.visibleLocalVariableAnnotations?.clear();
                methodNode.invisibleLocalVariableAnnotations?.clear();
                methodNode.tryCatchBlocks?.clear();
            } else
                methodNode.instructions.insertBefore(insn, injectMethod.instructions);
        } else {
            after = (before = insn).getNext();
            methodNode.instructions.insert(insn, injectMethod.instructions);
        }
        if (insn != null && before != null && Bytecodes.isConditionalBranch(after.getOpcode()) && sourceReturnType == Type.BOOLEAN_TYPE && captureType == Type.BOOLEAN_TYPE) {
            final Label end = generator.newLabel(), jumpElse = generator.newLabel();
            generator.visitJumpInsn(after.getOpcode(), jumpElse);
            final boolean reversal = annotation.branchReversal();
            generator.push(!reversal);
            generator.goTo(end);
            generator.mark(jumpElse);
            generator.push(reversal);
            generator.mark(end);
            methodNode.instructions.insert(before, injectMethod.instructions);
            after.opcode(reversal ? IFNE : IFEQ);
        }
    }
    
    private void fillStack(final MethodGenerator generator, final int index, final int storeIndex, final Type type) {
        generator.dup(TYPE_MAP); // Map, Map
        generator.push(index); // Map, Map, I
        generator.box(Type.INT_TYPE); // Map, Map, Integer
        generator.invokeInterface(TYPE_MAP, CONTAINS_KEY); // Map, Z
        final Label next = generator.newLabel();
        generator.ifZCmp(MethodGenerator.EQ, next); // Map
        generator.dup(TYPE_MAP); // Map, Map
        generator.push(index); // Map, Map, I
        generator.box(Type.INT_TYPE); // Map, Map, Integer
        generator.invokeInterface(TYPE_MAP, GET); // Map, Object
        generator.checkCast(boxType(type)); // Map, ?
        if (isUnboxType(type))
            generator.unbox(type); // Map, ?
        generator.storeInsn(type, storeIndex); // Map
        generator.mark(next); // Map
    }
    
    private LocalVariables separateLocalVariables(final MethodNode methodNode) {
        final String mappedDesc = InvisibleType.Transformer.transformDescriptor(manager.remapper(), sourceMethod);
        final @Nullable List<AnnotationNode> parameterAnnotations[] = methodNode.visibleParameterAnnotations;
        if (parameterAnnotations != null) {
            boolean flag = false;
            final LinkedList<Hook.LocalVar> localVars = { };
            for (int i = parameterAnnotations.length - 1; i >= 0; i--) {
                final List<AnnotationNode> annotations = parameterAnnotations[i];
                final @Nullable Hook.LocalVar localVar = findAnnotation(annotations, Hook.LocalVar.class, contextClassLoader());
                if (localVar == null)
                    flag = true;
                else if (flag)
                    Maho.warn(STR."Invalid local variable at index: \{i}, which must be at the end of the method parameter list.");
                else
                    localVars >> localVar;
            }
            if (!localVars.isEmpty()) {
                final Type argumentTypes[] = Type.getArgumentTypes(mappedDesc);
                return { Type.getMethodDescriptor(Type.getReturnType(mappedDesc), Stream.of(argumentTypes).limit(argumentTypes.length - localVars.size()).toArray(Type[]::new)), localVars };
            }
        }
        return { mappedDesc, List.of() };
    }
    
    private boolean checkMethodNode(final MethodNode methodNode) {
        if (annotation.isStatic() != anyMatch(methodNode.access, ACC_STATIC))
            return false;
        final Iterator<Type> sourceMethodTypes = List.of(argumentTypes).iterator(), srcMethodTypes = List.of(Type.getArgumentTypes(methodNode.desc)).iterator();
        if (!annotation.isStatic())
            sourceMethodTypes.next();
        while (sourceMethodTypes.hasNext())
            if (!srcMethodTypes.hasNext() || !sourceMethodTypes.next().equals(srcMethodTypes.next()))
                return false;
        return !srcMethodTypes.hasNext();
    }
    
    @Override
    public Set<String> targets() = Set.of(target);
    
    @Override
    public String lookupOwner(final String name) = className(target);
    
    @Override
    public String lookupDescriptor(final String name) = desc;
    
    @Override
    public Stream<? extends ClassTransformer> derivedTransformers() = Stream.ofNullable(referenceTransformer);
    
}
