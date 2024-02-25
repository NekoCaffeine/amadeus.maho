package amadeus.maho.transform.mark.base;

import java.lang.annotation.Annotation;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.function.Predicate;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.TransformerManager;
import amadeus.maho.util.annotation.AnnotationHandler;
import amadeus.maho.util.annotation.mark.DefaultClass;
import amadeus.maho.util.annotation.mark.DisallowLoading;
import amadeus.maho.util.annotation.mark.IgnoredDefaultValue;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.Bytecodes;
import amadeus.maho.util.bytecode.remap.RemapHandler;
import amadeus.maho.util.runtime.StringHelper;

import static amadeus.maho.util.annotation.AnnotationHandler.*;

public @interface At {
    
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
    class Lookup {
        
        private static final String MATCHING = "matching";
        
        public static final String WILDCARD = "<any>";
        
        public static final int WILDCARD_INT = -1;
        
        protected static final Annotation DEFAULT_AT = make(Endpoint.class, List.of(name(Endpoint::value), Endpoint.Type.HEAD));
        
        At at;
        
        RemapHandler.ASMRemapper remapper;
        
        Predicate<AbstractInsnNode> predicate = mapPredicate(at);
        
        protected List<AbstractInsnNode> findTargets(final InsnList list) {
            final List<AbstractInsnNode> result = new LinkedList<>();
            int ordinal = at.ordinal(), index = 0;
            for (final AbstractInsnNode insn : list) {
                index++;
                if (predicate.test(insn)) {
                    if (ordinal > 0) {
                        ordinal--;
                        continue;
                    }
                    if (at.offset() == 0) {
                        result += insn;
                        if (at.ordinal() != WILDCARD_INT)
                            return result;
                        continue;
                    }
                    int offset = at.offset();
                    final ListIterator<AbstractInsnNode> tmpIterator = list.iterator(offset < 0 ? index - 1 : index);
                    AbstractInsnNode target = null;
                    while (offset != 0)
                        if (offset < 0) {
                            if (!tmpIterator.hasPrevious())
                                throw new IllegalArgumentException(STR."can't offset to \{at.offset()}");
                            target = tmpIterator.previous();
                            offset++;
                        } else {
                            if (!tmpIterator.hasNext())
                                throw new IllegalArgumentException(STR."can't offset to \{at.offset()}");
                            target = tmpIterator.next();
                            offset--;
                        }
                    if (at.ordinal() != WILDCARD_INT)
                        return List.of(target);
                    result += target;
                }
            }
            return result;
        }
        
