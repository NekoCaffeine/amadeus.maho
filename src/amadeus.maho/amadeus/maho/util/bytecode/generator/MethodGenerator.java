package amadeus.maho.util.bytecode.generator;

import java.io.PrintStream;
import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.commons.TableSwitchGenerator;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.Bytecodes;
import amadeus.maho.util.runtime.ReflectionHelper;
import amadeus.maho.util.runtime.UnsafeHelper;

import static amadeus.maho.util.runtime.ReflectionHelper.STATIC;
import static org.objectweb.asm.Opcodes.*;

public class MethodGenerator extends MethodVisitor {
    
    public static final int
            ADD  = IADD,
            SUB  = ISUB,
            MUL  = IMUL,
            DIV  = IDIV,
            REM  = IREM,
            NEG  = INEG,
            SHL  = ISHL,
            SHR  = ISHR,
            USHR = IUSHR,
            AND  = IAND,
            OR   = IOR,
            XOR  = IXOR,
            EQ   = IFEQ,
            NE   = IFNE,
            LT   = IFLT,
            GE   = IFGE,
            GT   = IFGT,
            LE   = IFLE;
    
    public static final Type
            TYPE_UNSAFE        = Type.getObjectType(ASMHelper.className(UnsafeHelper.unsafe().getClass())),
            TYPE_SYSTEM        = Type.getObjectType(ASMHelper.className(System.class)),
            TYPE_PRINT_STREAM  = Type.getObjectType(ASMHelper.className(PrintStream.class)),
            TYPE_UNSAFE_HELPER = Type.getObjectType(ASMHelper.className(UnsafeHelper.class)),
            TYPE_LOOKUP        = Type.getObjectType(ASMHelper.className(MethodHandles.Lookup.class)),
            TYPE_METHOD_TYPE   = Type.getObjectType(ASMHelper.className(MethodType.class)),
            TYPE_METHOD_HANDLE = Type.getObjectType(ASMHelper.className(MethodHandle.class)),
            TYPE_CALL_SITE     = Type.getObjectType(ASMHelper.className(CallSite.class));
    
    public static final Method
            BOOLEAN_VALUE      = Method.getMethod("boolean booleanValue()"),
            CHAR_VALUE         = Method.getMethod("char charValue()"),
            INT_VALUE          = Method.getMethod("int intValue()"),
            FLOAT_VALUE        = Method.getMethod("float floatValue()"),
            LONG_VALUE         = Method.getMethod("long longValue()"),
            DOUBLE_VALUE       = Method.getMethod("double doubleValue()"),
            ALLOCATE_INSTANCE  = { "allocateInstance", ASMHelper.TYPE_OBJECT, new Type[]{ ASMHelper.TYPE_CLASS } },
            LOAD_CLASS         = { "loadType", ASMHelper.TYPE_CLASS, new Type[]{ ASMHelper.TYPE_STRING } },
            UNSAFE             = { "unsafe", TYPE_UNSAFE, ASMHelper.EMPTY_METHOD_ARGS },
            VALUE_OF           = { "valueOf", ASMHelper.TYPE_ENUM, new Type[]{ ASMHelper.TYPE_CLASS, ASMHelper.TYPE_STRING } },
            FOR_NAME           = { "forName", ASMHelper.TYPE_CLASS, new Type[]{ ASMHelper.TYPE_STRING } },
            GET_CLASS          = { "getClass", ASMHelper.TYPE_CLASS, ASMHelper.EMPTY_METHOD_ARGS },
            HASH_CODE          = { "hashCode", Type.INT_TYPE, ASMHelper.EMPTY_METHOD_ARGS },
            EQUALS             = { "equals", Type.BOOLEAN_TYPE, new Type[]{ ASMHelper.TYPE_OBJECT } },
            CLONE              = { "clone", ASMHelper.TYPE_OBJECT, ASMHelper.EMPTY_METHOD_ARGS },
            TO_STRING          = { "toString", ASMHelper.TYPE_STRING, ASMHelper.EMPTY_METHOD_ARGS },
            NOTIFY             = { "notify", Type.VOID_TYPE, ASMHelper.EMPTY_METHOD_ARGS },
            NOTIFY_ALL         = { "notifyAll", Type.VOID_TYPE, ASMHelper.EMPTY_METHOD_ARGS },
            WAIT               = { "wait", Type.VOID_TYPE, ASMHelper.EMPTY_METHOD_ARGS },
            WAIT_TIMEOUT       = { "wait", Type.VOID_TYPE, new Type[]{ Type.LONG_TYPE } },
            WAIT_TIMEOUT_NANOS = { "wait", Type.VOID_TYPE, new Type[]{ Type.LONG_TYPE, Type.INT_TYPE } };
    
