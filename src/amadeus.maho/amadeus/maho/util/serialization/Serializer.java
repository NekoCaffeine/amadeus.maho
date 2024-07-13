package amadeus.maho.util.serialization;

import java.io.IOException;
import java.lang.constant.ClassDesc;
import java.lang.constant.Constable;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.ImmutableCollections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import amadeus.maho.core.MahoExport;
import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Alias;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.Delegate;
import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.annotation.mark.HiddenDanger;
import amadeus.maho.util.container.Indexed;
import amadeus.maho.util.dynamic.ClassLocal;
import amadeus.maho.util.runtime.ArrayHelper;
import amadeus.maho.util.runtime.DebugHelper;
import amadeus.maho.util.runtime.MethodHandleHelper;
import amadeus.maho.util.runtime.ReflectionHelper;
import amadeus.maho.util.runtime.TypeHelper;

import static amadeus.maho.util.runtime.ObjectHelper.requireNonNull;

@SneakyThrows
public interface Serializer<T> {
    
    interface Context {
        
        interface Derived extends Context {
            
            @Delegate(hard = true)
            Context base();
            
        }
        
        @ToString
        @EqualsAndHashCode
        record Base(Serializer<Object> root, MethodHandles.Lookup lookup = MethodHandleHelper.lookup()) implements Context {
            
            public Base lookup(final MethodHandles.Lookup lookup) = { root, lookup };
            
        }
        
        @ToString
        @EqualsAndHashCode
        record WithStringEncoding(Context base, Charset charset) implements Derived {
            
            @Override
            public String readString(final Deserializable.Input input) throws IOException {
                final byte buffer[] = new byte[input.readVarInt()];
                input.readFully(buffer);
                return { buffer, charset };
            }
            
            @Override
            public void writeString(final Serializable.Output output, final String value) throws IOException {
                final byte buffer[] = value.getBytes(charset);
                output.writeVarInt(buffer.length);
                output.write(buffer);
            }
            
        }
        
        @ToString
        @EqualsAndHashCode
        record WithStringIndexed(Context base, Indexed<String> indexed = Indexed.of()) implements Derived {
            
            @Override
            public String readString(final Deserializable.Input input) throws IOException = indexed[input.readVarInt()];
            
            @Override
            public void writeString(final Serializable.Output output, final String value) throws IOException = output.writeVarInt(indexed[value]);
            
        }
        
        Serializer<Object> root();
        
        MethodHandles.Lookup lookup();
        
        default <C> C instantiation(final Class<? extends C> type) = type.tryInstantiationOrAllocate();
        
        default <C> @Nullable C deserialization(final Deserializable.Input input) throws IOException = (C) root().deserialization(input, this);
        
        default void serialization(final Serializable.Output output, final @Nullable Object instance) throws IOException = root().serialization(output, this, instance);
        
        default String readString(final Deserializable.Input input) throws IOException = input.readUTF();
        
        default void writeString(final Serializable.Output output, final String value) throws IOException = output.writeUTF(value);
        
    }
    
    record Root(Serializer<Object> serializer, Context context) {
        
        public @Nullable Object deserialization(final Deserializable.Input input) throws IOException = serializer.deserialization(input, context);
        
        public void serialization(final Serializable.Output output, final @Nullable Object instance) throws IOException = serializer.serialization(output, context, instance);
        
    }
    
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
    abstract class Chain<T> implements Serializer<T> {
        
        @Default
        Serializer<T> next = Error.INSTANCE;
        
        @Override
        public @Nullable T deserialization(final Deserializable.Input input, final Context context) throws IOException = next.deserialization(input, context);
        
        @Override
        public void serialization(final Serializable.Output output, final Context context, final @Nullable T instance) throws IOException = next.serialization(output, context, instance);
        
    }
    
    final class Error implements Serializer {
        
        public static final Error INSTANCE = { };
        
        @Override
        public @Nullable Object deserialization(final Deserializable.Input input, final Context context) { throw new UnsupportedOperationException(); }
        
