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
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.ImmutableCollections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import amadeus.maho.core.MahoExport;
import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.annotation.mark.HiddenDanger;
import amadeus.maho.util.dynamic.ClassLocal;
import amadeus.maho.util.runtime.ArrayHelper;
import amadeus.maho.util.runtime.DebugHelper;
import amadeus.maho.util.runtime.MethodHandleHelper;
import amadeus.maho.util.runtime.ReflectionHelper;
import amadeus.maho.util.runtime.TypeHelper;

public interface Serializer<T> {
    
    interface Context {
        
        record Base(Serializer<Object> root, MethodHandles.Lookup lookup = MethodHandleHelper.lookup()) implements Context {
            
            public Base lookup(final MethodHandles.Lookup lookup) = { root, lookup };
            
        }
        
        Serializer<Object> root();
        
        MethodHandles.Lookup lookup();
        
        default <C> C instantiation(final Class<? extends C> type) = type.tryInstantiationOrAllocate();
        
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
        @SneakyThrows
        public ConstantDesc deserialization(final Deserializable.Input input, final Context context) throws IOException = switch (input.readByteUnsigned()) {
            case CONSTANT_CLASS_DESC         -> ClassDesc.of(input.readUTF());
            case CONSTANT_METHOD_HANDLE_DESC -> MethodHandleDesc.of(DirectMethodHandleDesc.Kind.valueOf(input.readUTF()), ClassDesc.of(input.readUTF()), input.readUTF(), input.readUTF());
            case CONSTANT_METHOD_TYPE_DESC   -> MethodTypeDesc.ofDescriptor(input.readUTF());
            case CONSTANT_DYNAMIC_DESC       -> DynamicConstantDesc.AnonymousDynamicConstantDesc.ofCanonical(
                    (DirectMethodHandleDesc) context.root().deserialization(input, context),
                    input.readUTF(),
                    ClassDesc.of(input.readUTF()),
                    (ConstantDesc[]) context.root().deserialization(input, context)
            );
            default                          -> throw DebugHelper.breakpointBeforeThrow(new UnsupportedOperationException());
        };
        