        public Predicate<AbstractInsnNode> mapPredicate(final At at) {
            final AnnotationHandler<At> handler = asOneOfUs(at);
            final List<? extends Annotation> annotations = handler.values()
                    .map(Map.Entry::getValue)
                    .cast(Annotation.class)
                    .toList();
            if (annotations.size() > 1)
                throw new IllegalArgumentException(STR."\{at} containing repeating elements");
            final Annotation annotation = annotations.isEmpty() ? DEFAULT_AT : annotations[0];
            return switch (annotation) {
                case Endpoint endpoint                     -> switch (endpoint.value()) {
                    case HEAD      -> {
                        final boolean flag[] = { true };
                        yield insnNode -> flag[0] && !(flag[0] = false);
                    }
                    case TAIL      -> insnNode -> Bytecodes.isOver(insnNode.getOpcode());
                    case FINALLY,
                         RETURN    -> insnNode -> Bytecodes.isReturn(insnNode.getOpcode());
                    case THROW     -> insnNode -> Bytecodes.isThrow(insnNode.getOpcode());
                    case EXCEPTION -> insnNode -> false;
                };
                case FieldInsn fieldInsn                   -> {
                    final String name, desc;
                    if (!at.remap())
                        name = fieldInsn.name();
                    else if (fieldInsn.name().equals(WILDCARD))
                        name = WILDCARD;
                    else
                        name = TransformerManager.runtime().mapFieldName(fieldInsn.owner(), fieldInsn.name());
                    final AnnotationHandler<FieldInsn> methodInsnHandler = asOneOfUs(fieldInsn);
                    final String sourceDesc = methodInsnHandler.isNotDefault(FieldInsn::type) ?
                            methodInsnHandler.<Type>lookupSourceValue(FieldInsn::type).getDescriptor() : fieldInsn.desc();
                    if (!at.remap())
                        desc = sourceDesc;
                    else if (sourceDesc.equals(WILDCARD))
                        desc = WILDCARD;
                    else
                        desc = remapper.mapDesc(sourceDesc);
                    yield insnNode -> insnNode instanceof FieldInsnNode it &&
                                      (fieldInsn.opcode() == WILDCARD_INT || fieldInsn.opcode() == it.getOpcode()) &&
                                      (fieldInsn.owner().equals(WILDCARD) || fieldInsn.owner().equals(it.owner)) &&
                                      (name.equals(WILDCARD) || name.equals(it.name)) &&
                                      (desc.equals(WILDCARD) || desc.equals(it.desc));
                }
                case MethodInsn methodInsn                 -> {
                    final String name, desc;
                    if (!at.remap())
                        name = methodInsn.name();
                    else if (methodInsn.name().equals(WILDCARD))
                        name = WILDCARD;
                    else if (methodInsn.desc().equals(WILDCARD))
                        name = TransformerManager.runtime().mapMethodName(methodInsn.owner(), methodInsn.name(), "");
                    else
                        name = TransformerManager.runtime().mapMethodName(methodInsn.owner(), methodInsn.name(), methodInsn.desc());
                    final AnnotationHandler<MethodInsn> methodInsnHandler = asOneOfUs(methodInsn);
                    final String sourceDesc = methodInsnHandler.isNotDefault(MethodInsn::descriptor) ?
                            MethodDescriptor.Mapper.methodDescriptor(methodInsn.descriptor(), remapper, at.remap()) : methodInsn.desc();
                    if (!at.remap())
                        desc = sourceDesc;
                    else if (sourceDesc.equals(WILDCARD))
                        desc = WILDCARD;
                    else
                        desc = remapper.mapDesc(sourceDesc);
                    yield insnNode -> insnNode instanceof MethodInsnNode it &&
                                      (methodInsn.opcode() == WILDCARD_INT || methodInsn.opcode() == it.getOpcode()) &&
                                      (methodInsn.owner().equals(WILDCARD) || methodInsn.owner().equals(it.owner)) &&
                                      (name.equals(WILDCARD) || name.equals(it.name)) &&
                                      (desc.equals(WILDCARD) || desc.equals(it.desc)) &&
                                      (methodInsn.itf() == Boolean.WILDCARD || methodInsn.itf() == (it.itf ? Boolean.TRUE : Boolean.FALSE));
                }
                case TypeInsn typeInsn                     -> {
                    final String desc;
                    final AnnotationHandler<TypeInsn> methodInsnHandler = asOneOfUs(typeInsn);
                    final String sourceDesc = methodInsnHandler.isNotDefault(TypeInsn::type) ?
                            methodInsnHandler.<Type>lookupSourceValue(TypeInsn::type).getInternalName() : typeInsn.desc();
                    if (!at.remap())
                        desc = sourceDesc;
                    else if (sourceDesc.equals(WILDCARD))
                        desc = WILDCARD;
                    else
                        desc = remapper.map(sourceDesc);
                    yield insnNode -> insnNode instanceof TypeInsnNode it &&
                                      (typeInsn.opcode() == WILDCARD_INT || typeInsn.opcode() == it.getOpcode()) &&
                                      (desc.equals(WILDCARD) || desc.equals(it.desc));
                }
                case Insn insn                             -> insnNode -> insnNode instanceof InsnNode it && (insn.opcode() == WILDCARD_INT || insn.opcode() == it.getOpcode());
                case JumpInsn jumpInsn                     -> insnNode -> insnNode instanceof JumpInsnNode it && (jumpInsn.opcode() == WILDCARD_INT || jumpInsn.opcode() == it.getOpcode());
                case AnyInsn anyInsn                       -> insnNode -> anyInsn.opcode() == WILDCARD_INT || anyInsn.opcode() == insnNode.getOpcode();
                case IntInsn intInsn                       -> insnNode -> insnNode instanceof IntInsnNode it &&
                                                                          (intInsn.opcode() == WILDCARD_INT || intInsn.opcode() == it.getOpcode()) &&
                                                                          (intInsn.operand() == WILDCARD_INT || intInsn.operand() == it.operand);
                case InvokeDynamicInsn invokeDynamicInsn   -> {
                    final String name, desc;
                    if (!at.remap())
                        name = invokeDynamicInsn.name();
                    else if (invokeDynamicInsn.name().equals(WILDCARD))
                        name = WILDCARD;
                    else if (invokeDynamicInsn.desc().equals(WILDCARD))
                        name = TransformerManager.runtime().mapMethodName(".", invokeDynamicInsn.name(), "");
                    else
                        name = TransformerManager.runtime().mapMethodName(".", invokeDynamicInsn.name(), invokeDynamicInsn.desc());
                    final AnnotationHandler<InvokeDynamicInsn> invokeDynamicInsnHandler = asOneOfUs(invokeDynamicInsn);
                    final String sourceDesc = invokeDynamicInsnHandler.isNotDefault(InvokeDynamicInsn::descriptor) ?
                            MethodDescriptor.Mapper.methodDescriptor(invokeDynamicInsn.descriptor(), remapper, at.remap()) : invokeDynamicInsn.desc();
                    if (!at.remap())
                        desc = sourceDesc;
                    else if (sourceDesc.equals(WILDCARD))
                        desc = WILDCARD;
                    else
                        desc = remapper.mapDesc(sourceDesc);
                    yield insnNode -> insnNode instanceof InvokeDynamicInsnNode it &&
                                      (invokeDynamicInsn.opcode() == WILDCARD_INT || invokeDynamicInsn.opcode() == it.getOpcode()) &&
                                      (name.equals(WILDCARD) || name.equals(it.name)) &&
                                      (desc.equals(WILDCARD) || desc.equals(it.desc));
                    
                }
                case LdcInsn ldcInsn                       -> {
                    final AnnotationHandler<LdcInsn> ldcInsnHandler = asOneOfUs(ldcInsn);
                    final Map<String, Object> mapping = ldcInsnHandler.sourceMemberValues();
                    mapping.remove(name(LdcInsn::opcode));
                    mapping.remove(name(LdcInsn::onlyType));
                    if (mapping.size() > 1)
                        throw new IllegalArgumentException(STR."\{ldcInsn} containing repeating elements");
                    final Object cst = mapping.isEmpty() ? null : mapping.values().iterator().next();
                    final Class<?> type = switch (mapping.keySet().iterator().next()) {
                        case "intValue"    -> Integer.class;
                        case "floatValue"  -> Float.class;
                        case "longValue"   -> Long.class;
                        case "doubleValue" -> Double.class;
                        case "stringValue" -> String.class;
                        case "classValue"  -> Type.class;
                        default            -> Object.class;
                    };
                    yield insnNode -> {
                        Object tmp = cst;
                        if (!ldcInsn.onlyType())
                            if (type == Type.class) {
                                tmp = Type.getType((String) cst);
                                if (at.remap())
                                    tmp = remapper.mapType((Type) tmp);
                            }
                        return insnNode instanceof LdcInsnNode it &&
                               (ldcInsn.opcode() == WILDCARD_INT || ldcInsn.opcode() == it.getOpcode()) &&
                               (ldcInsn.onlyType() ? type.isAssignableFrom(it.cst.getClass()) : tmp == null || tmp.equals(it.cst));
                    };
                    
                }
                case MultiANewArrayInsn multiANewArrayInsn -> {
                    final String desc;
                    if (!at.remap())
                        desc = multiANewArrayInsn.desc();
                    else if (multiANewArrayInsn.desc().equals(WILDCARD))
                        desc = WILDCARD;
                    else
                        desc = TransformerManager.runtime().mapInternalName(multiANewArrayInsn.desc());
                    yield insnNode -> insnNode instanceof MultiANewArrayInsnNode it &&
                                      (multiANewArrayInsn.opcode() == WILDCARD_INT || multiANewArrayInsn.opcode() == it.getOpcode()) &&
                                      (desc.equals(WILDCARD) || desc.equals(it.desc)) &&
                                      (multiANewArrayInsn.dims() == WILDCARD_INT || multiANewArrayInsn.dims() == it.dims);
                    
                }
                case VarInsn varInsn                       -> insnNode -> insnNode instanceof VarInsnNode it &&
                                                                          (varInsn.opcode() == WILDCARD_INT || varInsn.opcode() == it.getOpcode()) &&
                                                                          (varInsn.var() == WILDCARD_INT || varInsn.var() == it.var);
                case IincInsn iincInsn                     -> insnNode -> insnNode instanceof IincInsnNode it &&
                                                                          (iincInsn.opcode() == WILDCARD_INT || iincInsn.opcode() == it.getOpcode()) &&
                                                                          (iincInsn.var() == WILDCARD_INT || iincInsn.var() == it.var) &&
                                                                          (iincInsn.incr() == WILDCARD_INT || iincInsn.incr() == it.incr);
                case LineNumber lineNumber                 -> insnNode -> insnNode instanceof LineNumberNode it &&
                                                                          (lineNumber.value() == WILDCARD_INT || lineNumber.value() == it.line);
                default                                    -> throw new UnsupportedOperationException(annotation.toString());
            };
        }
        