        @Override
        public void serialization(final Serializable.Output output, final Context context, final @Nullable Object instance) { throw new UnsupportedOperationException(); }
        
    }
    
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    class RuntimeConstantDesc implements Serializer<ConstantDesc> {
        
        public static final int
                CONSTANT_CLASS_DESC         = 0,
                CONSTANT_METHOD_HANDLE_DESC = 1,
                CONSTANT_METHOD_TYPE_DESC   = 2,
                CONSTANT_DYNAMIC_DESC       = 3;
        
        public static final RuntimeConstantDesc INSTANCE = { };
        
        @Override
        public ConstantDesc deserialization(final Deserializable.Input input, final Context context) throws IOException = switch (input.readByteUnsigned()) {
            case CONSTANT_CLASS_DESC         -> ClassDesc.of(context.readString(input));
            case CONSTANT_METHOD_HANDLE_DESC -> MethodHandleDesc.of(DirectMethodHandleDesc.Kind.valueOf(context.readString(input)), ClassDesc.of(context.readString(input)), context.readString(input), context.readString(input));
            case CONSTANT_METHOD_TYPE_DESC   -> MethodTypeDesc.ofDescriptor(context.readString(input));
            case CONSTANT_DYNAMIC_DESC       -> DynamicConstantDesc.AnonymousDynamicConstantDesc.ofCanonical(
                    context.deserialization(input),
                    context.readString(input),
                    ClassDesc.of(context.readString(input)),
                    context.deserialization(input)
            );
            default                          -> throw DebugHelper.breakpointBeforeThrow(new UnsupportedOperationException());
        };
        
        @Override
        public void serialization(final Serializable.Output output, final Context context, final ConstantDesc instance) throws IOException = switch (instance) {
            case null                                       -> DebugHelper.breakpointBeforeThrow(new NullPointerException());
            case ClassDesc classDesc                        -> {
                output.write(CONSTANT_CLASS_DESC);
                context.writeString(output, classDesc.descriptorString());
            }
            case DirectMethodHandleDesc methodHandleDesc    -> {
                output.write(CONSTANT_METHOD_HANDLE_DESC);
                context.writeString(output, methodHandleDesc.kind().name());
                context.writeString(output, methodHandleDesc.owner().descriptorString());
                context.writeString(output, methodHandleDesc.methodName());
                context.writeString(output, methodHandleDesc.lookupDescriptor());
            }
            case MethodTypeDesc methodTypeDesc              -> {
                output.write(CONSTANT_METHOD_TYPE_DESC);
                context.writeString(output, methodTypeDesc.descriptorString());
            }
            case DynamicConstantDesc<?> dynamicConstantDesc -> {
                output.write(CONSTANT_DYNAMIC_DESC);
                context.serialization(output, dynamicConstantDesc.bootstrapMethod());
                context.writeString(output, dynamicConstantDesc.constantName());
                context.writeString(output, dynamicConstantDesc.constantType().descriptorString());
                context.serialization(output, dynamicConstantDesc.bootstrapArgs());
            }
            default                                         -> DebugHelper.breakpointBeforeThrow(new UnsupportedOperationException(instance.toString()));
        };
        
    }
    
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    class Base extends Chain {
        
        public static final int
                NULL          = 0,
                BOOLEAN       = 1,
                BYTE          = 2,
                SHORT         = 3,
                CHAR          = 4,
                INT           = 5,
                LONG          = 6,
                FLOAT         = 7,
                DOUBLE        = 8,
                ARRAY         = 9,
                STRING        = 10,
                ENUM          = 11,
                CLASS         = 12,
                CONSTABLE     = 13,
                CONSTANT_DESC = 14,
                BINARY_MAPPER = 15,
                RECORD        = 16,
                OBJECT        = 17;
        
        public static final boolean DEBUG_CLASS_RESOLVE = MahoExport.debug();
        
        @Getter
        @Default
        RuntimeConstantDesc constantDesc = RuntimeConstantDesc.INSTANCE;
        
        @Getter
        @Default
        Predicate<? super DynamicConstantDesc<?>> dynamicConstantDescFilter = desc -> !(desc instanceof DynamicConstantDesc.AnonymousDynamicConstantDesc<?>);
        
        @Getter
        @Default
        boolean useVarInteger = true;
        
        @Override
        public @Nullable Object deserialization(final Deserializable.Input input, final Context context) throws IOException = switch (input.readByteUnsigned()) {
            case NULL          -> null;
            case BOOLEAN       -> input.readBoolean();
            case BYTE          -> input.readByte();
            case SHORT         -> input.readShortLittleEndian();
            case CHAR          -> input.readCharLittleEndian();
            case INT           -> useVarInteger() ? input.readVarInt() : input.readIntLittleEndian();
            case LONG          -> useVarInteger() ? input.readVarLong() : input.readLongLittleEndian();
            case FLOAT         -> input.readFloatLittleEndian();
            case DOUBLE        -> input.readDoubleLittleEndian();
            case ARRAY         -> {
                final Class<?> elementType = requireNonNull(context.deserialization(input));
                final int length = input.readVarInt();
                if (elementType.isPrimitive()) {
                    switch (elementType.getName()) {
                        case "boolean" -> {
                            final boolean array[] = new boolean[length];
                            for (int i = 0; i < length; i++)
                                array[i] = input.readBoolean();
                            yield array;
                        }
                        case "byte"    -> {
                            final byte array[] = new byte[length];
                            input.readFully(array);
                            yield array;
                        }
                        case "short"   -> {
                            final short array[] = new short[length];
                            for (int i = 0; i < length; i++)
                                array[i] = input.readShortLittleEndian();
                            yield array;
                        }
                        case "char"    -> {
                            final char array[] = new char[length];
                            for (int i = 0; i < length; i++)
                                array[i] = input.readCharLittleEndian();
                            yield array;
                        }
                        case "int"     -> {
                            final int array[] = new int[length];
                            if (useVarInteger())
                                for (int i = 0; i < length; i++)
                                    array[i] = input.readVarInt();
                            else
                                for (int i = 0; i < length; i++)
                                    array[i] = input.readIntLittleEndian();
                            yield array;
                        }
                        case "long"    -> {
                            final long array[] = new long[length];
                            if (useVarInteger())
                                for (int i = 0; i < length; i++)
                                    array[i] = input.readVarLong();
                            else
                                for (int i = 0; i < length; i++)
                                    array[i] = input.readLongLittleEndian();
                            yield array;
                        }
                        case "float"   -> {
                            final float array[] = new float[length];
                            for (int i = 0; i < length; i++)
                                array[i] = input.readFloatLittleEndian();
                            yield array;
                        }
                        case "double"  -> {
                            final double array[] = new double[length];
                            for (int i = 0; i < length; i++)
                                array[i] = input.readDoubleLittleEndian();
                            yield array;
                        }
                        default        -> throw new UnsupportedOperationException(elementType.getName());
                    }
                } else {
                    final Object array = Array.newInstance(elementType, length);
                    for (int i = 0; i < length; i++)
                        Array.set(array, i, elementType.cast(context.deserialization(input)));
                    yield array;
                }
            }
            case STRING        -> context.readString(input);
            case ENUM          -> Enum.valueOf(((Class<?>) requireNonNull(context.deserialization(input))).asSubclass(Enum.class), context.readString(input));
            case CLASS         -> ClassDesc.of(context.readString(input)).resolveConstantDesc(context.lookup());
            case CONSTABLE     -> switch (requireNonNull(context.deserialization(input))) {
                case DynamicConstantDesc<?> dynamicConstantDesc -> {
                    if (dynamicConstantDescFilter().test(dynamicConstantDesc))
                        yield dynamicConstantDesc.resolveConstantDesc(context.lookup());
                    else
                        throw DebugHelper.breakpointBeforeThrow(new UnsupportedOperationException(STR."DynamicConstantDesc filter rejected: \{dynamicConstantDesc}"));
                }
                case ConstantDesc constantDesc                  -> constantDesc.resolveConstantDesc(context.lookup());
                default                                         -> throw DebugHelper.breakpointBeforeThrow(new UnsupportedOperationException());
            };
            case CONSTANT_DESC -> constantDesc().deserialization(input, context);
            case BINARY_MAPPER -> context.<BinaryMapper>instantiation(((Class<?>) requireNonNull(context.deserialization(input))).asSubclass(BinaryMapper.class)).deserialization(input);
            case RECORD        ->  {
                final Class<?> recordType = requireNonNull(context.deserialization(input));
                final int count = input.readVarInt();
                final String names[] = new String[count];
                for (int i = 0; i < count; i++)
                    names[i] = context.readString(input);
                final @Nullable Constructor<?> constructor = Alias.Helper.lookup(recordType, names);
                if (constructor == null)
                    throw DebugHelper.breakpointBeforeThrow(new UnsupportedOperationException(STR."No record constructor found: \{recordType} with \{ArrayHelper.toString(names)}"));
                final Object values[] = new Object[count];
                for (int i = 0; i < count; i++)
                    values[i] = context.deserialization(input);
                yield constructor.newInstance(values);
            }
            case OBJECT        -> super.deserialization(input, context);
            default            -> throw DebugHelper.breakpointBeforeThrow(new UnsupportedOperationException());
        };
        
        @Override
        public void serialization(final Serializable.Output output, final Context context, final Object instance) throws IOException = switch (instance) {
            case null               -> output.write(NULL);
            case Boolean value      -> {
                output.write(BOOLEAN);
                output.writeBoolean(value);
            }
            case Byte value         -> {
                output.write(BYTE);
                output.writeByte(value);
            }
            case Short value        -> {
                output.write(SHORT);
                output.writeShortLittleEndian(value);
            }
            case Character value    -> {
                output.write(CHAR);
                output.writeCharLittleEndian(value);
            }
            case Integer value      -> {
                output.write(INT);
                if (useVarInteger())
                    output.writeVarInt(value);
                else
                    output.writeIntLittleEndian(value);
            }
            case Long value         -> {
                output.write(LONG);
                if (useVarInteger())
                    output.writeVarLong(value);
                else
                    output.writeLongLittleEndian(value);
            }
            case Float value        -> {
                output.write(FLOAT);
                output.writeFloatLittleEndian(value);
            }
            case Double value       -> {
                output.write(DOUBLE);
                output.writeDoubleLittleEndian(value);
            }
            case boolean[] array    -> {
                output.write(ARRAY);
                context.serialization(output, boolean.class);
                output.writeVarInt(array.length);
                for (final boolean element : array)
                    output.writeBoolean(element);
            }
            case byte[] array       -> {
                output.write(ARRAY);
                context.serialization(output, byte.class);
                output.writeVarInt(array.length);
                output.write(array);
            }
            case short[] array      -> {
                output.write(ARRAY);
                context.serialization(output, short.class);
                output.writeVarInt(array.length);
                for (final short element : array)
                    output.writeShortLittleEndian(element);
            }
            case char[] array       -> {
                output.write(ARRAY);
                context.serialization(output, char.class);
                output.writeVarInt(array.length);
                for (final char element : array)
                    output.writeCharLittleEndian(element);
            }
            case int[] array        -> {
                output.write(ARRAY);
                context.serialization(output, int.class);
                output.writeVarInt(array.length);
                if (useVarInteger())
                    for (final int element : array)
                        output.writeVarInt(element);
                else
                    for (final int element : array)
                        output.writeIntLittleEndian(element);
            }
            case long[] array       -> {
                output.write(ARRAY);
                context.serialization(output, long.class);
                output.writeVarInt(array.length);
                if (useVarInteger())
                    for (final long element : array)
                        output.writeVarLong(element);
                else
                    for (final long element : array)
                        output.writeLongLittleEndian(element);
            }
            case float[] array      -> {
                output.write(ARRAY);
                context.serialization(output, float.class);
                output.writeVarInt(array.length);
                for (final float element : array)
                    output.writeFloatLittleEndian(element);
            }
            case double[] array     -> {
                output.write(ARRAY);
                context.serialization(output, double.class);
                output.writeVarInt(array.length);
                for (final double element : array)
                    output.writeDoubleLittleEndian(element);
            }
            case Object[] array     -> {
                output.write(ARRAY);
                final Class<?> elementType = array.getClass().getComponentType();
                context.serialization(output, elementType);
                output.writeVarInt(array.length);
                for (final Object element : array)
                    context.serialization(output, element);
            }
            case String value       -> {
                output.write(STRING);
                context.writeString(output, value);
            }
            case Enum<?> value      -> {
                output.write(ENUM);
                context.serialization(output, value.getDeclaringClass());
                context.writeString(output, value.name());
            }
            case Class<?> value     -> {
                output.write(CLASS);
                if (DEBUG_CLASS_RESOLVE)
                    try {
                        value.describeConstable().orElseThrow().resolveConstantDesc(context.lookup());
                    } catch (final ReflectiveOperationException e) { throw DebugHelper.breakpointBeforeThrow(new UnsupportedOperationException(e)); }
                context.writeString(output, value.describeConstable().orElseThrow().descriptorString());
            }
            case Constable value    -> {
                output.write(CONSTABLE);
                constantDesc().serialization(output, context, value.describeConstable().orElseThrow());
            }
            case ConstantDesc value -> {
                output.write(CONSTANT_DESC);
                constantDesc().serialization(output, context, value);
            }
            case BinaryMapper value -> {
                output.write(BINARY_MAPPER);
                context.serialization(output, value.getClass());
                value.serialization(output);
            }
            case Record record      -> {
                output.write(RECORD);
                context.serialization(output, record.getClass());
                final RecordComponent components[] = record.getClass().getRecordComponents();
                output.writeVarInt(components.length);
                for (final RecordComponent component : components)
                    context.writeString(output, component.getName());
                for (final RecordComponent component : components)
                    context.serialization(output, component.getAccessor().invoke(record));
            }
            default                 -> {
                output.write(OBJECT);
                super.serialization(output, context, instance);
            }
        };
        
    }
    
