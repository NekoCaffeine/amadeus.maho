package amadeus.maho.util.bytecode;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.annotation.IncompleteAnnotationException;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.internal.misc.Unsafe;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypeReference;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.commons.ModuleHashesAttribute;
import org.objectweb.asm.commons.ModuleResolutionAttribute;
import org.objectweb.asm.commons.ModuleTargetAttribute;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeAnnotationNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;
import org.objectweb.asm.util.TraceMethodVisitor;

import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.GhostContext;
import amadeus.maho.util.annotation.AnnotationHandler;
import amadeus.maho.util.bytecode.generator.MethodGenerator;
import amadeus.maho.util.bytecode.tree.DynamicVarInsnNode;
import amadeus.maho.util.bytecode.type.TypePathFilter;
import amadeus.maho.util.function.FunctionHelper;
import amadeus.maho.util.misc.ConstantLookup;
import amadeus.maho.util.misc.Environment;
import amadeus.maho.util.runtime.ArrayHelper;
import amadeus.maho.util.runtime.ObjectHelper;
import amadeus.maho.util.runtime.StreamHelper;
import amadeus.maho.util.runtime.TypeHelper;
import amadeus.maho.util.throwable.ExtraInformationThrowable;

import static org.objectweb.asm.Opcodes.*;

public interface ASMHelper {
    
    ConstantLookup
            OPCODE_LOOKUP = new ConstantLookup().recording(ASMHelper.class),
            ACCESS_LOOKUP = new ConstantLookup().recording(field -> field.getName().startsWith("ACC_"), Opcodes.class);
    
    String
            OBJECT_NAME      = "java/lang/Object",
            CLASS_NAME       = "java/lang/Class",
            STRING_NAME      = "java/lang/String",
            THROWABLE_NAME   = "java/lang/Throwable",
            OBJECT_DESC      = "Ljava/lang/Object;",
            _INIT_           = "<init>",
            _CLINIT_         = "<clinit>",
            VOID_METHOD_DESC = "()V";
    
    Type
            TYPE_BYTE                            = Type.getType(Byte.class),
            TYPE_BYTE_ARRAY                      = Type.getType(byte[].class),
            TYPE_BOOLEAN                         = Type.getType(Boolean.class),
            TYPE_BOOLEAN_ARRAY                   = Type.getType(boolean[].class),
            TYPE_SHORT                           = Type.getType(Short.class),
            TYPE_SHORT_ARRAY                     = Type.getType(short[].class),
            TYPE_CHARACTER                       = Type.getType(Character.class),
            TYPE_CHAR_ARRAY                      = Type.getType(char[].class),
            TYPE_INTEGER                         = Type.getType(Integer.class),
            TYPE_INT_ARRAY                       = Type.getType(int[].class),
            TYPE_FLOAT                           = Type.getType(Float.class),
            TYPE_FLOAT_ARRAY                     = Type.getType(float[].class),
            TYPE_LONG                            = Type.getType(Long.class),
            TYPE_LONG_ARRAY                      = Type.getType(long[].class),
            TYPE_DOUBLE                          = Type.getType(Double.class),
            TYPE_DOUBLE_ARRAY                    = Type.getType(double[].class),
            TYPE_NUMBER                          = Type.getType(Number.class),
            TYPE_OBJECT                          = Type.getType(Object.class),
            TYPE_OBJECT_ARRAY                    = Type.getType(Object[].class),
            TYPE_CLASS                           = Type.getType(Class.class),
            TYPE_STRING                          = Type.getType(String.class),
            TYPE_CHAR_SEQUENCE                   = Type.getType(CharSequence.class),
            TYPE_THROWABLE                       = Type.getType(Throwable.class),
            TYPE_VOID                            = Type.getType(Void.class),
            TYPE_ENUM                            = Type.getType(Enum.class),
            TYPE_LIST                            = Type.getType(List.class),
            TYPE_MAP                             = Type.getType(Map.class),
            TYPE_SET                             = Type.getType(Set.class),
            TYPE_UNSAFE                          = Type.getType(Unsafe.class),
            TYPE_CALL_SITE                       = Type.getType(CallSite.class),
            TYPE_METHOD_HANDLES_LOOKUP           = Type.getType(MethodHandles.Lookup.class),
            TYPE_METHOD_TYPE                     = Type.getType(MethodType.class),
            TYPE_METHOD_HANDLE                   = Type.getType(MethodHandle.class),
            TYPE_INCOMPATIBLE_CLASS_CHANGE_ERROR = Type.getType(IncompatibleClassChangeError.class),
            TYPE_GHOST_CONTEXT                   = Type.getType(GhostContext.class),
            EMPTY_METHOD_ARGS[]                  = new Type[0];
    
    Method
            METHOD_INIT_   = { _INIT_, VOID_METHOD_DESC },
            METHOD_CLINIT_ = { _CLINIT_, VOID_METHOD_DESC };
    
    int ACCESS_MODIFIERS = ACC_PUBLIC | ACC_PROTECTED | ACC_PRIVATE;
    
    int asm_api_version = Environment.local().lookup("amadeus.maho.asm.api.version", ASM9);
    
    static boolean anyMatch(final int access, final int mask) = (access & mask) != 0;
    
    static boolean allMatch(final int access, final int mask) = (access & mask) == mask;
    
    static boolean noneMatch(final int access, final int mask) = (access & mask) == 0;
    
    static IntPredicate anyMatch(final int mask) = access -> (access & mask) != 0;
    
    static IntPredicate allMatch(final int mask) = access -> (access & mask) == mask;
    
    static IntPredicate noneMatch(final int mask) = access -> (access & mask) == 0;
    
    static boolean isInit(final MethodNode methodNode) = methodNode.name.equals(_INIT_);
    