        @Override
        public void serialization(final Serializable.Output output, final Context context, final ConstantDesc instance) throws IOException = switch (instance) {
            case null                                       -> DebugHelper.breakpointBeforeThrow(new NullPointerException());
            case ClassDesc classDesc                        -> {
                output.write(CONSTANT_CLASS_DESC);
                output.writeUTF(classDesc.descriptorString());
            }
            case DirectMethodHandleDesc methodHandleDesc    -> {
                output.write(CONSTANT_METHOD_HANDLE_DESC);
                output.writeUTF(methodHandleDesc.kind().name());
                output.writeUTF(methodHandleDesc.owner().descriptorString());
                output.writeUTF(methodHandleDesc.methodName());
                output.writeUTF(methodHandleDesc.lookupDescriptor());
            }
            case MethodTypeDesc methodTypeDesc              -> {
                output.write(CONSTANT_METHOD_TYPE_DESC);
                output.writeUTF(methodTypeDesc.descriptorString());
            }
            case DynamicConstantDesc<?> dynamicConstantDesc -> {
                output.write(CONSTANT_DYNAMIC_DESC);
                context.root().serialization(output, context, dynamicConstantDesc.bootstrapMethod());
                output.writeUTF(dynamicConstantDesc.constantName());
                output.writeUTF(dynamicConstantDesc.constantType().descriptorString());
                context.root().serialization(output, context, dynamicConstantDesc.bootstrapArgs());
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
                OBJECT        = 16;
        
        public static final int
                STRING_CHARSET_US_ASCII   = 1,
                STRING_CHARSET_ISO_8859_1 = 2,
                STRING_CHARSET_UTF_8      = 3,
                STRING_CHARSET_UTF_16     = 4,
                STRING_CHARSET_UTF_16BE   = 5,
                STRING_CHARSET_UTF_16LE   = 6;
        
        public static final Charset STANDARD_CHARSETS[] = {
                StandardCharsets.US_ASCII,
                StandardCharsets.ISO_8859_1,
                StandardCharsets.UTF_8,
                StandardCharsets.UTF_16,
                StandardCharsets.UTF_16BE,
                StandardCharsets.UTF_16LE
        };
        
        public static final boolean DEBUG_CLASS_RESOLVE = MahoExport.debug();
        
        @Getter
        @Default
        RuntimeConstantDesc constantDesc = RuntimeConstantDesc.INSTANCE;
        
        @Getter
        @Default
        Predicate<? super DynamicConstantDesc<?>> dynamicConstantDescFilter = desc -> !(desc instanceof DynamicConstantDesc.AnonymousDynamicConstantDesc<?>);
        
        @Getter
        @Default
        Function<String, Charset> charsetFunction = _ -> StandardCharsets.UTF_8;
        
        @Override
        @SneakyThrows
        public @Nullable Object deserialization(final Deserializable.Input input, final Context context) throws IOException = switch (input.readByteUnsigned()) {
            case NULL          -> null;
            case BOOLEAN       -> input.readBoolean();
            case BYTE          -> input.readByte();
            case SHORT         -> input.readShortLittleEndian();
            case CHAR          -> input.readCharLittleEndian();
            case INT           -> input.readIntLittleEndian();
            case LONG          -> input.readLongLittleEndian();
            case FLOAT         -> input.readFloatLittleEndian();
            case DOUBLE        -> input.readDoubleLittleEndian();
            case ARRAY         -> {
                final Class<?> elementType = (Class<?>) context.root().deserialization(input, context);
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
                            for (int i = 0; i < length; i++)
                                array[i] = input.readIntLittleEndian();
                            yield array;
                        }
                        case "long"    -> {
                            final long array[] = new long[length];
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
                        default        -> throw new UnsupportedOperationException();
                    }
                } else {
                    final Object array = Array.newInstance(elementType, length);
                    for (int i = 0; i < length; i++)
                        Array.set(array, i, elementType.cast(context.root().deserialization(input, context)));
                    yield array;
                }
            }
            case STRING        -> {
                final Charset charset = switch (input.readByteUnsigned()) {
                    case STRING_CHARSET_US_ASCII   -> StandardCharsets.US_ASCII;
                    case STRING_CHARSET_ISO_8859_1 -> StandardCharsets.ISO_8859_1;
                    case STRING_CHARSET_UTF_8      -> StandardCharsets.UTF_8;
                    case STRING_CHARSET_UTF_16     -> StandardCharsets.UTF_16;
                    case STRING_CHARSET_UTF_16BE   -> StandardCharsets.UTF_16BE;
                    case STRING_CHARSET_UTF_16LE   -> StandardCharsets.UTF_16LE;
                    default                        -> Charset.forName(new String(input.readFully(input.readVarInt()), StandardCharsets.UTF_8));
                };
                yield new String(input.readFully(input.readVarInt()), charset);
            }
            case ENUM          -> Enum.valueOf(((Class<?>) context.root().deserialization(input, context)).asSubclass(Enum.class), input.readUTF());
            case CLASS         -> ClassDesc.of(input.readUTF()).resolveConstantDesc(context.lookup());
            case CONSTABLE     -> switch (context.root().deserialization(input, context)) {
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
            case BINARY_MAPPER -> context.<BinaryMapper>instantiation(((Class<?>) context.root().deserialization(input, context)).asSubclass(BinaryMapper.class)).deserialization(input);
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
                output.writeIntLittleEndian(value);
            }
            case Long value         -> {
                output.write(LONG);
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
                context.root().serialization(output, context, boolean.class);
                output.writeVarInt(array.length);
                for (final boolean element : array)
                    output.writeBoolean(element);
            }
            case byte[] array       -> {
                output.write(ARRAY);
                context.root().serialization(output, context, byte.class);
                output.writeVarInt(array.length);
                output.write(array);
            }
            case short[] array      -> {
                output.write(ARRAY);
                context.root().serialization(output, context, short.class);
                output.writeVarInt(array.length);
                for (final short element : array)
                    output.writeShortLittleEndian(element);
            }
            case char[] array       -> {
                output.write(ARRAY);
                context.root().serialization(output, context, char.class);
                output.writeVarInt(array.length);
                for (final char element : array)
                    output.writeCharLittleEndian(element);
            }
            case int[] array        -> {
                output.write(ARRAY);
                context.root().serialization(output, context, int.class);
                output.writeVarInt(array.length);
                for (final int element : array)
                    output.writeIntLittleEndian(element);
            }
            case long[] array       -> {
                output.write(ARRAY);
                context.root().serialization(output, context, long.class);
                output.writeVarInt(array.length);
                for (final long element : array)
                    output.writeLongLittleEndian(element);
            }
            case float[] array      -> {
                output.write(ARRAY);
                context.root().serialization(output, context, float.class);
                output.writeVarInt(array.length);
                for (final float element : array)
                    output.writeFloatLittleEndian(element);
            }
            case double[] array     -> {
                output.write(ARRAY);
                context.root().serialization(output, context, double.class);
                output.writeVarInt(array.length);
                for (final double element : array)
                    output.writeDoubleLittleEndian(element);
            }
            case Object[] array     -> {
                output.write(ARRAY);
                final Class<?> elementType = array.getClass().getComponentType();
                final Serializer<Object> root = context.root();
                root.serialization(output, context, elementType);
                output.writeVarInt(array.length);
                for (final Object element : array)
                    root.serialization(output, context, element);
            }
            case String value       -> {
                output.write(STRING);
                final Charset charset = charsetFunction()[value];
                final int index = ArrayHelper.indexOf(STANDARD_CHARSETS, charset);
                output.write(index == -1 ? 0 : index);
                final byte buffer[] = value.getBytes(charset);
                output.writeVarInt(buffer.length);
                output.write(buffer);
            }
            case Enum<?> value      -> {
                output.write(ENUM);
                context.root().serialization(output, context, value.getDeclaringClass());
                output.writeUTF(value.name());
            }
            case Class<?> value     -> {
                output.write(CLASS);
                if (DEBUG_CLASS_RESOLVE)
                    try {
                        value.describeConstable().orElseThrow().resolveConstantDesc(context.lookup());
                    } catch (final ReflectiveOperationException e) { throw DebugHelper.breakpointBeforeThrow(new UnsupportedOperationException(e)); }
                output.writeUTF(value.describeConstable().orElseThrow().descriptorString());
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
                context.root().serialization(output, context, value.getClass());
                value.serialization(output);
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
                UNOPTIMIZED        = -1;
        
        @Override
        public @Nullable Object deserialization(final Deserializable.Input input, final Context context) throws IOException = switch (input.readByteUnsigned()) {
            case FLYWEIGHT_INSTANCE -> flyweights[input.readByteUnsigned()];
            case AS_LIST            -> {
                final Object array[] = new Object[input.readVarInt()];
                for (int i = 0; i < array.length; i++)
                    array[i] = context.root().deserialization(input, context);
                yield Arrays.asList(array);
            }
            case SINGLETON_LIST     -> Collections.singletonList(context.root().deserialization(input, context));
            case SINGLETON_SET      -> Collections.singleton(context.root().deserialization(input, context));
            case SINGLETON_MAP      -> Collections.singletonMap(context.root().deserialization(input, context), context.root().deserialization(input, context));
            case IMMUTABLE_LIST     -> {
                final Object array[] = new Object[input.readVarInt()];
                for (int i = 0; i < array.length; i++)
                    array[i] = context.root().deserialization(input, context);
                yield (Privilege) ImmutableCollections.listFromTrustedArray(array);
            }
            case IMMUTABLE_SET      -> {
                final Object array[] = new Object[input.readVarInt()];
                for (int i = 0; i < array.length; i++)
                    array[i] = context.root().deserialization(input, context);
                yield (Privilege) new ImmutableCollections.SetN<>(array);
            }
            case IMMUTABLE_MAP      -> {
                final int size = input.readVarInt();
                final Object array[] = new Object[size << 1];
                for (int i = 0; i < size; i++) {
                    array[i << 1] = context.root().deserialization(input, context);
                    array[(i << 1) + 1] = context.root().deserialization(input, context);
                }
                yield (Privilege) new ImmutableCollections.MapN<>(array);
            }
            case CREATE_COLLECTION  -> {
                final Class<? extends Collection> type = (Class<? extends Collection>) context.root().deserialization(input, context);
                final Collection<Object> collection = context.instantiation(type);
                deserialization(input, context, collection);
                yield collection;
            }
            case CREATE_MAP         -> {
                final Class<? extends Map> type = (Class<? extends Map>) context.root().deserialization(input, context);
                final Map<Object, Object> map = context.instantiation(type);
                deserialization(input, context, map);
                yield map;
            }
            case URI                -> java.net.URI.create(input.readUTF());
            case UUID               -> new java.util.UUID(input.readLongLittleEndian(), input.readLongLittleEndian());
            case REGEX              -> Pattern.compile(input.readUTF(), input.readIntLittleEndian());
            case PATH               -> Path.of((java.net.URI) context.root().deserialization(input, context));
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
                        switch (collection) {
                            case Arrays.ArrayList<?> arrayList                               -> {
                                output.write(AS_LIST);
                                serialization(output, context, arrayList);
                            }
                            case Collections.SingletonList<?> singletonList                  -> {
                                output.write(SINGLETON_LIST);
                                context.root().serialization(output, context, singletonList.getFirst());
                            }
                            case Collections.SingletonSet<?> singletonSet                    -> {
                                output.write(SINGLETON_SET);
                                context.root().serialization(output, context, singletonSet.iterator().next());
                            }
                            case ImmutableCollections.AbstractImmutableList<?> immutableList -> {
                                output.write(IMMUTABLE_LIST);
                                serialization(output, context, immutableList);
                            }
                            case ImmutableCollections.AbstractImmutableSet<?> immutableSet   -> {
                                output.write(IMMUTABLE_SET);
                                serialization(output, context, immutableSet);
                            }
                            default                                                          -> {
                                final Class<? extends Collection> type = collection.getClass();
                                if (java.io.Serializable.class.isAssignableFrom(type) && TypeHelper.hasNoArgConstructor(type)) {
                                    output.write(CREATE_COLLECTION);
                                    context.root().serialization(output, context, type);
                                    serialization(output, context, collection);
                                } else {
                                    output.write(UNOPTIMIZED);
                                    super.serialization(output, context, instance);
                                }
                            }
                        }
                    }
                    case Map<?, ?> map            -> {
                        switch (map) {
                            case Collections.SingletonMap<?, ?> singletonMap                  -> {
                                output.write(SINGLETON_MAP);
                                final Map.Entry<?, ?> entry = singletonMap.entrySet().iterator().next();
                                context.root().serialization(output, context, entry.getKey());
                                context.root().serialization(output, context, entry.getValue());
                            }
                            case ImmutableCollections.AbstractImmutableMap<?, ?> immutableMap -> {
                                output.write(IMMUTABLE_MAP);
                                output.writeVarInt(immutableMap.size());
                            }
                            default                                                           -> {
                                final Class<? extends Map> type = map.getClass();
                                if (java.io.Serializable.class.isAssignableFrom(type) && TypeHelper.hasNoArgConstructor(type)) {
                                    output.write(CREATE_MAP);
                                    context.root().serialization(output, context, type);
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
                        output.writeUTF(uri.toString());
                    }
                    case java.util.UUID uuid      -> {
                        output.write(UUID);
                        output.writeLongLittleEndian(uuid.getMostSignificantBits());
                        output.writeLongLittleEndian(uuid.getLeastSignificantBits());
                    }
                    case Pattern regex            -> {
                        output.write(REGEX);
                        output.writeUTF(regex.pattern());
                        output.writeIntLittleEndian(regex.flags());
                    }
                    case Path path               -> {
                        output.write(PATH);
                        context.root().serialization(output, context, path.toUri());
                    }
                    default                       -> {
                        output.write(UNOPTIMIZED);
                        super.serialization(output, context, instance);
                    }
                }
        }
        
        public static void deserialization(final Deserializable.Input input, final Context context, final Collection<Object> collection) throws IOException {
            final int size = input.readVarInt();
            for (int i = 0; i < size; i++)
                collection.add(context.root().deserialization(input, context));
        }
        
        public static void serialization(final Serializable.Output output, final Context context, final Collection<?> collection) throws IOException {
            output.writeVarInt(collection.size());
            for (final Object element : collection)
                context.root().serialization(output, context, element);
        }
        
        public static void deserialization(final Deserializable.Input input, final Context context, final Map<Object, Object> map) throws IOException {
            final int size = input.readVarInt();
            for (int i = 0; i < size; i++)
                map.put(context.root().deserialization(input, context), context.root().deserialization(input, context));
        }
        
        public static void serialization(final Serializable.Output output, final Context context, final Map<?, ?> map) throws IOException {
            output.writeVarInt(map.size());
            final Serializer<Object> root = context.root();
            for (final Map.Entry<?, ?> entry : map.entrySet()) {
                root.serialization(output, context, entry.getKey());
                root.serialization(output, context, entry.getValue());
            }
        }
        
    }
    
    @Deprecated
    @HiddenDanger("RCE(Remote Code Execution) vulnerability")
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    class FieldMapping extends Chain {
        
        @SneakyThrows
        ClassLocal<Map<String, VarHandle>> fieldMappings = {
                type -> ReflectionHelper.allFields(type).stream()
                        .filter(field -> (field.getModifiers() & (Modifier.STATIC | Modifier.TRANSIENT)) == 0)
                        .collect(Collectors.toMap(Field::getName, MethodHandleHelper.lookup()::unreflectVarHandle))
        };
        
        @Default
        @Nullable Predicate<Class<?>> filter = null;
        
        @Default
        boolean forwardCompatibility = false;
        
        @Override
        @SneakyThrows
        public @Nullable Object deserialization(final Deserializable.Input input, final Context context) throws IOException {
            if (filter == null || forwardCompatibility || input.readBoolean()) {
                final Class<?> type = (Class<?>) context.root().deserialization(input, context);
                final Map<String, VarHandle> mapping = fieldMappings[type];
                final Object instance = context.instantiation(type);
                final int count = input.readVarInt();
                for (int i = 0; i < count; i++) {
                    final String name = input.readUTF();
                    final @Nullable VarHandle handle = mapping[name];
                    if (handle == null)
                        throw DebugHelper.breakpointBeforeThrow(new NoSuchFieldException(name));
                    final Object value = context.root().deserialization(input, context);
                    handle.set(instance, value);
                }
                return instance;
            }
            return super.deserialization(input, context);
        }
        
        @Override
        public void serialization(final Serializable.Output output, final Context context, final Object instance) throws IOException {
            final Class<?> type = instance.getClass();
            if (filter == null || filter.test(type)) {
                if (filter != null)
                    output.writeBoolean(true);
                context.root().serialization(output, context, type);
                final Map<String, VarHandle> mapping = fieldMappings[type];
                final int count = mapping.size();
                output.writeVarInt(count);
                for (final Map.Entry<String, VarHandle> entry : mapping.entrySet()) {
                    output.writeUTF(entry.getKey());
                    context.root().serialization(output, context, entry.getValue().get(instance));
                }
            } else
                super.serialization(output, context, instance);
        }
        
    }
    
    @Nullable
    T deserialization(final Deserializable.Input input, final Context context) throws IOException;
    
    void serialization(final Serializable.Output output, final Context context, final @Nullable T instance) throws IOException;
    
}