    public static final Handle HANDLE_LAMBDA_META_FACTORY = {
            H_INVOKESTATIC,
            ASMHelper.className(LambdaMetafactory.class),
            "metafactory",
            Type.getMethodDescriptor(TYPE_CALL_SITE, TYPE_LOOKUP, ASMHelper.TYPE_STRING, TYPE_METHOD_TYPE, TYPE_METHOD_TYPE, TYPE_METHOD_HANDLE, TYPE_METHOD_TYPE),
            false
    };
    
    public final int access;
    
    public final String name, desc;
    
    public final Type methodType, returnType, argumentTypes[];
    
    public final List<Type> localTypes = new ArrayList<>();
    
    protected LinkedList<Integer> localOffsetStack = { };
    
    protected int nowLocalOffset = 0, firstLocal;
    
    public MethodGenerator(final MethodVisitor mv, final int access, final String name, final String desc) {
        super(ASMHelper.asm_api_version, mv);
        this.access = access;
        this.name = name;
        this.desc = desc;
        methodType = Type.getMethodType(desc);
        returnType = methodType.getReturnType();
        argumentTypes = methodType.getArgumentTypes();
        firstLocal = ASMHelper.baseStackSize(ASMHelper.anyMatch(access, STATIC), desc);
    }
    
    public InsnList instructions() = ((MethodNode) mv).instructions;
    
    public void push(final boolean value) = push(value ? 1 : 0);
    
