package amadeus.maho.util.bytecode.traverser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import amadeus.maho.core.MahoProfile;
import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.ClassWriter;
import amadeus.maho.util.bytecode.ComputeType;
import amadeus.maho.util.bytecode.FrameHelper;
import amadeus.maho.util.bytecode.traverser.exception.ComputeException;
import amadeus.maho.util.bytecode.type.IntTypeRange;
import amadeus.maho.util.profile.Sampler;
import amadeus.maho.util.runtime.TypeHelper;
import amadeus.maho.vm.transform.mark.HotSpotJIT;
import amadeus.maho.vm.transform.mark.HotSpotMethodFlags;

import static amadeus.maho.util.bytecode.Bytecodes.*;
import static amadeus.maho.vm.reflection.hotspot.KlassMethod.Flags._force_inline;
import static java.lang.Math.max;
import static org.objectweb.asm.tree.AbstractInsnNode.*;

@HotSpotJIT
@TransformProvider
@FunctionalInterface
public interface MethodTraverser {
    
    @HotSpotJIT
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PUBLIC)
    class ComputeLabel extends Label {
        
        protected static final Label shouldMark = { };
        
        boolean mark = false;
        
        @Nullable Frame result = null;
        
        HashSet<Frame.Snapshot> snapshots = { };
        
        final @Nullable List<TryCatchBlockNode> handlers;
        
        @HotSpotMethodFlags(_force_inline)
        public final boolean mark(final Frame frame, final Frame.Snapshot snapshot, final BinaryOperator<String> getCommonSuperClass) {
            if (result == null) {
                result = frame.dup((LabelNode) info);
                snapshots += snapshot;
            } else if (snapshots.add(snapshot))
                result.merge(frame, getCommonSuperClass);
            else
                return false;
            if (handlers != null)
                for (final TryCatchBlockNode block : handlers)
                    if (block.start == info)
                        ((ExceptionHandlerLabel) block.handler.labelGet()).mergeExceptionHandler(frame, getCommonSuperClass);
            return true;
        }
        
        public static void insertFrame(final InsnList instructions, final Frame.Snapshot init) {
            Frame.Snapshot snapshot = init;
            @Nullable AbstractInsnNode next = instructions.getFirst();
            while (next != null) {
                if (next.getType() == LABEL && ((LabelNode) next).labelGet() instanceof ComputeLabel computeLabel && computeLabel.mark) {
                    while ((next = next.getNext()) != null && next.getOpcode() == -1) ;
                    if (next == null)
                        return;
                    instructions.insertBefore(next, snapshot.diff(snapshot = computeLabel.result.snapshot()));
                }
                next = next.getNext();
            }
        }
        
        public static boolean preMark(final MethodNode methodNode, final BinaryOperator<String> getCommonSuperClass) {
            final InsnList instructions = methodNode.instructions;
            FrameHelper.dropFrame(instructions);
            @Nullable AbstractInsnNode next = instructions.getFirst();
            final @Nullable List<TryCatchBlockNode> handlers = methodNode.tryCatchBlocks == null || methodNode.tryCatchBlocks.isEmpty() ? null : methodNode.tryCatchBlocks;
            final @Nullable ArrayList<TryCatchBlockNode> contextHandlers = handlers == null ? null : new ArrayList<>();
            final Supplier<List<TryCatchBlockNode>> handlersGet = contextHandlers == null ? () -> null : () -> new ArrayList<>(contextHandlers);
            boolean result = handlers != null;
            loop:
            do
                switch (next.getType()) {
                    case LABEL             -> {
                        final LabelNode labelNode = (LabelNode) next;
                        if (handlers != null) {
                            for (final TryCatchBlockNode block : handlers) {
                                contextHandlers.removeIf(it -> it.end == labelNode);
                                if (block.start == labelNode)
                                    contextHandlers += block;
                            }
                            @Nullable ExceptionHandlerLabel label = null;
                            for (final TryCatchBlockNode block : handlers)
                                if (block.handler == labelNode)
                                    if (label == null) {
                                        label = { handlersGet.get(), block.type };
                                        label.info = labelNode;
                                        labelNode.label(label);
                                    } else if (label.exception == null)
                                        label.exception = block.type;
                                    else if (block.type != null)
                                        label.exception = getCommonSuperClass.apply(label.exception, block.type);
                            if (label != null)
                                continue;
                        }
                        final ComputeLabel label = { handlersGet.get() };
                        label.info = labelNode;
                        label.mark = labelNode.labelGet() == shouldMark;
                        labelNode.label(label);
                    }
                    case JUMP_INSN         -> {
                        result = true;
                        markLabel(((JumpInsnNode) next).label);
                    }
                    case TABLESWITCH_INSN  -> {
                        final TableSwitchInsnNode switchInsnNode = (TableSwitchInsnNode) next;
                        result = true;
                        final HashSet<LabelNode> nodes = { switchInsnNode.labels };
                        for (final LabelNode label : nodes)
                            markLabel(label);
                        markLabel(switchInsnNode.dflt);
                    }
                    case LOOKUPSWITCH_INSN -> {
                        final LookupSwitchInsnNode switchInsnNode = (LookupSwitchInsnNode) next;
                        result = true;
                        final HashSet<LabelNode> nodes = { switchInsnNode.labels };
                        for (final LabelNode label : nodes)
                            markLabel(label);
                        markLabel(switchInsnNode.dflt);
                    }
                    case TYPE_INSN         -> {
                        if (next.getOpcode() == NEW) {
                            @Nullable AbstractInsnNode prev = next, at = null;
                            lookup:
                            while ((prev = prev.getPrevious()) != null)
                                switch (prev.getOpcode()) {
                                    case -1 -> {
                                        if (prev.getType() == LABEL)
                                            continue loop;
                                    }
                                    default -> {
                                        at = prev;
                                        break lookup;
                                    }
                                }
                            final LabelNode labelNode = { };
                            labelNode.label(new ComputeLabel(handlersGet.get()));
                            if (at == null)
                                instructions.insertBefore(next, labelNode);
                            else
                                instructions.insert(at, labelNode);
                        }
                    }
                }
            while ((next = next.getNext()) != null);
            return result;
        }
        
        @HotSpotMethodFlags(_force_inline)
        private static void markLabel(final LabelNode labelNode) {
            if (labelNode.labelGet() instanceof ComputeLabel computeLabel)
                computeLabel.mark = true;
            else
                labelNode.label(shouldMark);
        }
        
    }
    
    @HotSpotJIT
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PUBLIC)
    class ExceptionHandlerLabel extends ComputeLabel {
        
        String exception;
        
        final HashSet<ArrayList<TypeOwner>> locals = { };
        
        @Default
        boolean completed = false;
        
        { mark = true; }
        
        @HotSpotMethodFlags(_force_inline)
        public final void mergeExceptionHandler(final Frame frame, final BinaryOperator<String> getCommonSuperClass) {
            if (result == null)
                result = frame.dup((LabelNode) info, TypeOwner.ofThrowable(exception));
            else {
                final ArrayList<TypeOwner> owners = { frame.locals() };
                if (locals.add(owners)) {
                    completed = false;
                    Frame.merge(getCommonSuperClass, result.locals(), owners);
                }
            }
        }
        
    }
    
    @Getter
    MethodTraverser instance = () -> null;
    
    @Getter
    Sampler<String> sampler = MahoProfile.sampler();
    
    default void compute(final MethodNode methodNode, final ClassWriter writer, final @Nullable Consumer<Frame> peek = null, final ComputeType... computeTypes) = compute(methodNode, writer, peek, Set.of(computeTypes));
    
    default void compute(final MethodNode methodNode, final ClassWriter writer, final @Nullable Consumer<Frame> peek = null, final Set<ComputeType> computeTypes) {
        try (final var handle = sampler().handle("%s#%s%s -> %s".formatted(writer.name(), methodNode.name, methodNode.desc, computeTypes.stream().sorted(Enum::compareTo).map(Enum::name).collect(Collectors.joining(" | "))))) {
            methodNode.maxLocals = methodNode.maxStack = 0;
            final InsnList instructions = methodNode.instructions;
            final Frame initFrame = { };
            final LinkedList<TypeOwner> locals = initFrame.locals();
            if (ASMHelper.noneMatch(methodNode.access, Opcodes.ACC_STATIC))
                locals << TypeOwner.ofThis(Type.getObjectType(writer.name()), !ASMHelper.isInit(methodNode));
            for (final Type argumentType : Type.getArgumentTypes(methodNode.desc))
                locals << TypeOwner.of(argumentType);
            if (instructions.size() > 0) {
                final BinaryOperator<String> getCommonSuperClass = writer::getCommonSuperClass;
                final @Nullable List<TryCatchBlockNode> handlers = methodNode.tryCatchBlocks == null || methodNode.tryCatchBlocks.isEmpty() ? null : methodNode.tryCatchBlocks;
                final boolean needMarkFrame = ComputeLabel.preMark(methodNode, getCommonSuperClass);
                final ArrayList<Frame> queue = { };
                final Frame firstFrame = initFrame.dup(instructions.getFirst());
                if (firstFrame.insn() instanceof LabelNode labelNode)
                    ((ComputeLabel) labelNode.labelGet()).mark(firstFrame, firstFrame.snapshot(), getCommonSuperClass);
                queue += firstFrame;
                while (!queue.isEmpty()) {
                    final Frame frame = queue.remove(queue.size() - 1);
                    int stackSize = frame.stackSize();
                    @Nullable AbstractInsnNode next = frame.insn();
                    int opcode = next.getOpcode();
                    @Nullable List<TryCatchBlockNode> contextHandlers = null;
                    do {
                        safeCompute(frame.markInsn(next), opcode);
                        if (peek != null)
                            peek.accept(frame);
                        if (handlers != null && next.getType() == LABEL)
                            contextHandlers = ((ComputeLabel) ((LabelNode) next).labelGet()).handlers;
                        else if (opcode > NOP) {
                            if (isStore(opcode)) {
                                if (contextHandlers != null)
                                    for (final TryCatchBlockNode block : contextHandlers)
                                        ((ExceptionHandlerLabel) block.handler.labelGet()).mergeExceptionHandler(frame, getCommonSuperClass);
                                methodNode.maxLocals = max(methodNode.maxLocals, frame.localsSize());
                            }
                            final int offset = ASMHelper.stackEffectOf(next);
                            if (offset != 0)
                                methodNode.maxStack = max(methodNode.maxStack, stackSize += offset);
                            switch (next.getType()) {
                                case JUMP_INSN         -> {
                                    final JumpInsnNode jumpInsn = (JumpInsnNode) next;
                                    if (((ComputeLabel) jumpInsn.label.labelGet()).mark(frame, frame.snapshot(), getCommonSuperClass))
                                        queue += frame.dup(jumpInsn.label);
                                }
                                case TABLESWITCH_INSN  -> {
                                    final TableSwitchInsnNode switchInsn = (TableSwitchInsnNode) next;
                                    final Frame.Snapshot snapshot = frame.snapshot();
                                    final HashSet<LabelNode> nodes = { switchInsn.labels };
                                    for (final LabelNode label : nodes)
                                        if (((ComputeLabel) label.labelGet()).mark(frame, snapshot, getCommonSuperClass))
                                            queue += frame.dup(label);
                                    if (!nodes.contains(switchInsn.dflt) && ((ComputeLabel) switchInsn.dflt.labelGet()).mark(frame, snapshot, getCommonSuperClass))
                                        queue += frame.dup(switchInsn.dflt);
                                }
                                case LOOKUPSWITCH_INSN -> {
                                    final LookupSwitchInsnNode switchInsn = (LookupSwitchInsnNode) next;
                                    final Frame.Snapshot snapshot = frame.snapshot();
                                    final HashSet<LabelNode> nodes = { switchInsn.labels };
                                    for (final LabelNode label : nodes)
                                        if (((ComputeLabel) label.labelGet()).mark(frame, snapshot, getCommonSuperClass))
                                            queue += frame.dup(label);
                                    if (!nodes.contains(switchInsn.dflt) && ((ComputeLabel) switchInsn.dflt.labelGet()).mark(frame, snapshot, getCommonSuperClass))
                                        queue += frame.dup(switchInsn.dflt);
                                }
                            }
                        }
                    } while (!isStop(opcode) && (next = next.getNext()) != null &&
                            ((opcode = next.getOpcode()) != -1 || next.getType() != LABEL || ((ComputeLabel) ((LabelNode) next).labelGet()).mark(frame, frame.snapshot(), getCommonSuperClass)));
                    if (queue.isEmpty())
                        if (handlers == null)
                            break;
                        else
                            for (final TryCatchBlockNode block : handlers) {
                                final ExceptionHandlerLabel handlerLabel = (ExceptionHandlerLabel) block.handler.labelGet();
                                if (!handlerLabel.completed && handlerLabel.result != null) {
                                    handlerLabel.completed = true;
                                    queue += handlerLabel.result.dup();
                                    break; // Avoid useless calculations caused by interactions between exception handlers, so only process one at a time.
                                }
                            }
                }
                if (needMarkFrame)
                    ComputeLabel.insertFrame(instructions, initFrame.snapshot());
            }
            methodNode.maxLocals = max(methodNode.maxLocals, initFrame.localsSize());
        }
    }
    
    @Nullable BiConsumer<Frame, ComputeException> handler();
    
    default void safeCompute(final Frame frame, final int opcode) {
        switch (opcode) {
            case -1,
                    NOP,
                    GOTO -> compute(frame, opcode);
            default      -> {
                final @Nullable BiConsumer<Frame, ComputeException> handler = handler();
                if (handler != null) {
                    final Frame dup = frame.dup();
                    try {
                        compute(frame, opcode);
                    } catch (final ComputeException e) {
                        handler.accept(dup, e);
                        safeCompute(dup, opcode);
                        frame.locals() >>>= dup.locals();
                        frame.stack() >>>= dup.stack();
                    }
                } else
                    compute(frame, opcode);
            }
        }
    }
    
    @HotSpotMethodFlags(_force_inline)
    private static void compute(final Frame frame, final int opcode) {
        switch (opcode) {
            case -1,
                    NOP,
                    GOTO          -> {
            }
            case ACONST_NULL      -> pushNull(frame);
            case ICONST_M1,
                    ICONST_0,
                    ICONST_1,
                    ICONST_2,
                    ICONST_3,
                    ICONST_4,
                    ICONST_5,
                    LCONST_0,
                    LCONST_1,
                    FCONST_0,
                    FCONST_1,
                    FCONST_2,
                    DCONST_0,
                    DCONST_1,
                    BIPUSH,
                    SIPUSH,
                    LDC           -> pushConstant(frame);
            case ILOAD,
                    LLOAD,
                    FLOAD,
                    DLOAD,
                    ALOAD         -> load(frame);
            case ISTORE,
                    LSTORE,
                    FSTORE,
                    DSTORE,
                    ASTORE        -> store(frame);
            case IALOAD,
                    LALOAD,
                    FALOAD,
                    DALOAD,
                    AALOAD,
                    BALOAD,
                    CALOAD,
                    SALOAD        -> arrayLoad(frame);
            case IASTORE,
                    LASTORE,
                    FASTORE,
                    DASTORE,
                    AASTORE,
                    BASTORE,
                    CASTORE,
                    SASTORE       -> arrayStore(frame);
            case POP,
                    POP2          -> pop(frame);
            case DUP,
                    DUP_X1,
                    DUP_X2,
                    DUP2,
                    DUP2_X1,
                    DUP2_X2       -> dup(frame);
            case SWAP             -> swap(frame);
            case IADD,
                    LADD,
                    FADD,
                    DADD,
                    ISUB,
                    LSUB,
                    FSUB,
                    DSUB,
                    IMUL,
                    LMUL,
                    FMUL,
                    DMUL,
                    IDIV,
                    LDIV,
                    FDIV,
                    DDIV,
                    IREM,
                    LREM,
                    FREM,
                    DREM,
                    ISHL,
                    LSHL,
                    ISHR,
                    LSHR,
                    IUSHR,
                    LUSHR,
                    IAND,
                    LAND,
                    IOR,
                    LOR,
                    IXOR,
                    LXOR,
                    LCMP,
                    FCMPL,
                    FCMPG,
                    DCMPL,
                    DCMPG         -> binary(frame);
            case INEG,
                    LNEG,
                    FNEG,
                    DNEG          -> unary(frame);
            case IINC             -> inc(frame);
            case I2L,
                    I2F,
                    I2D,
                    L2I,
                    L2F,
                    L2D,
                    F2I,
                    F2L,
                    F2D,
                    D2I,
                    D2L,
                    D2F,
                    I2B,
                    I2C,
                    I2S           -> cast(frame);
            case CHECKCAST        -> checkCast(frame);
            case IFEQ,
                    IFNE,
                    IFLT,
                    IFGE,
                    IFGT,
                    IFLE,
                    IFNULL,
                    IFNONNULL     -> ifJump(frame);
            case IF_ICMPEQ,
                    IF_ICMPNE,
                    IF_ICMPLT,
                    IF_ICMPGE,
                    IF_ICMPGT,
                    IF_ICMPLE,
                    IF_ACMPEQ,
                    IF_ACMPNE     -> ifCmpJump(frame);
            case JSR              -> jsr(frame);
            case RET              -> ret(frame);
            case TABLESWITCH,
                    LOOKUPSWITCH  -> switchJump(frame);
            case IRETURN,
                    LRETURN,
                    FRETURN,
                    DRETURN,
                    ARETURN,
                    RETURN        -> returnType(frame);
            case GETSTATIC,
                    PUTSTATIC,
                    GETFIELD,
                    PUTFIELD      -> field(frame);
            case INVOKEVIRTUAL,
                    INVOKESPECIAL,
                    INVOKESTATIC,
                    INVOKEINTERFACE,
                    INVOKEDYNAMIC -> invoke(frame);
            case NEW              -> newInstance(frame);
            case NEWARRAY,
                    ANEWARRAY     -> newArray(frame);
            case ARRAYLENGTH      -> arrayLength(frame);
            case ATHROW           -> athrow(frame);
            case INSTANCEOF       -> instanceOf(frame);
            case MONITORENTER,
                    MONITOREXIT   -> monitor(frame);
            case MULTIANEWARRAY   -> multiNewArray(frame);
            default               -> throw new IllegalArgumentException("Unsupported opcode: " + frame.insn().getOpcode());
        }
    }
    
    @HotSpotMethodFlags(_force_inline)
    private static void pushNull(final Frame frame) = frame.push(TypeOwner.NULL);
    
    @HotSpotMethodFlags(_force_inline)
    private static void pushConstant(final Frame frame) = frame.push(switch (frame.insn().getOpcode()) {
        case ICONST_M1,
                ICONST_5,
                ICONST_4,
                ICONST_3,
                ICONST_2,
                ICONST_1,
                ICONST_0,
                BIPUSH,
                SIPUSH   -> TypeOwner.INTEGER;
        case LCONST_0,
                LCONST_1 -> TypeOwner.LONG;
        case FCONST_0,
                FCONST_1,
                FCONST_2 -> TypeOwner.FLOAT;
        case DCONST_0,
                DCONST_1 -> TypeOwner.DOUBLE;
        case LDC         -> switch (frame.<LdcInsnNode>insn().cst) {
            case Integer ignored         -> TypeOwner.INTEGER;
            case Type type               -> type.getSort() == Type.METHOD ? TypeOwner.METHOD_TYPE : TypeOwner.CLASS;
            case Handle ignored          -> TypeOwner.METHOD_HANDLE;
            case ConstantDynamic dynamic -> TypeOwner.of(Type.getType(dynamic.getDescriptor()));
            case Object cst              -> TypeOwner.of(Type.getType(TypeHelper.unboxType(cst.getClass())));
        };
        default          -> throw unreachableArea();
    });
    
    @HotSpotMethodFlags(_force_inline)
    private static void load(final Frame frame) = frame.push(checkTargetType(frame, targetType(frame.insn().getOpcode()), frame.load(frame.<VarInsnNode>insn().var)));
    
    @HotSpotMethodFlags(_force_inline)
    private static void store(final Frame frame) = frame.store(frame.<VarInsnNode>insn().var, checkTargetType(frame, targetType(frame.insn().getOpcode()), frame.pop()));
    
    @HotSpotMethodFlags(_force_inline)
    private static void arrayLoad(final Frame frame) {
        checkTargetType(frame, Type.INT_TYPE, frame.pop());
        final Type arrayType = arrayType(frame.insn().getOpcode());
        final TypeOwner array = frame.pop();
        checkTargetType(frame, arrayType, array);
        frame.push(array == TypeOwner.NULL ? TypeOwner.ofNull(ASMHelper.elementType(arrayType)) : TypeOwner.of(ASMHelper.elementType(array.type())));
    }
    
    @HotSpotMethodFlags(_force_inline)
    private static void arrayStore(final Frame frame) {
        final int opcode = frame.insn().getOpcode();
        checkTargetType(frame, targetType(opcode), frame.pop());
        checkTargetType(frame, Type.INT_TYPE, frame.pop());
        checkTargetType(frame, arrayType(opcode), frame.pop());
    }
    
    @HotSpotMethodFlags(_force_inline)
    private static void pop(final Frame frame) = checkTargetSize(frame, 1 + frame.insn().getOpcode() - POP, frame.pop());
    
    @HotSpotMethodFlags(_force_inline)
    private static void dup(final Frame frame) {
        final int opcode = frame.insn().getOpcode(), size = opcode < DUP2 ? 1 : 2, offset = opcode - (opcode < DUP2 ? DUP : DUP2), index[] = { 0 };
        for (int sum = 0; sum < offset; index[0]++)
            sum += checkTargetSize(frame, 1, offset - sum, frame.fetch(index[0] + 1)).type().getSize();
        final LinkedList<TypeOwner> values = { };
        for (int i = 0, sum = 0; sum < size; i++)
            sum += checkTargetSize(frame, 1, size - sum, frame.fetch(i)).let(values::addFirst).type().getSize();
        values.forEach(value -> frame.insert(index[0], value));
    }
    
    @HotSpotMethodFlags(_force_inline)
    private static void swap(final Frame frame) {
        final TypeOwner
                next = checkTargetSize(frame, 1, frame.pop()),
                prev = checkTargetSize(frame, 1, frame.pop());
        frame.push(next);
        frame.push(prev);
    }
    
    @HotSpotMethodFlags(_force_inline)
    private static void binary(final Frame frame) {
        final Type
                right = rightType(frame.insn().getOpcode()),
                expected = targetType(frame.insn().getOpcode());
        checkTargetType(frame, right, frame.pop());    // next
        checkTargetType(frame, expected, frame.pop()); // prev
        frame.push(TypeOwner.of(resultType(frame.insn().getOpcode())));
    }
    
    @HotSpotMethodFlags(_force_inline)
    private static void unary(final Frame frame) = frame.push(checkTargetType(frame, targetType(frame.insn().getOpcode()), frame.pop()));
    
    @HotSpotMethodFlags(_force_inline)
    private static void inc(final Frame frame) = checkTargetType(frame, Type.INT_TYPE, frame.load(frame.<IincInsnNode>insn().var));
    
    @HotSpotMethodFlags(_force_inline)
    private static void ifJump(final Frame frame) = checkTargetType(frame, targetType(frame.insn().getOpcode()), frame.pop());
    
    @HotSpotMethodFlags(_force_inline)
    private static void ifCmpJump(final Frame frame) {
        final Type expected = targetType(frame.insn().getOpcode());
        checkTargetType(frame, expected, frame.pop());
        checkTargetType(frame, expected, frame.pop());
    }
    
    @HotSpotMethodFlags(_force_inline)
    private static void cast(final Frame frame) {
        final AbstractInsnNode insn = frame.insn();
        final int opcode = insn.getOpcode();
        checkTargetType(frame, targetType(opcode), frame.pop());
        frame.push(TypeOwner.of(resultType(opcode)));
    }
    
    @HotSpotMethodFlags(_force_inline)
    private static void checkCast(final Frame frame) {
        final AbstractInsnNode insn = frame.insn();
        final Type expected = Type.getObjectType(((TypeInsnNode) insn).desc);
        checkTargetType(frame, ASMHelper.TYPE_OBJECT, frame.pop());
        frame.push(TypeOwner.of(expected));
    }
    
    @HotSpotMethodFlags(_force_inline)
    private static void jsr(final Frame frame) { throw obsoleteOpcode(frame.insn().getOpcode()); }
    
    @HotSpotMethodFlags(_force_inline)
    private static void ret(final Frame frame) { throw obsoleteOpcode(frame.insn().getOpcode()); }
    
    @HotSpotMethodFlags(_force_inline)
    private static void switchJump(final Frame frame) = checkTargetType(frame, Type.INT_TYPE, frame.pop());
    
    @HotSpotMethodFlags(_force_inline)
    private static void returnType(final Frame frame) {
        final Type expected = targetType(frame.insn().getOpcode());
        if (expected.getSort() != Type.VOID)
            checkTargetType(frame, expected, frame.pop());
    }
    
    @HotSpotMethodFlags(_force_inline)
    private static void field(final Frame frame) {
        switch (frame.insn().getOpcode()) {
            case GETSTATIC -> getStatic(frame);
            case PUTSTATIC -> putStatic(frame);
            case GETFIELD  -> getField(frame);
            case PUTFIELD  -> putField(frame);
        }
    }
    
    @HotSpotMethodFlags(_force_inline)
    private static void getStatic(final Frame frame) = frame.push(TypeOwner.of(Type.getType(frame.<FieldInsnNode>insn().desc)));
    
    @HotSpotMethodFlags(_force_inline)
    private static void putStatic(final Frame frame) = checkTargetType(frame, Type.getType(frame.<FieldInsnNode>insn().desc), frame.pop());
    
    @HotSpotMethodFlags(_force_inline)
    private static void getField(final Frame frame) {
        checkTargetType(frame, ASMHelper.TYPE_OBJECT, frame.pop());
        frame.push(TypeOwner.of(Type.getType(frame.<FieldInsnNode>insn().desc)));
    }
    
    @HotSpotMethodFlags(_force_inline)
    private static void putField(final Frame frame) {
        checkTargetType(frame, Type.getType(frame.<FieldInsnNode>insn().desc), frame.pop());
        checkTargetType(frame, Type.getObjectType(frame.<FieldInsnNode>insn().owner), frame.pop());
    }
    
    @HotSpotMethodFlags(_force_inline)
    private static void invoke(final Frame frame) {
        switch (frame.insn().getOpcode()) {
            case INVOKEVIRTUAL   -> invokeVirtual(frame);
            case INVOKESPECIAL   -> invokeSpecial(frame);
            case INVOKESTATIC    -> invokeStatic(frame);
            case INVOKEINTERFACE -> invokeInterface(frame);
            case INVOKEDYNAMIC   -> invokeDynamic(frame);
        }
    }
    
    @HotSpotMethodFlags(_force_inline)
    private static void invokeVirtual(final Frame frame) = invoke(frame, frame.<MethodInsnNode>insn().desc, frame.<MethodInsnNode>insn().owner);
    
    @HotSpotMethodFlags(_force_inline)
    private static void invokeSpecial(final Frame frame) = invoke(frame, frame.<MethodInsnNode>insn().desc, frame.<MethodInsnNode>insn().owner);
    
    @HotSpotMethodFlags(_force_inline)
    private static void invokeStatic(final Frame frame) = invoke(frame, frame.<MethodInsnNode>insn().desc);
    
    @HotSpotMethodFlags(_force_inline)
    private static void invokeInterface(final Frame frame) = invoke(frame, frame.<MethodInsnNode>insn().desc, frame.<MethodInsnNode>insn().owner);
    
    @HotSpotMethodFlags(_force_inline)
    private static void invokeDynamic(final Frame frame) = invoke(frame, frame.<InvokeDynamicInsnNode>insn().desc);
    
    @HotSpotMethodFlags(_force_inline)
    private static void invoke(final Frame frame, final String desc, final @Nullable String owner = null) {
        final Type argumentTypes[] = Type.getArgumentTypes(desc);
        for (int i = argumentTypes.length - 1; i > -1; i--)
            checkTargetType(frame, argumentTypes[i], frame.pop());
        final @Nullable TypeOwner target = owner == null ? null : checkTargetType(frame, Type.getObjectType(owner), frame.pop());
        final Type returnType = Type.getReturnType(desc);
        if (returnType.getSort() != Type.VOID)
            frame.push(TypeOwner.of(returnType));
        if (frame.insn().getOpcode() == INVOKESPECIAL && ASMHelper.isInit(frame.insn()))
            frame.erase(target.flag());
    }
    
    @HotSpotMethodFlags(_force_inline)
    private static void newInstance(final Frame frame) = frame.push(TypeOwner.ofNew(frame.insn()));
    
    @HotSpotMethodFlags(_force_inline)
    private static void newArray(final Frame frame) {
        final AbstractInsnNode insn = frame.insn();
        checkTargetType(frame, Type.INT_TYPE, frame.pop());
        frame.push(TypeOwner.of(ASMHelper.arrayType(insn instanceof TypeInsnNode typeInsn ? Type.getObjectType(typeInsn.desc) : newArrayType(((IntInsnNode) insn).operand))));
    }
    
    @HotSpotMethodFlags(_force_inline)
    private static void arrayLength(final Frame frame) {
        checkTargetIsArray(frame, frame.pop());
        frame.push(TypeOwner.INTEGER);
    }
    
    @HotSpotMethodFlags(_force_inline)
    private static void athrow(final Frame frame) = checkTargetType(frame, ASMHelper.TYPE_THROWABLE, frame.pop());
    
    @HotSpotMethodFlags(_force_inline)
    private static void instanceOf(final Frame frame) {
        checkTargetType(frame, ASMHelper.TYPE_OBJECT, frame.pop());
        frame.push(TypeOwner.INTEGER);
    }
    
    @HotSpotMethodFlags(_force_inline)
    private static void monitor(final Frame frame) = frame.pop();
    
    @HotSpotMethodFlags(_force_inline)
    private static void multiNewArray(final Frame frame) {
        final MultiANewArrayInsnNode insn = frame.insn();
        IntStream.range(0, insn.dims).forEach(length -> checkTargetType(frame, Type.INT_TYPE, frame.pop()));
        frame.push(TypeOwner.of(Type.getObjectType(insn.desc)));
    }
    
    @HotSpotMethodFlags(_force_inline)
    private static TypeOwner checkTargetType(final Frame frame, final Type expected, final TypeOwner owner) {
        final int expectedSort = expected.getSort(), valueSort = owner.type().getSort();
        if (expectedSort == valueSort)
            return owner;
        if (owner == TypeOwner.NULL && (expectedSort == Type.OBJECT || expectedSort == Type.ARRAY))
            return owner;
        if (expectedSort == Type.OBJECT && valueSort == Type.ARRAY)
            return owner;
        if (IntTypeRange.implicitlyConvertible(expected, owner.type()))
            return owner;
        throw new ComputeException.TypeMismatch(frame, expected, owner);
    }
    
    @HotSpotMethodFlags(_force_inline)
    private static TypeOwner checkTargetSize(final Frame frame, final int expected, final TypeOwner owner) {
        if (expected != owner.type().getSize())
            throw new ComputeException.SizeMismatch(frame, expected, owner);
        return owner;
    }
    
    @HotSpotMethodFlags(_force_inline)
    private static TypeOwner checkTargetSize(final Frame frame, final int min, final int max, final TypeOwner owner) {
        final int size = owner.type().getSize();
        if (min > size || max < size)
            throw new ComputeException.SizeRangeMismatch(frame, min, max, owner);
        return owner;
    }
    
    @HotSpotMethodFlags(_force_inline)
    private static TypeOwner checkTargetIsArray(final Frame frame, final TypeOwner owner) {
        if (owner != TypeOwner.NULL && owner.type().getSort() != Type.ARRAY)
            throw new ComputeException.ArrayTypeMismatch(frame, owner);
        return owner;
    }
    
    @HotSpotMethodFlags(_force_inline)
    private static IllegalArgumentException obsoleteOpcode(final int opcode) = { "Obsolete opcode: " + opcode };
    
    @HotSpotMethodFlags(_force_inline)
    private static AssertionError unreachableArea() = { "Unreachable area!" };
    
}