    static boolean isClinit(final MethodNode methodNode) = methodNode.name.equals(_CLINIT_);
    
    static boolean isInit(final MethodInsnNode methodInsn) = methodInsn.name.equals(_INIT_);
    
    static boolean isClinit(final MethodInsnNode methodInsn) = methodInsn.name.equals(_CLINIT_);
    
    Map<Type, Type> PRIMITIVE_MAPPING = Collections.unmodifiableMap(Stream.of(TypeHelper.Wrapper.values())
            .collect(Collectors.toMap(wrapper -> Type.getType(wrapper.primitiveType()), wrapper -> Type.getType(wrapper.wrapperType()), FunctionHelper.last(), HashMap::new)));
    
    static boolean isUnboxType(final Type type) = PRIMITIVE_MAPPING.containsKey(type);
    
    static boolean isBoxType(final Type type) = PRIMITIVE_MAPPING.containsValue(type);
    
    static Type boxType(final Type type) = PRIMITIVE_MAPPING.getOrDefault(type, type);
    
    static Type unboxType(final Type type) = PRIMITIVE_MAPPING.entrySet().stream()
            .filter(entry -> entry.getValue().equals(type))
            .map(Map.Entry::getKey)
            .findAny()
            .orElse(type);
    
    static boolean shouldCast(final Type sourceType, final @Nullable Type castType) = castType != null && !sourceType.equals(castType) && shouldCast(castType);
    
    static boolean shouldCast(final Type castType) = (castType.getSort() == Type.OBJECT || castType.getSort() == Type.ARRAY) && !castType.equals(TYPE_OBJECT);
    
    static Handle handle(final java.lang.reflect.Method method)
        = { Modifier.isStatic(method.getModifiers()) ? H_INVOKESTATIC : H_INVOKEVIRTUAL, Type.getInternalName(method.getDeclaringClass()), method.getName(), Type.getMethodDescriptor(method), method.getDeclaringClass().isInterface() };
    
    static int returnOpcode(final Type type) = switch (type.getSort()) {
        case Type.BYTE,
             Type.SHORT,
             Type.INT,
             Type.BOOLEAN -> IRETURN;
        case Type.LONG    -> LRETURN;
        case Type.FLOAT   -> FRETURN;
        case Type.DOUBLE  -> DRETURN;
        case Type.OBJECT,
             Type.ARRAY   -> ARETURN;
        default           -> RETURN;
    };
    
    static int loadOpcode(final Type type) = switch (type.getSort()) {
        case Type.BYTE,
             Type.SHORT,
             Type.INT,
             Type.BOOLEAN -> ILOAD;
        case Type.LONG    -> LLOAD;
        case Type.FLOAT   -> FLOAD;
        case Type.DOUBLE  -> DLOAD;
        default           -> ALOAD;
    };
    
    static int storeOpcode(final Type type) = switch (type.getSort()) {
        case Type.BYTE,
             Type.SHORT,
             Type.INT,
             Type.BOOLEAN -> ISTORE;
        case Type.LONG    -> LSTORE;
        case Type.FLOAT   -> FSTORE;
        case Type.DOUBLE  -> DSTORE;
        default           -> ASTORE;
    };
    