        public static List<AbstractInsnNode> findTargets(final At at, final RemapHandler.ASMRemapper remapper, final InsnList list) = new Lookup(at, remapper).findTargets(list);
        
        public static Predicate<MethodNode> methodNodeChecker(final String selector, final String methodName) {
            final Predicate<String> predicate = selector(selector, methodName);
            return methodNode -> predicate.test(methodNode.name);
        }
        
        public static Predicate<String> selector(final String selector, String name) = switch (selector) {
            case WILDCARD           -> _ -> true;
            case StringHelper.EMPTY -> (switch (name = dropInvalidPart(name)) {
                case "_init_"   -> ASMHelper._INIT_;
                case "_clinit_" -> ASMHelper._CLINIT_;
                default         -> name;
            })::equals;
            default                 -> selector.isJavaIdentifierPart() ? (Predicate<String>) selector::equals : selector.matchPredicate();
        };
        
        public static @Nullable String dropInvalidPart(final String name) = StringHelper.dropStartsWith(name, "_$");
        
    }
    
    int ordinal() default Lookup.WILDCARD_INT;
    
    int offset() default 0;
    
    boolean remap() default true;
    
    @interface Endpoint {
        
        enum Type {HEAD, TAIL, RETURN, THROW, FINALLY, EXCEPTION}
        
