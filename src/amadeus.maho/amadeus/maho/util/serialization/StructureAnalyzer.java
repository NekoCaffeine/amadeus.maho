package amadeus.maho.util.serialization;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.stream.Stream;

import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.ClassNode;

import amadeus.maho.core.extension.MagicAccessor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.ClassWriter;
import amadeus.maho.util.bytecode.ComputeType;
import amadeus.maho.util.bytecode.generator.MethodGenerator;
import amadeus.maho.util.runtime.MethodHandleHelper;
import amadeus.maho.util.runtime.ReflectionHelper;
import amadeus.maho.util.runtime.TypeHelper;
import amadeus.maho.util.runtime.UnsafeHelper;

import static amadeus.maho.util.bytecode.generator.MethodGenerator.*;
import static org.objectweb.asm.Opcodes.*;

public class StructureAnalyzer {
    
    public static final Type
            TYPE_BYTE_BUFFER          = Type.getType(ByteBuffer.class),
            TYPE_I_SERIALIZER         = Type.getType(Serializer.class),
            TYPE_I_SERIALIZER_CONTEXT = Type.getType(Serializer.Context.class);
    
    public static final Method
            LOOKUP_SERIALIZER = { "lookupSerializer", Type.getMethodDescriptor(TYPE_I_SERIALIZER, ASMHelper.TYPE_CLASS) },
            INSTANTIATION     = { "instantiation", Type.getMethodDescriptor(ASMHelper.TYPE_OBJECT, ASMHelper.TYPE_CLASS) },
            SERIALIZATION     = { "serialization", Type.getMethodDescriptor(Type.VOID_TYPE, TYPE_I_SERIALIZER_CONTEXT, TYPE_BYTE_BUFFER, ASMHelper.TYPE_OBJECT) },
            DESERIALIZATION   = { "deserialization", Type.getMethodDescriptor(ASMHelper.TYPE_OBJECT, TYPE_I_SERIALIZER_CONTEXT, TYPE_BYTE_BUFFER, ASMHelper.TYPE_OBJECT) };
    
    protected static final int
            INDEX_CONTEXT  = 0,
            INDEX_BUFFER   = 1,
            INDEX_INSTANCE = 2;
    