    public void push(final int value) {
        if (value >= -1 && value <= 5)
            mv.visitInsn(ICONST_0 + value);
        else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE)
            mv.visitIntInsn(BIPUSH, value);
        else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE)
            mv.visitIntInsn(SIPUSH, value);
        else
            mv.visitLdcInsn(value);
    }
    
    public void push(final long value) {
        if (value == 0L || value == 1L)
            mv.visitInsn(LCONST_0 + (int) value);
        else
            mv.visitLdcInsn(value);
    }
    
    public void push(final float value) {
        final int bits = Float.floatToIntBits(value);
        if (bits == 0L || bits == 0x3F800000 || bits == 0x40000000) // 0..2
            mv.visitInsn(FCONST_0 + (int) value);
        else
            mv.visitLdcInsn(value);
    }
    
    public void push(final double value) {
        final long bits = Double.doubleToLongBits(value);
        if (bits == 0L || bits == 0x3FF0000000000000L) // +0.0d and 1.0d
            mv.visitInsn(DCONST_0 + (int) value);
        else
            mv.visitLdcInsn(value);
    }
    
    public void push(final String value) {
        if (value == null)
            mv.visitInsn(ACONST_NULL);
        else
            mv.visitLdcInsn(value);
    }
    
    public void push(final Type value) {
        if (value == null)
            pushNull();
        else if (ASMHelper.isUnboxType(value))
            getStatic(ASMHelper.boxType(value), "TYPE", ASMHelper.TYPE_CLASS);
        else
            visitLdcInsn(value);
    }
    
    public void push(final Class<?> clazz) {
        if (ReflectionHelper.anyMatch(clazz, ReflectionHelper.PUBLIC))
            push(Type.getType(clazz));
        else
            forName(Type.getType(clazz));
    }
    
    public void pushDefaultLdc(final Type type) {
        switch (type.getSort()) {
            case Type.VOID    -> { }
            case Type.INT     -> push(0);
            case Type.BOOLEAN -> push(false);
            case Type.BYTE    -> push((byte) 0);
            case Type.SHORT   -> push((short) 0);
            case Type.LONG    -> push(0L);
            case Type.FLOAT   -> push(0F);
            case Type.DOUBLE  -> push(0D);
            case Type.CHAR    -> push((char) 0);
            default           -> pushNull();
        }
    }
    
    public void pushNull() = mv.visitInsn(ACONST_NULL);
    
    public void push(final Handle handle) {
        if (handle == null)
            mv.visitInsn(ACONST_NULL);
        else
            mv.visitLdcInsn(handle);
    }
    
    public void push(final ConstantDynamic constantDynamic) {
        if (constantDynamic == null)
            mv.visitInsn(ACONST_NULL);
        else
            mv.visitLdcInsn(constantDynamic);
    }
    
    public int getArgIndex(final int arg) {
        int index = ASMHelper.noneMatch(access, ACC_STATIC) ? 1 : 0;
        for (int i = 0; i < arg; i++)
            index += argumentTypes[i].getSize();
        return index;
    }
    
    public void loadInsn(final Type type, final int index) = mv.visitVarInsn(type.getOpcode(ILOAD), index);
    
    public void storeInsn(final Type type, final int index) = mv.visitVarInsn(type.getOpcode(ISTORE), index);
    
    public void loadThis() {
        if ((access & ACC_STATIC) != 0)
            throw new IllegalStateException("no 'this' pointer within static method");
        mv.visitVarInsn(ALOAD, 0);
    }
    
    public void loadThisIfNeed() {
        if ((access & ACC_STATIC) == 0)
            mv.visitVarInsn(ALOAD, 0);
    }
    
    public void loadArg(final int arg) = loadInsn(argumentTypes[arg], getArgIndex(arg));
    
    public void loadArgs(final int arg, final int count) {
        int index = getArgIndex(arg);
        for (int i = 0; i < count; i++) {
            final Type argumentType = argumentTypes[arg + i];
            loadInsn(argumentType, index);
            index += argumentType.getSize();
        }
    }
    
    public void loadArgs() = loadArgs(0, argumentTypes.length);
    
    public void loadArg(final int arg, final Type castType) {
        loadInsn(argumentTypes[arg], getArgIndex(arg));
        if (ASMHelper.shouldCast(argumentTypes[arg], castType))
            checkCast(castType);
    }
    
    public void loadArgs(final int arg, final int count, final Type castTypes[]) {
        if (count != castTypes.length)
            throw new IllegalArgumentException("count(" + count + ") != types.length(" + castTypes.length + ")");
        int index = getArgIndex(arg);
        for (int i = 0; i < count; i++) {
            final Type argumentType = argumentTypes[arg + i];
            loadInsn(argumentType, index);
            if (ASMHelper.shouldCast(argumentType, castTypes[i]))
                checkCast(castTypes[i]);
            index += argumentType.getSize();
        }
    }
    
    public void loadArgs(final Type castTypes[]) = loadArgs(0, argumentTypes.length, castTypes);
    
    public void loadArgArray() {
        push(argumentTypes.length);
        newArray(ASMHelper.TYPE_OBJECT);
        for (int i = 0; i < argumentTypes.length; i++) {
            dup();
            push(i);
            loadArg(i);
            box(argumentTypes[i]);
            arrayStore(ASMHelper.TYPE_OBJECT);
        }
    }
    
    public void storeArg(final int arg) = storeInsn(argumentTypes[arg], getArgIndex(arg));
    
    public Type localType(final int local) = localTypes.get(local - firstLocal);
    
    public void localType(final int local, final Type type) {
        final int index = local - firstLocal;
        while (localTypes.size() < index + 1)
            localTypes.add(null);
        localTypes.set(index, type);
    }
    
    public int offset(int index) {
        int result = firstLocal;
        for (int i = 0; i < index; i++) {
            final int size = localType(firstLocal + i).getSize(), offset = size - 1;
            result += size;
            if (offset != 0) {
                i += offset;
                index += offset;
            }
        }
        return result;
    }
    
    public void loadLocal(final int local) {
        final int offset = offset(local);
        loadInsn(localType(offset), offset);
    }
    
    public void loadLocal(final int local, final Type type) {
        final int offset = offset(local);
        localType(offset, type);
        loadInsn(type, offset);
    }
    
    public void storeLocal(final int local) {
        final int offset = offset(local);
        storeInsn(localType(offset), offset);
    }
    
    public void storeLocal(final int local, final Type type) {
        final int offset = offset(local);
        localType(offset, type);
        storeInsn(type, offset);
    }
    
    public void arrayLoad(final Type type) = mv.visitInsn(type.getOpcode(IALOAD));
    
    public void arrayStore(final Type type) = mv.visitInsn(type.getOpcode(IASTORE));
    
    public void pop() = mv.visitInsn(POP);
    
    public void pop2() = mv.visitInsn(POP2);
    
    public void dup() = mv.visitInsn(DUP);
    
    public void dup2() = mv.visitInsn(DUP2);
    
    public void dupX1() = mv.visitInsn(DUP_X1);
    
    public void dupX2() = mv.visitInsn(DUP_X2);
    
    public void dup2X1() = mv.visitInsn(DUP2_X1);
    
    public void dup2X2() = mv.visitInsn(DUP2_X2);
    
    public void swap() = mv.visitInsn(SWAP);
    
    public void swap(final Type prev, final Type type) {
        if (type.getSize() == 1) {
            if (prev.getSize() == 1) {
                swap(); // Same as dupX1 pop.
            } else {
                dupX2();
                pop();
            }
        } else {
            if (prev.getSize() == 1)
                dup2X1();
            else
                dup2X2();
            pop2();
        }
    }
    
    public void math(final int op, final Type type) = mv.visitInsn(type.getOpcode(op));
    
    public void not() {
        mv.visitInsn(ICONST_1);
        mv.visitInsn(IXOR);
    }
    
    public void iinc(final int local, final int amount) = mv.visitIincInsn(local, amount);
    
    public void box(final Type type) {
        if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY)
            return;
        if (type == Type.VOID_TYPE)
            pushNull();
        else {
            final Type boxedType = ASMHelper.boxType(type);
            invokeStatic(boxedType, new Method("valueOf", boxedType, new Type[]{ type }));
        }
    }
    
    public void unbox(final Type type) {
        if (type.getSort() == Type.VOID)
            return;
        final Type boxedType = switch (type.getSort()) {
            case Type.CHAR    -> ASMHelper.TYPE_CHARACTER;
            case Type.BOOLEAN -> ASMHelper.TYPE_BOOLEAN;
            default           -> ASMHelper.TYPE_NUMBER;
        };
        final @Nullable Method unboxMethod = switch (type.getSort()) {
            case Type.CHAR    -> CHAR_VALUE;
            case Type.BOOLEAN -> BOOLEAN_VALUE;
            case Type.DOUBLE  -> DOUBLE_VALUE;
            case Type.FLOAT   -> FLOAT_VALUE;
            case Type.LONG    -> LONG_VALUE;
            case Type.INT,
                    Type.SHORT,
                    Type.BYTE -> INT_VALUE;
            default           -> null;
        };
        if (unboxMethod == null)
            checkCast(type);
        else {
            checkCast(boxedType);
            invokeVirtual(boxedType, unboxMethod);
        }
    }
    
    public Label newLabel() = { };
    
    public void mark(final Label label) = mv.visitLabel(label);
    
    public Label mark() = new Label().let(mv::visitLabel);
    
    public void ifCmp(final Type type, final int mode, final Label label) {
        if (type.getSort() == Type.ARRAY || type.getSort() == Type.OBJECT)
            switch (mode) {
                case EQ -> mv.visitJumpInsn(IF_ACMPEQ, label);
                case NE -> mv.visitJumpInsn(IF_ACMPNE, label);
                default -> throw new IllegalArgumentException("Bad comparison for type " + type);
            }
        else {
            switch (type.getSort()) {
                case Type.LONG   -> mv.visitInsn(LCMP);
                case Type.DOUBLE -> mv.visitInsn(mode == GE || mode == GT ? DCMPL : DCMPG);
                case Type.FLOAT  -> mv.visitInsn(mode == GE || mode == GT ? FCMPL : FCMPG);
                default          -> mv.visitJumpInsn(switch (mode) {
                    case EQ -> IF_ICMPEQ;
                    case NE -> IF_ICMPNE;
                    case GE -> IF_ICMPGE;
                    case LT -> IF_ICMPLT;
                    case LE -> IF_ICMPLE;
                    case GT -> IF_ICMPGT;
                    default -> throw new IllegalArgumentException("Bad comparison mode " + mode);
                }, label);
            }
            mv.visitJumpInsn(mode, label);
        }
    }
    
    public void ifICmp(final int mode, final Label label) = ifCmp(Type.INT_TYPE, mode, label);
    
    public void ifZCmp(final int mode, final Label label) = mv.visitJumpInsn(mode, label);
    
    public void ifNull(final Label label) = mv.visitJumpInsn(IFNULL, label);
    
    public void ifNonNull(final Label label) = mv.visitJumpInsn(IFNONNULL, label);
    
    public void goTo(final Label label) = mv.visitJumpInsn(GOTO, label);
    
    public void ret(final int local) = mv.visitVarInsn(RET, local);
    
    public void tableSwitch(final int keys[], final TableSwitchGenerator generator, final boolean useTable = (keys.length == 0 ? 0 : (float) keys.length / (keys[keys.length - 1] - keys[0] + 1)) >= 0.5f) {
        for (int i = 1; i < keys.length; i++)
            if (keys[i] < keys[i - 1])
                throw new IllegalArgumentException("keys must be sorted in ascending order");
        final Label defaultLabel = newLabel();
        final Label endLabel = newLabel();
        if (keys.length > 0) {
            final int numKeys = keys.length;
            if (useTable) {
                final int min = keys[0], max = keys[numKeys - 1], range = max - min + 1;
                final Label labels[] = new Label[range];
                Arrays.fill(labels, defaultLabel);
                for (final int key : keys)
                    labels[key - min] = newLabel();
                mv.visitTableSwitchInsn(min, max, defaultLabel, labels);
                for (int i = 0; i < range; i++) {
                    final Label label = labels[i];
                    if (label != defaultLabel) {
                        mark(label);
                        generator.generateCase(i + min, endLabel);
                    }
                }
            } else {
                final Label[] labels = new Label[numKeys];
                for (int i = 0; i < numKeys; i++)
                    labels[i] = newLabel();
                mv.visitLookupSwitchInsn(defaultLabel, keys, labels);
                for (int i = 0; i < numKeys; i++) {
                    mark(labels[i]);
                    generator.generateCase(keys[i], endLabel);
                }
            }
        }
        mark(defaultLabel);
        generator.generateDefault();
        mark(endLabel);
    }
    
    public void returnValue() = mv.visitInsn(returnType.getOpcode(IRETURN));
    
    public void fieldInsn(final int opcode, final Type ownerType, final String name, final Type fieldType) = mv.visitFieldInsn(opcode, ownerType.getInternalName(), name, fieldType.getDescriptor());
    
    public void fieldInsn(final int opcode, final Type ownerType, final String name, final String fieldType) = mv.visitFieldInsn(opcode, ownerType.getInternalName(), name, fieldType);
    
    public void getStatic(final Type owner, final String name, final Type type) = fieldInsn(GETSTATIC, owner, name, type);
    
    public void putStatic(final Type owner, final String name, final Type type) = fieldInsn(PUTSTATIC, owner, name, type);
    
    public void getField(final Type owner, final String name, final Type type) = fieldInsn(GETFIELD, owner, name, type);
    
    public void putField(final Type owner, final String name, final Type type) = fieldInsn(PUTFIELD, owner, name, type);
    
    public void getStatic(final Type owner, final FieldNode field) = fieldInsn(GETSTATIC, owner, field.name, field.desc);
    
    public void putStatic(final Type owner, final FieldNode field) = fieldInsn(PUTSTATIC, owner, field.name, field.desc);
    
    public void getField(final Type owner, final FieldNode field) = fieldInsn(GETFIELD, owner, field.name, field.desc);
    
    public void putField(final Type owner, final FieldNode field) = fieldInsn(PUTFIELD, owner, field.name, field.desc);
    
    public void invokeHandle(final Handle handle) = switch (handle.getTag()) {
        case H_GETFIELD  -> mv.visitFieldInsn(GETFIELD, handle.getOwner(), name, handle.getDesc());
        case H_GETSTATIC -> mv.visitFieldInsn(GETSTATIC, handle.getOwner(), name, handle.getDesc());
        case H_PUTFIELD  -> mv.visitFieldInsn(PUTFIELD, handle.getOwner(), name, handle.getDesc());
        case H_PUTSTATIC -> mv.visitFieldInsn(PUTSTATIC, handle.getOwner(), name, handle.getDesc());
        default          -> mv.visitMethodInsn(switch (handle.getTag()) {
            case H_INVOKEVIRTUAL       -> INVOKEVIRTUAL;
            case H_INVOKESTATIC        -> INVOKESTATIC;
            case H_INVOKESPECIAL,
                    H_NEWINVOKESPECIAL -> INVOKESPECIAL;
            case H_INVOKEINTERFACE     -> INVOKEINTERFACE;
            default                    -> throw new IllegalStateException("Unexpected value: " + handle.getTag());
        }, handle.getOwner(), handle.getName(), handle.getDesc(), handle.isInterface());
    };
    
    public void invokeInsn(final int opcode, final Type type, final Method method, final boolean isInterface)
            = mv.visitMethodInsn(opcode, type.getSort() == Type.ARRAY ? type.getDescriptor() : type.getInternalName(), method.getName(), method.getDescriptor(), isInterface);
    
    public void invokeVirtual(final Type owner, final Method method) = invokeInsn(INVOKEVIRTUAL, owner, method, false);
    
    public void invokeConstructor(final Type type, final Method method) = invokeInsn(INVOKESPECIAL, type, method, false);
    
    public void invokeStatic(final Type owner, final Method method) = invokeInsn(INVOKESTATIC, owner, method, false);
    
    public void invokeInterface(final Type owner, final Method method) = invokeInsn(INVOKEINTERFACE, owner, method, true);
    
    public void invokeDynamic(final String name, final String descriptor, final Handle bootstrapMethodHandle, final Object... bootstrapMethodArguments)
            = mv.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
    
    public void invokeSpecial(final Type type, final Method method, final boolean itf) = invokeInsn(INVOKESPECIAL, type, method, itf);
    
    public void invokeStatic(final Type type, final Method method, final boolean itf) = invokeInsn(INVOKESTATIC, type, method, itf);
    
    public void invokeLambda(final String name, final String interfaceMethodDesc, final Type lambdaMethodType, final Handle lambdaHandle, final Type lambdaActualType = lambdaMethodType)
            = invokeDynamic(name, interfaceMethodDesc, HANDLE_LAMBDA_META_FACTORY, lambdaMethodType, lambdaHandle, lambdaActualType);
    
    public void invokeInit(final Type type) = invokeSpecial(type, ASMHelper.METHOD_INIT_, false);
    
    public void invokeStdPrintln(final Type type) { // ?
        box(type); // Object
        getStatic(TYPE_SYSTEM, "out", TYPE_PRINT_STREAM); // Object, Object
        swap(); // Object, Object
        invokeVirtual(TYPE_PRINT_STREAM, new Method("println", Type.VOID_TYPE, new Type[]{ type }));
    }
    
    public void invokeObject(final Method method) = invokeVirtual(ASMHelper.TYPE_OBJECT, method);
    
    public void invokeUnsafe(final Method method) = invokeVirtual(ASMHelper.TYPE_UNSAFE, method);
    
    public void invokeTarget(final java.lang.reflect.Method method, final boolean special = false) {
        final Type targetType = Type.getType(method.getDeclaringClass());
        final Method targetMethod = Method.getMethod(method);
        final boolean isStatic = Modifier.isStatic(method.getModifiers()), isInterface = method.getDeclaringClass().isInterface();
        invokeInsn(isStatic ? INVOKESTATIC : isInterface ? INVOKEINTERFACE : special ? INVOKEVIRTUAL : INVOKESPECIAL, targetType, targetMethod, isInterface);
    }
    
    public void invokeTargetSpecial(final java.lang.reflect.Method method) = invokeTarget(method, true);
    
    public void invokeTarget(final Constructor<?> constructor) = invokeSpecial(Type.getType(constructor.getDeclaringClass()), Method.getMethod(constructor), false);
    
    public void invokeObjectConstructor() = invokeEmptyConstructor(ASMHelper.TYPE_OBJECT);
    
    public void invokeEmptyConstructor(final Type target) = invokeConstructor(target, ASMHelper.VOID_METHOD_DESC);
    
    public void invokeConstructor(final Type target, final String desc) = invokeConstructor(target, new Method(ASMHelper._INIT_, desc));
    
    private void typeInsn(final int opcode, final Type type) = mv.visitTypeInsn(opcode, type.getInternalName());
    
    public void newInstance(final Type type) = typeInsn(NEW, type);
    
    public void cast(final Type from, final Type to) {
        if (!from.equals(to))
            switch (from.getSort()) {
                case Type.DOUBLE -> {
                    switch (to.getSort()) {
                        case Type.FLOAT -> mv.visitInsn(D2F);
                        case Type.LONG  -> mv.visitInsn(D2L);
                        default         -> {
                            mv.visitInsn(D2I);
                            cast(Type.INT_TYPE, to);
                        }
                    }
                }
                case Type.FLOAT  -> {
                    switch (to.getSort()) {
                        case Type.DOUBLE -> mv.visitInsn(F2D);
                        case Type.LONG   -> mv.visitInsn(F2L);
                        default          -> {
                            mv.visitInsn(F2I);
                            cast(Type.INT_TYPE, to);
                        }
                    }
                }
                case Type.LONG   -> {
                    switch (to.getSort()) {
                        case Type.DOUBLE -> mv.visitInsn(L2D);
                        case Type.FLOAT  -> mv.visitInsn(L2F);
                        default          -> {
                            mv.visitInsn(L2I);
                            cast(Type.INT_TYPE, to);
                        }
                    }
                }
                default          -> {
                    switch (to.getSort()) {
                        case Type.BYTE   -> mv.visitInsn(I2B);
                        case Type.CHAR   -> mv.visitInsn(I2C);
                        case Type.DOUBLE -> mv.visitInsn(I2D);
                        case Type.FLOAT  -> mv.visitInsn(I2F);
                        case Type.LONG   -> mv.visitInsn(I2L);
                        case Type.SHORT  -> mv.visitInsn(I2S);
                        default          -> { }
                    }
                }
            }
    }
    
    public void newArray(final Type type) {
        if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY)
            mv.visitTypeInsn(ANEWARRAY, type.getInternalName());
        else
            mv.visitIntInsn(NEWARRAY, Bytecodes.newArrayType(type));
    }
    
    public void arrayLength() = mv.visitInsn(ARRAYLENGTH);
    
    public void throwException() = mv.visitInsn(ATHROW);
    
    public void throwException(final Type type, final String message) {
        newInstance(type);
        dup();
        push(message);
        invokeConstructor(type, Method.getMethod("void <init> (String)"));
        throwException();
    }
    
    public void checkCast(final Type type) {
        if (!type.equals(ASMHelper.TYPE_OBJECT))
            typeInsn(CHECKCAST, type);
    }
    
    public void checkCast(final Type from, final Type to) {
        if (!to.equals(ASMHelper.TYPE_OBJECT) && !from.equals(to))
            typeInsn(CHECKCAST, to);
    }
    
    public void instanceOf(final Type type) = typeInsn(INSTANCEOF, type);
    
    public void monitorEnter() = mv.visitInsn(MONITORENTER);
    
    public void monitorExit() = mv.visitInsn(MONITOREXIT);
    
    public void endMethod() = mv.visitEnd();
    
    public void catchException(final Label start, final Label end, final Type exception) {
        final Label catchLabel = { };
        if (exception == null)
            mv.visitTryCatchBlock(start, end, catchLabel, null);
        else
            mv.visitTryCatchBlock(start, end, catchLabel, exception.getInternalName());
        mark(catchLabel);
    }
    
    public void pushLocalOffset(final int offset) {
        nowLocalOffset += offset;
        localOffsetStack.push(offset);
    }
    
    public void popLocalOffset() = nowLocalOffset -= localOffsetStack.pop();
    
    public int nowLocalOffset() = nowLocalOffset;
    
    public void returnDefault() {
        pushDefaultLdc(returnType);
        returnValue();
    }
    
    public void dup(final Type type) {
        if (type.getSize() == 1)
            dup();
        else if (type.getSize() == 2)
            dup2();
        assert type.getSize() >= 0 && type.getSize() < 3;
    }
    
    public void pop(final Type type) {
        if (type.getSize() == 1)
            pop();
        else if (type.getSize() == 2)
            pop2();
        assert type.getSize() >= 0 && type.getSize() < 3;
    }
    
    public void broadCast(final Type from, final Type to) {
        if (!from.equals(to))
            switch (from.getSort()) {
                case Type.OBJECT,
                        Type.ARRAY -> {
                    switch (to.getSort()) {
                        case Type.OBJECT,
                                Type.ARRAY -> checkCast(to);
                        case Type.VOID     -> pop(from);
                        default            -> unbox(to);
                    }
                }
                case Type.VOID     -> {
                    if (to != Type.VOID_TYPE)
                        pushDefaultLdc(to);
                }
                default            -> {
                    switch (to.getSort()) {
                        case Type.OBJECT,
                                Type.ARRAY -> {
                            box(from);
                            checkCast(to);
                        }
                        case Type.VOID     -> pop(from);
                        default            -> cast(from, to);
                    }
                }
            }
    }
    
    public void forName(final Type type) {
        if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) {
            push(ASMHelper.sourceName(type.getInternalName()));
            invokeStatic(ASMHelper.TYPE_CLASS, FOR_NAME);
        } else
            push(type);
    }
    
    public void valueOfEnum() = invokeStatic(ASMHelper.TYPE_ENUM, VALUE_OF);
    
    public void allocateInstance(final Type type) {
        push(type);
        invokeStatic(TYPE_UNSAFE_HELPER, ALLOCATE_INSTANCE);
    }
    
    public void loadUnsafe() = invokeStatic(TYPE_UNSAFE_HELPER, UNSAFE);
    
    public void redirectInvokeToField(final Type wrapperType, final Type type, final FieldNode fieldNode) {
        if (ASMHelper.anyMatch(fieldNode.access, ACC_STATIC)) {
            loadThis();
            getField(wrapperType, fieldNode.name, Type.getType(fieldNode.desc));
        } else
            getStatic(wrapperType, fieldNode.name, Type.getType(fieldNode.desc));
        loadArgs();
        invokeVirtual(type, new Method(name, desc));
    }
    
    public void markLineNumber(final int lineNumber, final Label label) = mv.visitLineNumber(lineNumber, label);
    
    @Override
    public void visitFrame(final int type, final int numLocal, final Object[] local, final int numStack, final Object[] stack) = mv.visitFrame(type, numLocal, local, numStack, stack);
    
    public void frame(final int type, final @Nullable Object locals[], final @Nullable Object stack[]) = visitFrame(type, locals == null ? 0 : locals.length, locals, stack == null ? 0 : stack.length, stack);
    
    public void sameFrame() = frame(F_SAME, null, null);
    
    public void same1Frame(final Object stack) = frame(F_SAME1, null, new Object[]{ stack });
    
    public void appendFrame(final Object... locals) = frame(F_APPEND, locals, null);
    
    public void chopFrame(final Object... locals) = frame(F_CHOP, locals, null);
    
    public void chopFrame(final int locals) = frame(F_CHOP, new Object[locals], null);
    
    public void fullFrame(final Object locals[], final Object stack[]) = frame(F_FULL, locals, stack);
    
    public static MethodGenerator fromMethodNode(final MethodNode node) = { node, node.access, node.name, node.desc };
    
    public static MethodGenerator fromShadowMethodNode(final MethodNode node, final InsnList insnList) {
        final MethodNode shadowNode = { node.access, node.name, node.desc, node.signature, null };
        shadowNode.instructions = insnList;
        return { shadowNode, node.access, node.name, node.desc };
    }
    
    public static MethodGenerator fromExecutable(final ClassVisitor visitor, final Executable executable, final FieldNode... fieldNodes) {
        final String sourceDesc = switch (executable) {
            case Constructor<?> constructor     -> Type.getConstructorDescriptor(constructor);
            case java.lang.reflect.Method method -> Type.getMethodDescriptor(method);
        };
        final String desc = fieldNodes.length == 0 ? sourceDesc :
                Type.getMethodDescriptor(Type.getReturnType(sourceDesc),
                        Stream.concat(Stream.of(Type.getArgumentTypes(sourceDesc)), Stream.of(fieldNodes).map(fieldNode -> fieldNode.desc).map(Type::getType)).toArray(Type[]::new));
        return visitMethod(visitor, executable.getModifiers() & ~(ACC_ABSTRACT | ACC_NATIVE), executable instanceof Constructor<?> ? ASMHelper._INIT_ : executable.getName(), desc, null, // TODO signature
                Stream.of(executable.getExceptionTypes()).map(ASMHelper::className).toArray(String[]::new));
    }
    
    public static MethodGenerator visitMethod(final ClassVisitor visitor, final int access, final String name, final String desc, final @Nullable String signature, final @Nullable String exceptions[])
            = { visitor.visitMethod(access, name, desc, signature, exceptions), access, name, desc };
    
    public static void emptyConstructor(final ClassNode node) = emptyConstructor(node, ACC_PUBLIC, ASMHelper.VOID_METHOD_DESC);
    
    public static void emptyConstructor(final ClassNode node, final int access) = emptyConstructor(node, access, ASMHelper.VOID_METHOD_DESC);
    
    public static void emptyConstructor(final ClassNode node, final int access, final String desc) {
        final MethodGenerator generator = visitMethod(node, access, ASMHelper._INIT_, ASMHelper.VOID_METHOD_DESC, null, null);
        generator.loadThis();
        generator.invokeConstructor(Type.getObjectType(node.superName), desc);
        generator.returnValue();
        generator.endMethod();
    }
    
}