        Type value() default Type.HEAD;
        
    }
    
    @IgnoredDefaultValue(Lookup.MATCHING)
    Endpoint endpoint() default @Endpoint;
    
    @interface FieldInsn {
        
        int opcode() default Lookup.WILDCARD_INT;
        
        String owner() default Lookup.WILDCARD;
        
        String name() default Lookup.WILDCARD;
        
        String desc() default Lookup.WILDCARD;
        
        @DisallowLoading
        Class<?> type() default DefaultClass.class;
        
    }
    
    @IgnoredDefaultValue(Lookup.MATCHING)
    FieldInsn field() default @FieldInsn;
    
    @interface MethodInsn {
        
        int opcode() default Lookup.WILDCARD_INT;
        
        String owner() default Lookup.WILDCARD;
        
        String name() default Lookup.WILDCARD;
        
        String desc() default Lookup.WILDCARD;
        
        MethodDescriptor descriptor() default @MethodDescriptor;
        
        Boolean itf() default Boolean.WILDCARD;
        
    }
    
    @IgnoredDefaultValue(Lookup.MATCHING)
    MethodInsn method() default @MethodInsn;
    
    @interface TypeInsn {
        
        int opcode() default Opcodes.MULTIANEWARRAY;
        
        String desc() default Lookup.WILDCARD;
        