    static AbstractInsnNode intNode(final int value) {
        if (value <= 5 && -1 <= value)
            return new InsnNode(ICONST_M1 + value + 1);
        if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE)
            return new IntInsnNode(BIPUSH, value);
        if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE)
            return new IntInsnNode(SIPUSH, value);
        return new LdcInsnNode(value);
    }
    
    static int changeAccess(final int srcAccess, final int newAccess) = srcAccess & ~ACCESS_MODIFIERS | newAccess & ACCESS_MODIFIERS;
    
    static boolean isAnonymousInnerClass(final String name) = name.matches(".*\\$\\d+");
    
    static String normalName(final String name) = name.replace("[", "_L").replaceAll("[().;/]", "_");
    
    static String className(final Class<?> clazz) = className(clazz.getName());
    
    static String className(final String clazz) = clazz.indexOf('L') == 0 && clazz.indexOf(';') == clazz.length() - 1 ? clazz.replaceFirst("L", "").replace(";", "") : clazz.replace('.', '/');
    
    static String classDesc(final Class<?> clazz) = Type.getDescriptor(clazz);
    
    static String classDesc(final String name) = STR."L\{className(name)};";
    
    static String sourceName(final String name) = className(name).replace('/', '.');
    
    static Type arrayType(final Type type, final int dim = 1) = dim < 1 ? type : Type.getType("[".repeat(dim) + type.getDescriptor());
    
    static Type elementType(final Type type) = type.getSort() == Type.ARRAY ? Type.getType(type.getDescriptor().substring(1)) : type;
    
    static int baseStackSize(final boolean isStatic, final String desc) {
        int result = isStatic ? 0 : 1;
        for (final Type type : Type.getArgumentTypes(desc))
            result += type.getSize();
        return result;
    }
    
    static int varSize(final int opcode) = switch (opcode) {
        case DLOAD,
             DSTORE,
             LLOAD,
             LSTORE -> 2;
        default     -> 1;
    };
    
    static MethodNode computeMethodNode(final ClassNode node, final String name, final String desc, final BiFunction<String, String, MethodNode> mapping)
        = lookupMethodNode(node, name, desc).orElseGet(() -> mapping.apply(name, desc).let(node.methods::add));
    
    static FieldNode computeField(final ClassNode node, final String name, final String desc, final BiFunction<String, String, FieldNode> mapping)
        = lookupFieldNode(node, name, desc).orElseGet(() -> mapping.apply(name, desc).let(node.fields::add));
    
    static Optional<MethodNode> lookupMethodNode(final ClassNode node, final String name, final String desc)
        = node.methods.stream().filter(methodNode -> methodNode.name.equals(name) && methodNode.desc.equals(desc)).findAny();
    
    static Optional<FieldNode> lookupFieldNode(final ClassNode node, final String name, final String desc)
        = node.fields.stream().filter(fieldNode -> fieldNode.name.equals(name) && fieldNode.desc.equals(desc)).findAny();
    
    static void injectInsnList(final MethodNode methodNode, final Consumer<MethodGenerator> consumer)
        = methodNode.instructions.insert(new InsnList().let(it -> consumer.accept(MethodGenerator.fromShadowMethodNode(methodNode, it))));
    
    static void injectInsnList(final MethodNode methodNode, final Predicate<AbstractInsnNode> predicate, final boolean before, final Consumer<MethodGenerator> consumer)
        = StreamHelper.fromIterable(methodNode.instructions)
                .filter(predicate)
                .forEach(insn -> {
                    final InsnList injectList = { };
                    consumer.accept(MethodGenerator.fromShadowMethodNode(methodNode, injectList));
                    if (before)
                        methodNode.instructions.insertBefore(insn, injectList);
                    else
                        methodNode.instructions.insert(insn, injectList);
                });
    
    static void injectInsnListByBytecodes(final MethodNode methodNode, final IntPredicate predicate, final boolean before, final Consumer<MethodGenerator> consumer)
        = injectInsnList(methodNode, insn -> predicate.test(insn.getOpcode()), before, consumer);
    
    static void delAllAnnotation(final ClassNode node, final Set<Class<? extends Annotation>> annotationTypes) {
        delAnnotation(node, annotationTypes);
        for (final FieldNode fieldNode : node.fields)
            delAnnotation(fieldNode, annotationTypes);
        for (final MethodNode methodNode : node.methods)
            delAnnotation(methodNode, annotationTypes);
    }
    
    static void delAnnotation(final ClassNode node, final Set<Class<? extends Annotation>> annotationTypes) {
        delAnnotation(node.visibleAnnotations, annotationTypes);
        delAnnotation(node.invisibleAnnotations, annotationTypes);
    }
    
    static void delAnnotation(final FieldNode node, final Set<Class<? extends Annotation>> annotationTypes) {
        delAnnotation(node.visibleAnnotations, annotationTypes);
        delAnnotation(node.invisibleAnnotations, annotationTypes);
        delAnnotation(node.visibleTypeAnnotations, annotationTypes);
        delAnnotation(node.invisibleTypeAnnotations, annotationTypes);
    }
    
    static void delAnnotation(final MethodNode node, final Set<Class<? extends Annotation>> annotationTypes) {
        delAnnotation(node.visibleAnnotations, annotationTypes);
        delAnnotation(node.invisibleAnnotations, annotationTypes);
        delAnnotation(node.visibleTypeAnnotations, annotationTypes);
        delAnnotation(node.invisibleTypeAnnotations, annotationTypes);
        delAnnotation(node.visibleLocalVariableAnnotations, annotationTypes);
        delAnnotation(node.invisibleLocalVariableAnnotations, annotationTypes);
        if (node.visibleParameterAnnotations != null)
            for (final List<? extends AnnotationNode> annotationNodes : node.visibleParameterAnnotations)
                delAnnotation(annotationNodes, annotationTypes);
        if (node.invisibleParameterAnnotations != null)
            for (final List<? extends AnnotationNode> annotationNodes : node.invisibleParameterAnnotations)
                delAnnotation(annotationNodes, annotationTypes);
    }
    
    static void delAnnotation(final @Nullable List<? extends AnnotationNode> annotationNodes, final Set<Class<? extends Annotation>> annotationTypes) {
        if (annotationNodes == null)
            return;
        for (final Class<? extends Annotation> annotationType : annotationTypes) {
            final Repeatable repeatable = annotationType.getAnnotation(Repeatable.class);
            if (repeatable != null) {
                final Class<?> containerType = repeatable.value();
                if (!Annotation.class.isAssignableFrom(annotationType))
                    throw new IncompatibleClassChangeError(STR."\{annotationType} is not an annotation target");
                final String containerDesc = classDesc(containerType);
                annotationNodes.removeIf(annotationNode -> annotationNode.desc.equals(containerDesc));
            } else {
                final String annotationDesc = classDesc(annotationType);
                annotationNodes.removeIf(annotationNode -> annotationNode.desc.equals(annotationDesc));
            }
        }
    }
    
    static @Nullable List<? extends AnnotationNode> lookupAnnotationNodes(final ClassNode node, final Class<? extends Annotation> annotationType)
        = isRuntimeAnnotation(annotationType) ? node.visibleAnnotations : node.invisibleAnnotations;
    
    static boolean hasAnnotation(final ClassNode node, final Class<? extends Annotation> annotationType)
        = hasAnnotation(lookupAnnotationNodes(node, annotationType), annotationType);
    
    static <T extends Annotation> @Nullable T findAnnotation(final ClassNode node, final Class<T> annotationType, final @Nullable ClassLoader loader)
        = findAnnotation(lookupAnnotationNodes(node, annotationType), annotationType, loader);
    
    static <T extends Annotation> @Nullable T[] findAnnotations(final ClassNode node, final Class<T> annotationType, final @Nullable ClassLoader loader)
        = findAnnotations(lookupAnnotationNodes(node, annotationType), annotationType, loader);
    
    static @Nullable List<? extends AnnotationNode> lookupAnnotationNodes(final FieldNode node, final Class<? extends Annotation> annotationType)
        = isRuntimeAnnotation(annotationType) ? node.visibleAnnotations : node.invisibleAnnotations;
    
    static boolean hasAnnotation(final FieldNode node, final Class<? extends Annotation> annotationType)
        = hasAnnotation(lookupAnnotationNodes(node, annotationType), annotationType);
    
    static <T extends Annotation> @Nullable T findAnnotation(final FieldNode node, final Class<T> annotationType, final @Nullable ClassLoader loader)
        = findAnnotation(lookupAnnotationNodes(node, annotationType), annotationType, loader);
    
    static <T extends Annotation> @Nullable T[] findAnnotations(final FieldNode node, final Class<T> annotationType, final @Nullable ClassLoader loader)
        = findAnnotations(lookupAnnotationNodes(node, annotationType), annotationType, loader);
    
    static @Nullable List<? extends AnnotationNode> lookupAnnotationNodes(final MethodNode node, final Class<? extends Annotation> annotationType)
        = isRuntimeAnnotation(annotationType) ? node.visibleAnnotations : node.invisibleAnnotations;
    
    static boolean hasAnnotation(final MethodNode node, final Class<? extends Annotation> annotationType)
        = hasAnnotation(lookupAnnotationNodes(node, annotationType), annotationType);
    
    static <T extends Annotation> @Nullable T findAnnotation(final MethodNode node, final Class<T> annotationType, final @Nullable ClassLoader loader)
        = findAnnotation(lookupAnnotationNodes(node, annotationType), annotationType, loader);
    
    static <T extends Annotation> @Nullable T[] findAnnotations(final MethodNode node, final Class<T> annotationType, final @Nullable ClassLoader loader)
        = findAnnotations(lookupAnnotationNodes(node, annotationType), annotationType, loader);
    
    static boolean hasAnnotation(final @Nullable List<? extends AnnotationNode> annotationNodes, final Class<? extends Annotation> annotationType)
        = findAnnotationNode(annotationNodes, annotationType) != null;
    
    static @Nullable AnnotationNode findAnnotationNode(final @Nullable List<? extends AnnotationNode> targetAnnotationNodes, final Class<? extends Annotation> annotationType) {
        if (targetAnnotationNodes == null)
            return null;
        final String desc = classDesc(annotationType);
        for (final AnnotationNode annotationNode : targetAnnotationNodes)
            if (annotationNode.desc.equals(desc))
                return annotationNode;
        final @Nullable AnnotationNode annotationNodes[] = findAnnotationNodes(targetAnnotationNodes, annotationType);
        return annotationNodes != null && annotationNodes.length > 0 ? annotationNodes[0] : null;
    }
    
    static @Nullable AnnotationNode[] findAnnotationNodes(final @Nullable List<? extends AnnotationNode> annotationNodes, final Class<? extends Annotation> annotationType) {
        if (annotationNodes == null)
            return null;
        final @Nullable Repeatable repeatable = annotationType.getAnnotation(Repeatable.class);
        if (repeatable == null)
            return null;
        final Class<? extends Annotation> containerType = repeatable.value();
        if (!Annotation.class.isAssignableFrom(annotationType))
            throw new IncompatibleClassChangeError(STR."\{annotationType} is not an annotation target");
        final String containerDesc = classDesc(containerType);
        for (final AnnotationNode annotationNode : annotationNodes)
            if (annotationNode.desc.equals(containerDesc)) {
                final Map<String, Object> mapping = AnnotationHandler.valueToMap(annotationNode.values);
                final @Nullable Object value = mapping["value"];
                if (value == null)
                    throw new IllegalArgumentException(new IncompleteAnnotationException(containerType, "value"));
                if (!List.class.isAssignableFrom(value.getClass()))
                    throw new IllegalArgumentException(new ClassCastException(STR."@\{containerType.getName()}.value() is not an instance of \{containerType.getName()}[]"));
                final List<AnnotationNode> values = (List<AnnotationNode>) value;
                if (values.isEmpty())
                    return { };
                final String annotationDesc = classDesc(annotationType);
                for (final AnnotationNode subAnnotationNode : values)
                    if (!subAnnotationNode.desc.equals(annotationDesc))
                        throw new IllegalArgumentException(new ClassCastException(STR."@\{containerType.getName()}.value() is not an instance of \{containerType.getName()}[]"));
                return values.toArray(AnnotationNode[]::new);
            }
        return null;
    }
    
    static <T extends Annotation> @Nullable T findAnnotation(final @Nullable List<? extends AnnotationNode> annotationNodes, final Class<T> annotationType, final @Nullable ClassLoader loader) {
        final @Nullable AnnotationNode node = findAnnotationNode(annotationNodes, annotationType);
        return node == null ? null : AnnotationHandler.make(annotationType, loader, node.values);
    }
    
    static <T extends Annotation> @Nullable T[] findAnnotations(final @Nullable List<? extends AnnotationNode> annotationNodes, final Class<T> annotationType, final @Nullable ClassLoader loader) {
        final @Nullable AnnotationNode nodes[] = findAnnotationNodes(annotationNodes, annotationType);
        return nodes == null ? null : Stream.of(nodes)
                .map(node -> AnnotationHandler.make(annotationType, loader, node.values))
                .toArray(size -> (T[]) Array.newInstance(annotationType, size));
    }
    
    static List<? extends TypeAnnotationNode> filterTypeReference(final List<? extends AnnotationNode> annotationNodes, final TypeReference reference, final TypePathFilter typePathFilter) {
        final int typeRef = reference.getValue();
        return annotationNodes.stream()
                .cast(TypeAnnotationNode.class)
                .filter(node -> node.typeRef == typeRef)
                .filter(node -> typePathFilter.test(node.typePath))
                .toList();
    }
    
    static Map<Class<? extends Annotation>, Annotation> findAnnotations(final @Nullable List<AnnotationNode> annotationNodes, final @Nullable ClassLoader loader) {
        if (annotationNodes == null)
            return Map.of();
        return annotationNodes.stream()
                .collect(HashMap::new, (map, annotationNode) -> {
                    final Class<?> annotationType = loadType(Type.getType(annotationNode.desc), false, loader);
                    if (!Annotation.class.isAssignableFrom(annotationType))
                        throw new IncompatibleClassChangeError(STR."\{annotationType} is not an annotation target");
                    final Annotation annotation = AnnotationHandler.make((Class<? extends Annotation>) annotationType, loader, annotationNode.values);
                    map.put((Class<? extends Annotation>) annotationType, annotation);
                }, Map::putAll);
    }
    
    static boolean corresponding(final AnnotationNode annotationNode, final Class<?> annotationType) = annotationNode.desc.equals(classDesc(annotationType));
    
    static boolean isRuntimeAnnotation(final Class<? extends Annotation> annotationType) {
        final Retention retention = annotationType.getAnnotation(Retention.class);
        return retention != null && retention.value() == RetentionPolicy.RUNTIME;
    }
    
    static int follow(final int target, final int source, final int value) = (target & value) != 0 ? source | value : source & ~value;
    
    static void generateGetter(final ClassNode node, final String owner = node.name, final FieldNode field, int access = ACC_PUBLIC, final @Nullable String name = null) {
        final boolean isStatic = anyMatch(field.access, ACC_STATIC);
        final Type fieldType = Type.getType(field.desc);
        access = follow(field.access, access, ACC_STATIC);
        final MethodGenerator generator = MethodGenerator.visitMethod(node, access, name == null ? field.name : name,
                Type.getMethodDescriptor(fieldType), field.signature == null ? null : STR."()\{field.signature}", null);
        if (isStatic)
            generator.getStatic(Type.getObjectType(owner), field.name, fieldType);
        else {
            generator.loadThis();
            generator.getField(Type.getObjectType(owner), field.name, fieldType);
        }
        generator.returnValue();
        generator.endMethod();
    }
    
    static void generateSetter(final ClassNode node, final String owner, final FieldNode field, int access = ACC_PUBLIC, final @Nullable String name = null) {
        final boolean isStatic = anyMatch(field.access, ACC_STATIC);
        final Type fieldType = Type.getType(field.desc);
        access = follow(field.access, access, ACC_STATIC);
        final MethodGenerator generator = MethodGenerator.visitMethod(node, access, name == null ? field.name : name,
                Type.getMethodDescriptor(Type.VOID_TYPE, fieldType), field.signature == null ? null : STR."(\{field.signature})V", null);
        if (isStatic) {
            generator.loadArg(0);
            generator.putStatic(Type.getObjectType(owner), field.name, fieldType);
        } else {
            generator.loadThis();
            generator.loadArg(0);
            generator.putField(Type.getObjectType(owner), field.name, fieldType);
        }
        generator.returnValue();
        generator.endMethod();
    }
    
    static void generateProxy(final ClassNode node, final String owner, final FieldNode field, final int access, final @Nullable String getterName, final @Nullable String setterName) {
        generateGetter(node, owner, field, access, getterName);
        generateSetter(node, owner, field, access, setterName);
    }
    
    static void generateProxy(final ClassNode node, final String owner, final FieldNode field, final int access) = generateProxy(node, owner, field, access, null, null);
    
    static void generateStaticGetter(final ClassNode node, final String owner, final FieldNode field, int access, final @Nullable String name) {
        final boolean isStatic = anyMatch(field.access, ACC_STATIC);
        final Type fieldType = Type.getType(field.desc);
        access = follow(field.access, access, ACC_STATIC);
        final MethodGenerator generator = MethodGenerator.visitMethod(node, access, name == null ? field.name : name,
                Type.getMethodDescriptor(fieldType, isStatic ? new Type[0] : new Type[]{ Type.getObjectType(owner) }),
                field.signature == null ? null : STR."(\{isStatic ? "" : className(node.name)})\{field.signature}", null);
        if (isStatic)
            generator.getStatic(Type.getObjectType(owner), field.name, fieldType);
        else {
            generator.loadArgs();
            generator.getField(Type.getObjectType(owner), field.name, fieldType);
        }
        generator.returnValue();
        generator.endMethod();
    }
    
    static void generateStaticSetter(final ClassNode node, final String owner, final FieldNode field, int access, final @Nullable String name) {
        final boolean isStatic = anyMatch(field.access, ACC_STATIC);
        final Type fieldType = Type.getType(field.desc);
        access = follow(field.access, access, ACC_STATIC);
        final MethodGenerator generator = MethodGenerator.visitMethod(node, access, name == null ? field.name : name,
                Type.getMethodDescriptor(Type.VOID_TYPE, isStatic ? new Type[]{ fieldType } : new Type[]{ Type.getObjectType(owner), fieldType }),
                field.signature == null ? null : STR."(\{isStatic ? "" : className(node.name)}\{field.signature})V", null);
        if (isStatic) {
            generator.loadArg(0);
            generator.putStatic(Type.getObjectType(owner), field.name, fieldType);
        } else {
            generator.loadArgs();
            generator.putField(Type.getObjectType(owner), field.name, fieldType);
        }
        generator.returnValue();
        generator.endMethod();
    }
    
    static void generateStaticProxy(final ClassNode node, final String owner, final FieldNode field, final int access, final @Nullable String getterName, final @Nullable String setterName) {
        generateStaticGetter(node, owner, field, access, getterName);
        generateStaticSetter(node, owner, field, access, setterName);
    }
    
    static void generateStaticProxy(final ClassNode node, final String owner, final FieldNode field, final int access) = generateStaticProxy(node, owner, field, access, null, null);
    
    static void useGetter(final FieldNode field, final String owner, final ClassNode node, final Method proxy) = node.methods.stream()
            .filter(target -> !corresponding(target, proxy))
            .forEach(target -> useGetter(field, owner, target, proxy));
    
    static void useGetter(final FieldNode field, final String owner, final MethodNode target, final Method proxy) = useProxy(field, owner, target, proxy, GETSTATIC, GETFIELD);
    
    static void useSetter(final FieldNode field, final String owner, final ClassNode node, final Method proxy) = node.methods.stream()
            .filter(target -> !corresponding(target, proxy))
            .forEach(target -> useSetter(field, owner, target, proxy));
    
    static void useSetter(final FieldNode field, final String owner, final MethodNode target, final Method proxy) = useProxy(field, owner, target, proxy, PUTSTATIC, PUTFIELD);
    
    static void useProxy(final FieldNode field, final String owner, final MethodNode target, final Method proxy, final int staticFieldOpcode, final int nonStaticFieldOpcode) {
        final boolean isStatic = anyMatch(field.access, ACC_STATIC);
        final int fieldOpcode = isStatic ? staticFieldOpcode : nonStaticFieldOpcode,
                methodOpcode = isStatic ? INVOKESTATIC : INVOKEVIRTUAL;
        for (final ListIterator<AbstractInsnNode> iterator = target.instructions.iterator(); iterator.hasNext(); ) {
            final AbstractInsnNode insn = iterator.next();
            if (insn.getOpcode() == fieldOpcode && corresponding(field, owner, (FieldInsnNode) insn))
                iterator.set(new MethodInsnNode(methodOpcode, owner, proxy.getName(), proxy.getDescriptor(), false));
        }
    }
    
    static boolean isSuperCall(final MethodNode method, final AbstractInsnNode insn, final String owner) = findSuperCall(method, owner) == insn;
    
    static @Nullable MethodInsnNode findSuperCall(final MethodNode method, final String owner) {
        int mark = 0;
        for (final AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode methodInsn) {
                if (methodInsn.name.equals(_INIT_) && methodInsn.owner.equals(owner))
                    if (mark == 0)
                        return methodInsn;
                    else
                        mark--;
            } else if (insn instanceof TypeInsnNode typeInsn)
                if (typeInsn.getOpcode() == NEW && typeInsn.desc.equals(owner))
                    mark++;
        }
        return null;
    }
    
    static int findCandidateParameter(final Type parameters[], final Type parameter) {
        for (int i = 0; i < parameters.length; i++)
            if (parameters[i].equals(parameter))
                return i;
        return -1;
    }
    
    static int[] findCandidateParameters(final Type parameters[], final Type parameter) {
        int result[] = ArrayHelper.EMPTY_INT_ARRAY;
        for (int i = 0; i < parameters.length; i++)
            if (parameters[i].equals(parameter))
                result = ArrayHelper.add(result, i);
        return result;
    }
    
    static int findAnnotatedParameter(final MethodNode methodNode, final Class<? extends Annotation> annotationType) {
        if (methodNode.visibleParameterAnnotations != null)
            for (int i = 0, length = methodNode.visibleParameterAnnotations.length; i < length; i++)
                if (hasAnnotation(methodNode.invisibleParameterAnnotations[i], annotationType))
                    return i;
        return -1;
    }
    
    static int[] findAnnotatedParameters(final MethodNode methodNode, final Class<? extends Annotation> annotationType) {
        int result[] = ArrayHelper.EMPTY_INT_ARRAY;
        if (methodNode.visibleParameterAnnotations != null)
            for (int i = 0, length = methodNode.visibleParameterAnnotations.length; i < length; i++)
                if (hasAnnotation(methodNode.visibleParameterAnnotations[i], annotationType))
                    result = ArrayHelper.add(result, i);
        return result;
    }
    
    static void rollback(final MethodNode methodNode, final Consumer<MethodNode> consumer) {
        final boolean rollback = noneMatch(methodNode.access, ACC_NATIVE);
        if (rollback) {
            final LabelNode start = { }, end = { };
            final TryCatchBlockNode tryCatchBlock = { start, end, end, null };
            final InsnList sourceList = methodNode.instructions;
            final boolean contextCall = hasGhostContextCall(sourceList);
            final int contextIndex = 0;
            methodNode.instructions = { };
            methodNode.instructions.add(start);
            consumer.accept(methodNode);
            methodNode.instructions.add(end);
            if (contextCall)
                methodNode.instructions.add(new DynamicVarInsnNode(ASTORE, contextIndex));
            else
                methodNode.instructions.add(new InsnNode(POP));
            methodNode.instructions.add(sourceList);
            if (contextCall) {
                for (final ListIterator<AbstractInsnNode> iterator = methodNode.instructions.iterator(); iterator.hasNext(); )
                    if (isGhostContextCall(iterator.next()))
                        iterator.set(new DynamicVarInsnNode(ALOAD, contextIndex));
                DynamicVarInsnNode.normalizationInsnList(methodNode.instructions);
            }
            methodNode.tryCatchBlocks += tryCatchBlock;
        } else {
            methodNode.access &= ~ACC_NATIVE;
            consumer.accept(methodNode);
        }
    }
    
    static boolean hasGhostContextCall(final InsnList insnList) {
        for (final AbstractInsnNode insn : insnList)
            if (isGhostContextCall(insn))
                return true;
        return false;
    }
    
    static boolean isGhostContextCall(final AbstractInsnNode insn) = insn instanceof MethodInsnNode methodInsnNode && methodInsnNode.owner.equals(TYPE_GHOST_CONTEXT.getInternalName());
    
    static boolean corresponding(final FieldNode field, final FieldNode target) = ObjectHelper.equals(field.name, target.name)
                                                                                  && ObjectHelper.equals(field.desc, target.desc);
    
    static boolean corresponding(final FieldNode field, final String owner, final FieldInsnNode fieldInsn) = ObjectHelper.equals(field.name, fieldInsn.name)
                                                                                                             && ObjectHelper.equals(owner, fieldInsn.owner)
                                                                                                             && ObjectHelper.equals(field.desc, fieldInsn.desc);
    
    static boolean corresponding(final FieldNode field, final Field target) = ObjectHelper.equals(field.name, target.getName())
                                                                              && ObjectHelper.equals(field.desc, Type.getDescriptor(target.getType()));
    
    static boolean corresponding(final FieldNode field, final String owner, final Field target) = ObjectHelper.equals(field.name, target.getName())
                                                                                                  && ObjectHelper.equals(owner, className(target.getDeclaringClass().getName()))
                                                                                                  && ObjectHelper.equals(field.desc, Type.getDescriptor(target.getType()));
    
    static boolean corresponding(final MethodNode method, final MethodNode target) = ObjectHelper.equals(method.name, target.name)
                                                                                     && ObjectHelper.equals(method.desc, target.desc);
    
    static boolean corresponding(final MethodNode method, final String owner, final MethodInsnNode methodInsn) = ObjectHelper.equals(method.name, methodInsn.name)
                                                                                                                 && ObjectHelper.equals(owner, methodInsn.owner)
                                                                                                                 && ObjectHelper.equals(method.desc, methodInsn.desc);
    
    static boolean corresponding(final Method method, final String owner, final MethodInsnNode methodInsn) = ObjectHelper.equals(method.getName(), methodInsn.name)
                                                                                                             && ObjectHelper.equals(owner, methodInsn.owner)
                                                                                                             && ObjectHelper.equals(method.getDescriptor(), methodInsn.desc);
    
    static boolean corresponding(final MethodNode method, final Method target) = ObjectHelper.equals(method.name, target.getName())
                                                                                 && ObjectHelper.equals(method.desc, target.getDescriptor());
    
    static boolean corresponding(final FieldNode field, final java.lang.reflect.Method target) = ObjectHelper.equals(field.name, target.getName())
                                                                                                 && ObjectHelper.equals(field.desc, Type.getMethodDescriptor(target));
    
    static boolean corresponding(final FieldNode field, final String owner, final java.lang.reflect.Method target) = ObjectHelper.equals(field.name, target.getName())
                                                                                                                     && ObjectHelper.equals(owner, className(target.getDeclaringClass().getName()))
                                                                                                                     && ObjectHelper.equals(field.desc, Type.getMethodDescriptor(target));
    
    static AbstractInsnNode defaultLdcNode(final Type type) = switch (type.getSort()) {
        case Type.INT     -> intNode(0);
        case Type.BOOLEAN -> new LdcInsnNode(false);
        case Type.BYTE    -> new LdcInsnNode((byte) 0);
        case Type.SHORT   -> new LdcInsnNode((short) 0);
        case Type.LONG    -> new LdcInsnNode(0L);
        case Type.FLOAT   -> new LdcInsnNode(0F);
        case Type.DOUBLE  -> new LdcInsnNode(0D);
        case Type.CHAR    -> new LdcInsnNode((char) 0);
        default           -> new InsnNode(ACONST_NULL);
    };
    
    static void clearMethod(final MethodNode method) {
        final Type returnType = Type.getReturnType(method.desc);
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        method.localVariables.clear();
        if (returnType.getSort() != Type.VOID)
            method.instructions.add(defaultLdcNode(returnType));
        method.instructions.add(new InsnNode(returnOpcode(returnType)));
    }
    
    static void removeInvoke(final InsnList list, final AbstractInsnNode insn, final boolean removeFirst, final int baseOffset = 0) {
        AbstractInsnNode prev;
        int offset = stackEffectOf(insn, false) + baseOffset;
        ListIterator<AbstractInsnNode> iterator = list.iterator(list.indexOf(insn));
        if (removeFirst)
            list.remove(insn);
        while (offset != 0) {
            if (!iterator.hasPrevious())
                return;
            prev = iterator.previous();
            offset += stackEffectOf(prev, true);
            if (prev instanceof LabelNode) {
                removeInvoke(list, (LabelNode) prev, offset);
                list.toArray();
                iterator = list.iterator(list.indexOf(prev));
                list.remove(prev);
            } else
                iterator.remove();
        }
    }
    
    static void removeInvoke(final InsnList list, final LabelNode labelNode, final int offset) {
        for (ListIterator<AbstractInsnNode> iterator = list.iterator(); iterator.hasNext(); ) {
            final AbstractInsnNode insn = iterator.next();
            if (insn instanceof JumpInsnNode jumpInsn && jumpInsn.label == labelNode) {
                removeInvoke(list, insn, false, offset);
                iterator = list.iterator(list.indexOf(insn));
                list.remove(insn);
            }
        }
    }
    
    static int stackEffectOf(final AbstractInsnNode insn, final boolean calcReturn = true) {
        final int opcode = insn.getOpcode();
        return switch (opcode) {
            case -1              -> 0;
            case LDC             -> {
                final Object cst = ((LdcInsnNode) insn).cst;
                if (cst instanceof ConstantDynamic dynamic)
                    yield Type.getType(dynamic.getDescriptor()).getSize();
                if (cst instanceof Long || cst instanceof Double)
                    yield 2;
                yield 1;
            }
            case INVOKEVIRTUAL,
                 INVOKESPECIAL,
                 INVOKESTATIC,
                 INVOKEINTERFACE -> argumentsAndReturnStackEffect(((MethodInsnNode) insn).desc, calcReturn) + Bytecodes.stackEffectOf(opcode);
            case INVOKEDYNAMIC   -> argumentsAndReturnStackEffect(((InvokeDynamicInsnNode) insn).desc, calcReturn);
            case GETSTATIC       -> Type.getType(((FieldInsnNode) insn).desc).getSize();
            case PUTSTATIC       -> -Type.getType(((FieldInsnNode) insn).desc).getSize();
            case GETFIELD        -> Type.getType(((FieldInsnNode) insn).desc).getSize() - 1;
            case PUTFIELD        -> -Type.getType(((FieldInsnNode) insn).desc).getSize() - 1;
            default              -> Bytecodes.stackEffectOf(opcode);
        };
    }
    
    static int argumentsAndReturnStackEffect(final String methodDescriptor, final boolean calcReturn = true) {
        int result = 0;
        int offset = 1;
        int ch = methodDescriptor.charAt(offset);
        while (ch != ')') {
            if (ch == 'J' || ch == 'D') {
                offset++;
                result -= 2;
            } else {
                while (methodDescriptor.charAt(offset) == '[')
                    offset++;
                if (methodDescriptor.charAt(offset++) == 'L')
                    offset = Math.max(offset, methodDescriptor.indexOf(';', offset) + 1);
                result -= 1;
            }
            ch = methodDescriptor.charAt(offset);
        }
        return calcReturn ? result + switch (methodDescriptor.charAt(offset + 1)) {
            case 'V' -> 0;
            case 'J',
                 'D' -> 2;
            default  -> 1;
        } : result;
    }
    
    static void replace(final InsnList insnList, final AbstractInsnNode source, final AbstractInsnNode target) {
        insnList.insert(source, target);
        insnList.remove(source);
    }
    
    static void replace(final InsnList insnList, final AbstractInsnNode source, final InsnList target) {
        insnList.insert(source, target);
        insnList.remove(source);
    }
    
    static ClassNode requestMinVersion(final ClassNode node, final int minVersion) {
        if (comparableVersionNumber(node.version) < comparableVersionNumber(minVersion))
            node.version = minVersion;
        return node;
    }
    
    static long comparableVersionNumber(final int version) = (long) (version >>> 16) | version << 16;
    
    @SneakyThrows
    static ClassReader newClassReader(final Path path) = { Files.newInputStream(path) };
    
    static ClassNode newClassNode(final ClassNode node) = newClassNode(ClassWriter.toBytecode(node::accept));
    
    Attribute attributePrototypes[] = { new ModuleHashesAttribute(), new ModuleResolutionAttribute(), new ModuleTargetAttribute() };
    
    static ClassNode newClassNode(final byte data[], final int flags = 0) {
        final ClassReader reader = { data };
        final ClassNode result = { };
        reader.accept(result, attributePrototypes, flags);
        return result;
    }
    
    static ClassNode newClassNode(final ClassReader reader, final int flags = 0) {
        final ClassNode result = { };
        reader.accept(result, flags);
        return result;
    }
    
    static void addBytecodeInfo(final Throwable target, final byte bytecode[]) = target.addSuppressed(new ExtraInformationThrowable(STR."bytecode: \n\{dumpBytecode(new ClassReader(bytecode))}"));
    
    @SneakyThrows
    static Class<?> loadType(final Type type, final boolean initialize = false, final @Nullable ClassLoader loader) = switch (type.getSort()) {
        case Type.VOID    -> void.class;
        case Type.BOOLEAN -> boolean.class;
        case Type.BYTE    -> byte.class;
        case Type.CHAR    -> char.class;
        case Type.SHORT   -> short.class;
        case Type.INT     -> int.class;
        case Type.LONG    -> long.class;
        case Type.FLOAT   -> float.class;
        case Type.DOUBLE  -> double.class;
        default           -> Class.forName(type.getSort() == Type.ARRAY ? type.getDescriptor().replace('/', '.') : type.getClassName(), initialize, loader);
    };
    
    static Class<?>[] loadTypes(final Stream<Type> types, final boolean initialize = false, final @Nullable ClassLoader loader) = types.map(type -> loadType(type, false, loader)).toArray(Class<?>[]::new);
    
    static MethodType loadMethodType(final String desc, final @Nullable ClassLoader loader)
        = MethodType.methodType(loadType(Type.getReturnType(desc), false, loader), loadTypes(Stream.of(Type.getArgumentTypes(desc)), loader));
    
    @SneakyThrows
    static java.lang.reflect.Method loadMethod(final Type owner, final String name, final String desc, final @Nullable ClassLoader loader)
        = loadType(owner, loader).getDeclaredMethod(name, loadTypes(Stream.of(Type.getArgumentTypes(desc)), loader));
    
    static String forName(final Type type) = type.getSort() == Type.ARRAY ? type.getDescriptor().replace('/', '.') : type.getClassName();
    
    static void printBytecode(final ClassReader reader, final OutputStream outputStream = System.out) = reader.accept(new TraceClassVisitor(new PrintWriter(outputStream)), 0);
    
    static String dumpBytecode(final ClassReader reader) = new StringWriter().let(writer -> reader.accept(new TraceClassVisitor(new PrintWriter(writer)), 0)).toString();
    
    static void printBytecode(final ClassNode node, final OutputStream outputStream = System.out) = node.accept(new TraceClassVisitor(new PrintWriter(outputStream)));
    
    static String dumpBytecode(final ClassNode node) = new StringWriter().let(writer -> node.accept(new TraceClassVisitor(new PrintWriter(writer)))).toString();
    
    @SneakyThrows
    static void printBytecode(final MethodNode node, final OutputStream outputStream = System.out) {
        final Textifier printer = { };
        final TraceMethodVisitor visitor = { printer };
        node.accept(visitor);
        printer.text.stream()
                .map(Object::toString)
                .map(String::getBytes)
                .forEach(bytes -> {
                    try {
                        outputStream.write(bytes);
                    } catch (final IOException ignored) { }
                });
        outputStream.flush();
    }
    
    static String dumpBytecode(final MethodNode node) {
        final Textifier printer = { };
        final TraceMethodVisitor visitor = { printer };
        node.accept(visitor);
        return printer.text.stream().map(Object::toString).collect(Collectors.joining());
    }
    
}