    @NoArgsConstructor
    class RuntimeOptimized extends Chain {
        
        public static final List<Object> flyweights = List.of(
                List.of(), Set.of(), Map.of(),
                Collections.emptyList(), Collections.emptySet(), Collections.emptyMap()
        );
        
        public static final IdentityHashMap<Object, Integer> flyweightIds = { };
        
        static {
            for (int i = 0; i < flyweights.size(); i++)
                flyweightIds[flyweights[i]] = i;
        }
        
        public static final int
                FLYWEIGHT_INSTANCE = 0,
                AS_LIST            = 1,
                SINGLETON_LIST     = 2,
                SINGLETON_SET      = 3,
                SINGLETON_MAP      = 4,
                IMMUTABLE_LIST     = 5,
                IMMUTABLE_SET      = 6,
                IMMUTABLE_MAP      = 7,
                CREATE_COLLECTION  = 8,
                CREATE_MAP         = 9,
                URI                = 10,
                UUID               = 11,
                REGEX              = 12,
                PATH               = 13,
                CREATE_INDEXED     = 14,
                UNOPTIMIZED        = -1;
        
        @Override
        public @Nullable Object deserialization(final Deserializable.Input input, final Context context) throws IOException = switch (input.readByteUnsigned()) {
            case FLYWEIGHT_INSTANCE -> flyweights[input.readByteUnsigned()];
            case AS_LIST            -> {
                final Object array[] = new Object[input.readVarInt()];
                for (int i = 0; i < array.length; i++)
                    array[i] = context.deserialization(input);
                yield Arrays.asList(array);
            }
            case SINGLETON_LIST     -> Collections.singletonList(context.deserialization(input));
            case SINGLETON_SET      -> Collections.singleton(context.deserialization(input));
            case SINGLETON_MAP      -> Collections.singletonMap(context.deserialization(input), context.deserialization(input));
            case IMMUTABLE_LIST     -> {
                final Object array[] = new Object[input.readVarInt()];
                for (int i = 0; i < array.length; i++)
                    array[i] = context.deserialization(input);
                yield (Privilege) ImmutableCollections.listFromTrustedArray(array);
            }
            case IMMUTABLE_SET      -> {
                final Object array[] = new Object[input.readVarInt()];
                for (int i = 0; i < array.length; i++)
                    array[i] = context.deserialization(input);
                yield (Privilege) new ImmutableCollections.SetN<>(array);
            }
            case IMMUTABLE_MAP      -> {
                final int size = input.readVarInt();
                final Object array[] = new Object[size << 1];
                for (int i = 0; i < size; i++) {
                    array[i << 1] = context.deserialization(input);
                    array[(i << 1) + 1] = context.deserialization(input);
                }
                yield (Privilege) new ImmutableCollections.MapN<>(array);
            }
            case CREATE_COLLECTION  -> {
                final Class<? extends Collection> type = context.deserialization(input);
                final Collection<Object> collection = context.instantiation(type);
                deserialization(input, context, collection);
                yield collection;
            }
            case CREATE_MAP         -> {
                final Class<? extends Map> type = context.deserialization(input);
                final Map<Object, Object> map = context.instantiation(type);
                deserialization(input, context, map);
                yield map;
            }
            case URI                -> java.net.URI.create(context.readString(input));
            case UUID               -> new java.util.UUID(input.readLongLittleEndian(), input.readLongLittleEndian());
            case REGEX              -> Pattern.compile(context.readString(input), input.readIntLittleEndian());
            case PATH               -> Path.of(context.deserialization(input));
            case CREATE_INDEXED     -> {
                final Indexed<Object> indexed = input.readBoolean() ? Indexed.ofConcurrent() : Indexed.of(context.deserialization(input), context.deserialization(input));
                final int size = input.readVarInt();
                for (int i = 0; i < size; i++)
                    indexed[context.deserialization(input)];
                yield indexed;
            }
            case UNOPTIMIZED        -> super.deserialization(input, context);
            default                 -> throw DebugHelper.breakpointBeforeThrow(new UnsupportedOperationException());
        };
        