        @DisallowLoading
        Class<?> type() default DefaultClass.class;
        
    }
    
    @IgnoredDefaultValue(Lookup.MATCHING)
    TypeInsn type() default @TypeInsn;
    
    @interface Insn {
        
        int opcode() default Lookup.WILDCARD_INT;
        
    }
    
    @IgnoredDefaultValue(Lookup.MATCHING)
    Insn insn() default @Insn;
    
        @interface JumpInsn {
        
        int opcode() default Lookup.WILDCARD_INT;
        
    }
    
    @IgnoredDefaultValue(Lookup.MATCHING)
    JumpInsn jumpInsn() default @JumpInsn;
    
    @interface AnyInsn {
        
        int opcode() default Lookup.WILDCARD_INT;
        
    }
    
    @IgnoredDefaultValue(Lookup.MATCHING)
    AnyInsn any() default @AnyInsn;
    
    @interface IntInsn {
        
        int opcode() default Lookup.WILDCARD_INT;
        
        int operand() default Lookup.WILDCARD_INT;
        
    }
    
    @IgnoredDefaultValue(Lookup.MATCHING)
    IntInsn intInsn() default @IntInsn;
    
    @interface InvokeDynamicInsn {
        
        int opcode() default Opcodes.INVOKEDYNAMIC;
        
        String name() default Lookup.WILDCARD;
        
        String desc() default Lookup.WILDCARD;
        
        MethodDescriptor descriptor() default @MethodDescriptor;
        
    }
    
    @IgnoredDefaultValue(Lookup.MATCHING)
    InvokeDynamicInsn invokeDynamic() default @InvokeDynamicInsn;
    
    @interface LdcInsn {
        
        int opcode() default Opcodes.LDC;
        
        boolean onlyType() default false;
        
        int intValue() default 0;
        
        float floatValue() default 0.0F;
        
        long longValue() default 0L;
        
        double doubleValue() default 0.0;
        
        String stringValue() default "";
        
        String classValue() default "";
        
    }
    
    @IgnoredDefaultValue(Lookup.MATCHING)
    LdcInsn ldc() default @LdcInsn;
    
    @interface MultiANewArrayInsn {
        
        int opcode() default Opcodes.MULTIANEWARRAY;
        
        String desc() default Lookup.WILDCARD;
        
        int dims() default 2;
        
    }
    
    @IgnoredDefaultValue(Lookup.MATCHING)
    MultiANewArrayInsn muitiANewArray() default @MultiANewArrayInsn;
    
    @interface VarInsn {
        
        int opcode() default Lookup.WILDCARD_INT;
        
        int var() default 0;
        
    }
    
    @IgnoredDefaultValue(Lookup.MATCHING)
    VarInsn var() default @VarInsn;
    
    @interface IincInsn {
        
        int opcode() default Opcodes.IINC;
        
        int var() default Lookup.WILDCARD_INT;
        
        int incr() default Lookup.WILDCARD_INT;
        
    }
    
    @IgnoredDefaultValue(Lookup.MATCHING)
    IincInsn iinc() default @IincInsn;
    
    @interface LineNumber {
        
        int value() default Lookup.WILDCARD_INT;
        
    }
    
    @IgnoredDefaultValue(Lookup.MATCHING)
    LineNumber lineNumber() default @LineNumber(-1);
    
}