    @SneakyThrows
    public <T> Serializer<T> analysis(final Class<T> clazz) {
        final Type type = Type.getType(clazz);
        final ClassNode node = { };
        node.name = ASMHelper.className(clazz.getName() + "$Serializer");
        node.access = ACC_PUBLIC | ACC_FINAL | ACC_SYNTHETIC;
        node.superName = MagicAccessor.Bridge;
        node.interfaces += TYPE_I_SERIALIZER.getInternalName();
        node.version = V1_8;
        {
            final MethodGenerator generator = visitMethod(node, ACC_PUBLIC | ACC_FINAL | ACC_SYNTHETIC, SERIALIZATION.getName(), SERIALIZATION.getDescriptor(), null, null);
            if (ASMHelper.isBoxType(type)) {
                generator.loadArg(INDEX_BUFFER);
                // [ByteBuffer(buffer)]
                generator.loadArg(INDEX_INSTANCE);
                // [ByteBuffer(buffer), T(instance)]
                put(generator, clazz);
                // [ByteBuffer(buffer)] ?(unboxValue)
                generator.pop(ASMHelper.TYPE_OBJECT);
                // []
            } else {
                final int dim = type.getSort() != Type.ARRAY ? 0 : type.getDimensions();
                if (dim == 0) {
                    Stream.of(clazz.getDeclaredFields())
                            .filter(this::shouldNotSerialization)
                            .sorted(comparator())
                            .forEach(field -> {
                                final Class<?> fieldType = field.getType();
                                if (fieldType.isPrimitive()) {
                                    generator.loadArg(INDEX_BUFFER);
                                    // [ByteBuffer(buffer)]
                                    generator.loadArg(INDEX_INSTANCE);
                                    // [ByteBuffer(buffer), T(instance)]
                                    generator.getField(type, field.getName(), Type.getType(fieldType));
                                    // [ByteBuffer(buffer), ?]
                                    put(generator, fieldType);
                                    // [ByteBuffer(buffer)] ?(field)
                                    generator.pop();
                                    // []
                                } else {
                                    generator.loadArg(INDEX_CONTEXT);
                                    // [Context(context)]
                                    generator.push(fieldType);
                                    // [Context(context), Class<?>]
                                    generator.invokeInterface(TYPE_I_SERIALIZER_CONTEXT, LOOKUP_SERIALIZER);
                                    // [Serializer<?>]
                                    generator.loadArg(INDEX_CONTEXT);
                                    // [Serializer<?>, Context(context)]
                                    generator.loadArg(INDEX_BUFFER);
                                    // [Serializer<?>, Context(context), ByteBuffer(buffer)]
                                    generator.loadArg(INDEX_INSTANCE);
                                    // [Serializer<?>, Context(context), ByteBuffer(buffer), T(instance)]
                                    generator.getField(type, field.getName(), Type.getType(fieldType));
                                    // [Serializer<?>, Context(context), ByteBuffer(buffer), ?]
                                    generator.invokeInterface(TYPE_I_SERIALIZER, SERIALIZATION);
                                    // []
                                }
                            });
                } else {
                    final int index = generator.nowLocalOffset(), array = index + 1, max = array + 1, contextSerializer = max + 1;
                    final Type elementType = ASMHelper.arrayType(type.getElementType(), dim - 1);
                    final Class<?> elementClass = clazz.getComponentType();
                    if (!elementClass.isPrimitive()) {
                        generator.loadArg(INDEX_CONTEXT);
                        // [Context(context)]
                        generator.push(elementType);
                        // [Context(context), Class<?>]
                        generator.invokeInterface(TYPE_I_SERIALIZER_CONTEXT, LOOKUP_SERIALIZER);
                        // [Serializer<?>]
                        generator.storeLocal(contextSerializer);
                        // []
                    }
                    generator.push(-1);
                    // [int(-1)]
                    generator.storeLocal(index, Type.INT_TYPE);
                    // []
                    generator.loadArg(INDEX_INSTANCE);
                    // [T(instance)]
                    generator.dup(ASMHelper.TYPE_OBJECT);
                    // [T(instance), T(instance)]
                    generator.storeLocal(array, type);
                    // [T(instance)]
                    generator.arrayLength();
                    // [int]
                    generator.dup(Type.INT_TYPE);
                    // [int, int]
                    generator.loadArg(INDEX_BUFFER);
                    // [int, int, ByteBuffer(buffer)]
                    generator.swap(Type.INT_TYPE, ASMHelper.TYPE_OBJECT);
                    // [int, ByteBuffer(buffer), int]
                    put(generator, int.class);
                    // [int, ByteBuffer(buffer)] int(arrayLength)
                    generator.pop(ASMHelper.TYPE_OBJECT);
                    // [int]
                    generator.storeLocal(max, Type.INT_TYPE);
                    // []
                    final Label start = generator.mark(), end = generator.newLabel();
                    generator.loadLocal(index);
                    // [int(index)]
                    generator.push(1);
                    // [int(index), int(1)]
                    generator.math(ADD, Type.INT_TYPE);
                    // [int] index++
                    generator.dup(Type.INT_TYPE);
                    // [int, int]
                    generator.loadLocal(max);
                    // [int, int, int]
                    generator.ifICmp(GE, end);
                    // [int]
                    generator.dup(Type.INT_TYPE);
                    // [int, int]
                    generator.storeLocal(index);
                    // [int]
                    generator.loadLocal(array);
                    // [int(index), T(instance)]
                    generator.swap(Type.INT_TYPE, ASMHelper.TYPE_OBJECT);
                    // [T(instance), int(index)]
                    generator.arrayLoad(elementType);
                    // [?]
                    if (elementClass.isPrimitive()) {
                        generator.loadArg(INDEX_BUFFER);
                        // [?, ByteBuffer(buffer)]
                        generator.swap(elementType, ASMHelper.TYPE_OBJECT);
                        // [ByteBuffer(buffer), ?]
                        put(generator, elementClass);
                        // [ByteBuffer(buffer)] ?(elementValue)
                        generator.pop(ASMHelper.TYPE_OBJECT);
                        // []
                    } else {
                        final Label defaultSerializer = generator.newLabel(), write = generator.newLabel();
                        generator.dup(elementType);
                        // [?, ?]
                        generator.ifNull(defaultSerializer);
                        // [?]
                        generator.invokeObject(GET_CLASS);
                        // [Class<?>]
                        generator.dup(ASMHelper.TYPE_OBJECT);
                        // [Class<?>, Class<?>]
                        generator.push(elementType);
                        // [Class<?>, Class<?>, Class<?>]
                        generator.ifCmp(ASMHelper.TYPE_CLASS, EQ, defaultSerializer);
                        // [Class<?>]
                        generator.loadArg(INDEX_CONTEXT);
                        // [Class<?>, Context(context)]
                        generator.swap(ASMHelper.TYPE_OBJECT, ASMHelper.TYPE_OBJECT);
                        // [Context(context), Class<?>]
                        generator.invokeInterface(TYPE_I_SERIALIZER_CONTEXT, LOOKUP_SERIALIZER);
                        // [Serializer<?>]
                        generator.goTo(write);
                        generator.mark(defaultSerializer);
                        generator.pop(ASMHelper.TYPE_OBJECT);
                        // []
                        generator.loadLocal(contextSerializer);
                        // [Serializer<?>]
                        generator.mark(write);
                        generator.loadArg(INDEX_CONTEXT);
                        // [Serializer<?>, Context(context)]
                        generator.loadArg(INDEX_BUFFER);
                        // [Serializer<?>, Context(context), ByteBuffer(buffer)]
                        generator.loadLocal(array);
                        // [Serializer<?>, Context(context), ByteBuffer(buffer)), T(instance)]
                        generator.loadLocal(index);
                        // [Serializer<?>, Context(context), ByteBuffer(buffer)), T(instance), int(index)]
                        generator.arrayLoad(elementType);
                        // [Serializer<?>, Context(context), ByteBuffer(buffer)), ?]
                        generator.invokeInterface(TYPE_I_SERIALIZER, SERIALIZATION);
                        // []
                    }
                    generator.goTo(start);
                    generator.mark(end);
                }
            }
            generator.returnValue();
            generator.endMethod();
        }
        {
            final MethodGenerator generator = visitMethod(node,
                    ACC_PUBLIC | ACC_FINAL | ACC_SYNTHETIC, DESERIALIZATION.getName(),
                    DESERIALIZATION.getDescriptor(), null, null);
            if (ASMHelper.isBoxType(type)) {
                generator.loadArg(INDEX_BUFFER);
                // [ByteBuffer(buffer)]
                generator.loadArg(INDEX_INSTANCE);
                // [ByteBuffer(buffer), T(instance)]
                put(generator, clazz);
                // [ByteBuffer(buffer)] ?(unboxValue)
                generator.pop(ASMHelper.TYPE_OBJECT);
                // []
            } else {
                final int dim = type.getSort() != Type.ARRAY ? 0 : type.getDimensions();
                if (dim == 0) {
                    Stream.of(clazz.getDeclaredFields())
                            .filter(this::shouldNotSerialization)
                            .sorted(comparator())
                            .forEach(field -> {
                                final Class<?> fieldType = field.getType();
                                if (fieldType.isPrimitive()) {
                                    generator.loadArg(INDEX_INSTANCE);
                                    // [T(instance)]
                                    generator.loadArg(INDEX_BUFFER);
                                    // [T(instance), ByteBuffer(buffer)]
                                    get(generator, fieldType);
                                    // [T(instance), ?] ?(field)
                                    generator.putField(type, field.getName(), Type.getType(fieldType));
                                    // [ByteBuffer(buffer), ?]
                                } else {
                                    generator.loadArg(INDEX_INSTANCE);
                                    // [T(instance)]
                                    generator.loadArg(INDEX_CONTEXT);
                                    // [T(instance), Context(context)]
                                    generator.push(fieldType);
                                    // [T(instance), Context(context), Class<?>]
                                    generator.invokeInterface(TYPE_I_SERIALIZER_CONTEXT, LOOKUP_SERIALIZER);
                                    // [T(instance), Serializer<?>]
                                    generator.loadArg(INDEX_CONTEXT);
                                    // [T(instance), Serializer<?>, Context(context)]
                                    generator.loadArg(INDEX_BUFFER);
                                    // [T(instance), Serializer<?>, Context(context), ByteBuffer(buffer)]
                                    generator.loadArg(INDEX_CONTEXT);
                                    // [T(instance), Serializer<?>, Context(context), ByteBuffer(buffer), Context(context)]
                                    generator.push(fieldType);
                                    // [T(instance), Serializer<?>, Context(context), ByteBuffer(buffer), Context(context), Class<?>]
                                    generator.invokeInterface(TYPE_I_SERIALIZER_CONTEXT, INSTANTIATION);
                                    // [T(instance), Serializer<?>, Context(context), ByteBuffer(buffer), ?]
                                    generator.invokeInterface(TYPE_I_SERIALIZER, DESERIALIZATION);
                                    // [T(instance), ?]
                                    generator.putField(type, field.getName(), Type.getType(fieldType));
                                    // []
                                }
                            });
                } else {
                
                }
            }
            generator.returnValue();
            generator.endMethod();
        }
        final ClassWriter writer = { clazz.getClassLoader() };
        return UnsafeHelper.allocateInstance(MethodHandleHelper.lookup().in(clazz).defineHiddenClass(writer.toBytecode(node, ComputeType.MAX, ComputeType.FRAME), false).lookupClass());
    }
    