        @Override
        public void serialization(final Serializable.Output output, final Context context, final Object instance) throws IOException {
            final @Nullable Integer flyweightId = flyweightIds[instance];
            if (flyweightId != null) {
                output.write(FLYWEIGHT_INSTANCE);
                output.write(flyweightId);
            } else
                switch (instance) {
                    case Collection<?> collection -> {
                        switch (collection.getClass().getName()) {
                            case "java.util.Arrays$ArrayList"                           -> {
                                output.write(AS_LIST);
                                serialization(output, context, collection);
                            }
                            case "java.util.Collections$SingletonList"                  -> {
                                output.write(SINGLETON_LIST);
                                context.serialization(output, collection.iterator().next());
                            }
                            case "java.util.Collections$SingletonSet"                   -> {
                                output.write(SINGLETON_SET);
                                context.serialization(output, collection.iterator().next());
                            }
                            case "java.util.ImmutableCollections$AbstractImmutableList" -> {
                                output.write(IMMUTABLE_LIST);
                                serialization(output, context, collection);
                            }
                            case "java.util.ImmutableCollections$AbstractImmutableSet"  -> {
                                output.write(IMMUTABLE_SET);
                                serialization(output, context, collection);
                            }
                            default                                                     -> {
                                final Class<? extends Collection> type = collection.getClass();
                                if (java.io.Serializable.class.isAssignableFrom(type) && TypeHelper.hasNoArgConstructor(type)) {
                                    output.write(CREATE_COLLECTION);
                                    context.serialization(output, type);
                                    serialization(output, context, collection);
                                } else {
                                    output.write(UNOPTIMIZED);
                                    super.serialization(output, context, instance);
                                }
                            }
                        }
                    }
                    case Map<?, ?> map            -> {
                        switch (map.getClass().getName()) {
                            case "java.util.Collections$SingletonMap"                  -> {
                                output.write(SINGLETON_MAP);
                                final Map.Entry<?, ?> entry = map.entrySet().iterator().next();
                                context.serialization(output, entry.getKey());
                                context.serialization(output, entry.getValue());
                            }
                            case "java.util.ImmutableCollections$AbstractImmutableMap" -> {
                                output.write(IMMUTABLE_MAP);
                                output.writeVarInt(map.size());
                                for (final Map.Entry<?, ?> entry : map.entrySet()) {
                                    context.serialization(output, entry.getKey());
                                    context.serialization(output, entry.getValue());
                                }
                            }
                            default                                                    -> {
                                final Class<? extends Map> type = map.getClass();
                                if (java.io.Serializable.class.isAssignableFrom(type) && TypeHelper.hasNoArgConstructor(type)) {
                                    output.write(CREATE_MAP);
                                    context.serialization(output, type);
                                    serialization(output, context, map);
                                } else {
                                    output.write(UNOPTIMIZED);
                                    super.serialization(output, context, instance);
                                }
                            }
                        }
                    }
                    case java.net.URI uri         -> {
                        output.write(URI);
                        context.writeString(output, uri.toString());
                    }
                    case java.util.UUID uuid      -> {
                        output.write(UUID);
                        output.writeLongLittleEndian(uuid.getMostSignificantBits());
                        output.writeLongLittleEndian(uuid.getLeastSignificantBits());
                    }
                    case Pattern regex            -> {
                        output.write(REGEX);
                        context.writeString(output, regex.pattern());
                        output.writeIntLittleEndian(regex.flags());
                    }
                    case Path path                -> {
                        output.write(PATH);
                        context.serialization(output, path.toUri());
                    }
                    case Indexed<?> indexed       -> {
                        output.write(CREATE_INDEXED);
                        switch (indexed) {
                            case Indexed.Concurrent<?> _ -> output.writeBoolean(true);
                            case Indexed.Base<?> base    -> {
                                output.writeBoolean(false);
                                output.write(CREATE_MAP);
                                context.serialization(output, base.ids().getClass());
                                output.writeVarInt(0);
                                output.write(CREATE_COLLECTION);
                                context.serialization(output, base.values().getClass());
                                output.writeVarInt(0);
                            }
                            default                      -> throw DebugHelper.breakpointBeforeThrow(new UnsupportedOperationException(indexed.getClass().getName()));
                        }
                        final List<?> values = indexed.values();
                        output.writeVarInt(values.size());
                        for (final Object value : values)
                            context.serialization(output, value);
                    }
                    default                       -> {
                        output.write(UNOPTIMIZED);
                        super.serialization(output, context, instance);
                    }
                }
        }
        
        public static void writeSerializableNoArgsConstructorType(final Serializable.Output output, final Context context, final Class<?> type) throws IOException {
            if (java.io.Serializable.class.isAssignableFrom(type) && TypeHelper.hasNoArgConstructor(type))
                context.serialization(output, type);
            else
                throw DebugHelper.breakpointBeforeThrow(new UnsupportedOperationException(type.getName()));
        }
        
        public static void deserialization(final Deserializable.Input input, final Context context, final Collection<Object> collection) throws IOException {
            final int size = input.readVarInt();
            for (int i = 0; i < size; i++)
                collection.add(context.deserialization(input));
        }
        
        public static void serialization(final Serializable.Output output, final Context context, final Collection<?> collection) throws IOException {
            output.writeVarInt(collection.size());
            for (final Object element : collection)
                context.serialization(output, element);
        }
        
        public static void deserialization(final Deserializable.Input input, final Context context, final Map<Object, Object> map) throws IOException {
            final int size = input.readVarInt();
            for (int i = 0; i < size; i++)
                map.put(context.deserialization(input), context.deserialization(input));
        }
        
        public static void serialization(final Serializable.Output output, final Context context, final Map<?, ?> map) throws IOException {
            output.writeVarInt(map.size());
            for (final Map.Entry<?, ?> entry : map.entrySet()) {
                context.serialization(output, entry.getKey());
                context.serialization(output, entry.getValue());
            }
        }
        
    }
    