    protected void put(final MethodGenerator generator, final Class<?> type) {
        final Class<?> boxType = TypeHelper.boxClass(type);
        if (boxType == type)
            generator.unbox(ASMHelper.unboxType(Type.getType(type)));
        if (boxType == Byte.class || boxType == Boolean.class)
            generator.invokeVirtual(TYPE_BYTE_BUFFER, new Method("put", Type.getMethodDescriptor(TYPE_BYTE_BUFFER, Type.BYTE_TYPE)));
        else
            generator.invokeVirtual(TYPE_BYTE_BUFFER, new Method("put" + boxType.getSimpleName(), Type.getMethodDescriptor(TYPE_BYTE_BUFFER, Type.getType(TypeHelper.unboxType(type)))));
    }
    
    protected void get(final MethodGenerator generator, final Class<?> type) {
        final Class<?> boxType = TypeHelper.boxClass(type);
        if (boxType == Byte.class || boxType == Boolean.class)
            generator.invokeVirtual(TYPE_BYTE_BUFFER, new Method("get", Type.getMethodDescriptor(TYPE_BYTE_BUFFER, Type.BYTE_TYPE)));
        else
            generator.invokeVirtual(TYPE_BYTE_BUFFER, new Method("get" + boxType.getSimpleName(), Type.getMethodDescriptor(TYPE_BYTE_BUFFER, Type.getType(TypeHelper.unboxType(type)))));
        if (boxType == type)
            generator.box(Type.getType(type));
    }
    
    protected boolean shouldNotSerialization(final Field field) = ReflectionHelper.anyMatch(field, ReflectionHelper.STATIC | ReflectionHelper.TRANSIENT);
    
    protected Comparator<? super Field> comparator() = Comparator.comparing(Field::getName);
    
}