    @Deprecated
    @HiddenDanger("RCE(Remote Code Execution) vulnerability")
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    class FieldMapping extends Chain {
        
        ClassLocal<Map<String, VarHandle>> fieldMappings = {
                type -> ReflectionHelper.allFields(type).stream()
                        .filter(field -> (field.getModifiers() & (Modifier.STATIC | Modifier.TRANSIENT)) == 0)
                        .collect(Collectors.toMap(Field::getName, MethodHandleHelper.lookup()::unreflectVarHandle))
        };
        
        @Default
        @Nullable
        Predicate<Class<?>> filter = null;
        
        @Default
        boolean forwardCompatibility = false;
        
        @Override
        public @Nullable Object deserialization(final Deserializable.Input input, final Context context) throws IOException {
            if (filter == null || forwardCompatibility || input.readBoolean()) {
                final Class<?> type = context.deserialization(input);
                final Map<String, VarHandle> mapping = fieldMappings[type];
                final Object instance = context.instantiation(type);
                final int count = input.readVarInt();
                for (int i = 0; i < count; i++) {
                    final String name = context.readString(input);
                    final @Nullable VarHandle handle = mapping[name];
                    if (handle == null)
                        throw DebugHelper.breakpointBeforeThrow(new NoSuchFieldException(name));
                    final Object value = context.deserialization(input);
                    handle.set(instance, value);
                }
                return instance;
            }
            return super.deserialization(input, context);
        }
        
        @Override
        public void serialization(final Serializable.Output output, final Context context, final @Nullable Object instance) throws IOException {
            final Class<?> type = requireNonNull(instance).getClass();
            if (filter == null || filter.test(type)) {
                if (filter != null)
                    output.writeBoolean(true);
                context.serialization(output, type);
                final Map<String, VarHandle> mapping = fieldMappings[type];
                final int count = mapping.size();
                output.writeVarInt(count);
                for (final Map.Entry<String, VarHandle> entry : mapping.entrySet()) {
                    context.writeString(output, entry.getKey());
                    context.serialization(output, entry.getValue().get(instance));
                }
            } else
                super.serialization(output, context, instance);
        }
        
    }
    
    @Nullable
    T deserialization(final Deserializable.Input input, final Context context) throws IOException;
    
    void serialization(final Serializable.Output output, final Context context, final @Nullable T instance) throws IOException;
    
}
