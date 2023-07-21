package amadeus.maho.util.runtime;

import amadeus.maho.lang.*;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.dynamic.ClassLocal;
import amadeus.maho.util.type.InferredGenericType;
import jdk.internal.ValueBased;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.*;
import java.util.LinkedList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public interface TypeHelper {
    
    @Getter
    @ToString
    @AllArgsConstructor(AccessLevel.PRIVATE)
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    enum Wrapper {
        
        // @formatter:off
        //        wrapperType      simple     primitiveType  simple    char  emptyArray         format
        BOOLEAN(  Boolean.class,   "Boolean", boolean.class, "boolean", 'Z', new boolean[0], Format.unsigned( 1)),
        // These must be in the order defined for widening primitive conversions in JLS 5.1.2
        // Avoid boxing integral types here to defer initialization of internal caches
        BYTE   (     Byte.class,      "Byte",    byte.class,    "byte", 'B', new    byte[0], Format.signed  ( 8)),
        SHORT  (    Short.class,     "Short",   short.class,   "short", 'S', new   short[0], Format.signed  (16)),
        CHAR   (Character.class, "Character",    char.class,    "char", 'C', new    char[0], Format.unsigned(16)),
        INT    (  Integer.class,   "Integer",     int.class,     "int", 'I', new     int[0], Format.signed  (32)),
        LONG   (     Long.class,      "Long",    long.class,    "long", 'J', new    long[0], Format.signed  (64)),
        FLOAT  (    Float.class,     "Float",   float.class,   "float", 'F', new   float[0], Format.floating(32)),
        DOUBLE (   Double.class,    "Double",  double.class,  "double", 'D', new  double[0], Format.floating(64)),
        // VOID must be the last type, since it is "assignable" from any other type:
        VOID   (     Void.class,      "Void",    void.class,    "void", 'V',           null, Format.other   ( 0)),
        ;// @formatter:on
        
        public interface Format {
            
            // @formatter:off
            int
                    SLOT_SHIFT = 0,
                    SIZE_SHIFT = 2,
                    KIND_SHIFT = 12;
            int
                    SIGNED = -1 << KIND_SHIFT,
                    UNSIGNED = 0  << KIND_SHIFT,
                    FLOATING = 1  << KIND_SHIFT;
            int
                    SLOT_MASK = (1 << SIZE_SHIFT - SLOT_SHIFT) - 1,
                    SIZE_MASK = (1 << KIND_SHIFT - SIZE_SHIFT) - 1;
            int
                    INT      = SIGNED   | 32 << SIZE_SHIFT | 1 << SLOT_SHIFT,
                    SHORT    = SIGNED   | 16 << SIZE_SHIFT | 1 << SLOT_SHIFT,
                    BOOLEAN  = UNSIGNED | 1  << SIZE_SHIFT | 1 << SLOT_SHIFT,
                    CHAR     = UNSIGNED | 16 << SIZE_SHIFT | 1 << SLOT_SHIFT,
                    FLOAT    = FLOATING | 32 << SIZE_SHIFT | 1 << SLOT_SHIFT,
                    VOID     = UNSIGNED | 0  << SIZE_SHIFT | 0 << SLOT_SHIFT,
                    NUM_MASK = -1 << SIZE_SHIFT;
            // @formatter:on
            
            static int format(final int kind, final int size, final int slots) = kind | size << SIZE_SHIFT | slots << SLOT_SHIFT;
            
            static int signed(final int size) = format(SIGNED, size, size > 32 ? 2 : 1);
            
            static int unsigned(final int size) = format(UNSIGNED, size, size > 32 ? 2 : 1);
            
            static int floating(final int size) = format(FLOATING, size, size > 32 ? 2 : 1);
            
            static int other(final int slots) = slots << SLOT_SHIFT;
            
        }
        
        Class<?> wrapperType;
        String   wrapperSimpleName;
        Class<?> primitiveType;
        String   primitiveSimpleName;
        char     basicTypeChar;
        Object   emptyArray;
        int      format;
        
        private static final Wrapper[] FROM_PRIM = new Wrapper[16], FROM_WRAP = new Wrapper[16], FROM_CHAR = new Wrapper[16];
        
        private static int hashPrim(final Class<?> x) {
            final String name = x.getName();
            return name.length() < 3 ? 0 : (name.charAt(0) + name.charAt(2)) % 16;
        }
        
        private static int hashWrap(final Class<?> x) {
            final String name = x.getName();
            return name.length() < 13 ? 0 : (3 * name.charAt(11) + name.charAt(12)) % 16;
        }
        
        private static int hashChar(final char x) = (x + (x >> 1)) % 16;
        
        static {
            for (final Wrapper wrapper : values()) {
                // @formatter:off
                FROM_PRIM[hashPrim(wrapper.primitiveType)] = wrapper;
                FROM_WRAP[hashWrap(wrapper.wrapperType)]   = wrapper;
                FROM_CHAR[hashChar(wrapper.basicTypeChar)] = wrapper;
                // @formatter:on
            }
        }
        
        public static @Nullable Wrapper findWrapperType(final Class<?> type) {
            final @Nullable Wrapper wrapper = FROM_WRAP[hashWrap(type)];
            return wrapper != null && wrapper.wrapperType == type ? wrapper : null;
        }
        
        public static @Nullable Wrapper findPrimitiveType(final Class<?> type) {
            final @Nullable Wrapper wrapper = FROM_PRIM[hashPrim(type)];
            return wrapper != null && wrapper.primitiveType == type ? wrapper : null;
        }
        
        public static @Nullable Wrapper findBasicType(final char type) {
            final @Nullable Wrapper wrapper = FROM_CHAR[hashChar(type)];
            return wrapper != null && wrapper.basicTypeChar == type ? wrapper : null;
        }
        
        public Class<?> arrayType() = emptyArray.getClass();
        
        /** How many bits are in the wrapped value?  Returns 0 for OBJECT or VOID. */
        public int bitWidth() = format >> Format.SIZE_SHIFT & Format.SIZE_MASK;
        
        /** How many JVM stack slots occupied by the wrapped value?  Returns 0 for VOID. */
        public int stackSlots() = format >> Format.SLOT_SHIFT & Format.SLOT_MASK;
        
        /** Does the wrapped value occupy a single JVM stack slot? */
        public boolean isSingleWord() = (format & 1 << Format.SLOT_SHIFT) != 0;
        
        /** Does the wrapped value occupy two JVM stack slots? */
        public boolean isDoubleWord() = (format & 2 << Format.SLOT_SHIFT) != 0;
        
        /** Is the wrapped type numeric (not void or object)? */
        public boolean isNumeric() = (format & Format.NUM_MASK) != 0;
        
        /** Is the wrapped type a primitive other than float, double, or void? */
        public boolean isIntegral() = isNumeric() && format < Format.FLOAT;
        
        /** Is the wrapped type one of int, boolean, byte, char, or short? */
        public boolean isSubWordOrInt() = isIntegral() && isSingleWord();
        
        /** Is the wrapped value a signed integral type (one of byte, short, int, or long)? */
        public boolean isSigned() = format < Format.VOID;
        
        /** Is the wrapped value an unsigned integral type (one of boolean or char)? */
        public boolean isUnsigned() = format >= Format.BOOLEAN && format < Format.FLOAT;
        
        /** Is the wrapped type either float or double? */
        public boolean isFloating() = format >= Format.FLOAT;
        
        /** Is the wrapped type either void or a reference? */
        public boolean isOther() = (format & ~Format.SLOT_MASK) == 0;
        
        // These are the cases allowed by MethodHandle.asType.
        public boolean isConvertibleFrom(final Wrapper source) = this == source || compareTo(source) >= 0 && ((format & source.format & Format.SIGNED) != 0 || isOther() || source.format == Format.CHAR);
        
        // Wrapped instances of floating-point types are not cached by the JVM.
        private static final Object FLOAT_ZERO = (float) 0, DOUBLE_ZERO = (double) 0;
        
        public @Nullable Object zero() = switch (this) {
            case BOOLEAN -> false;
            case INT     -> 0;
            case BYTE    -> (byte) 0;
            case CHAR    -> (char) 0;
            case SHORT   -> (short) 0;
            case LONG    -> (long) 0;
            case FLOAT   -> FLOAT_ZERO;
            case DOUBLE  -> DOUBLE_ZERO;
            default      -> null;
        };
        
        public <T> T zero(final Class<T> type) = type.cast(zero());
        
        public @Nullable Object wrap(final int value) = switch (this) {
            case VOID    -> null;
            case INT     -> value;
            case LONG    -> (long) value;
            case FLOAT   -> (float) value;
            case DOUBLE  -> (double) value;
            case SHORT   -> (short) value;
            case BYTE    -> (byte) value;
            case CHAR    -> (char) value;
            case BOOLEAN -> (value & 1) > 0;
        };
        
        public @Nullable Object wrap(final Object value) = switch (this) {
            case VOID -> null;
            default   -> {
                final Number number = numberValue(value);
                yield switch (this) {
                    case INT     -> number.intValue();
                    case LONG    -> number.longValue();
                    case FLOAT   -> number.floatValue();
                    case DOUBLE  -> number.doubleValue();
                    case SHORT   -> (short) number.intValue();
                    case BYTE    -> (byte) number.intValue();
                    case CHAR    -> (char) number.intValue();
                    case BOOLEAN -> (number.byteValue() & 1) > 0;
                    default      -> throw new AssertionError(this);
                };
            }
        };
        
        public static @Nullable Number numberValue(final @Nullable Object object) {
            if (object instanceof Number it)
                return it;
            if (object instanceof Character it)
                return (int) it;
            if (object instanceof Boolean it)
                return it ? 1 : 0;
            return null;
        }
        
    }
    
    enum BoundType {EQUAL, LOWER, UPPER}
    
    static Class<?> boxClass(final Class<?> clazz) {
        final @Nullable Wrapper wrapper = Wrapper.findPrimitiveType(clazz);
        return wrapper == null ? clazz : wrapper.wrapperType;
    }
    
    static Class<?> unboxType(final Class<?> clazz) {
        final @Nullable Wrapper wrapper = Wrapper.findWrapperType(clazz);
        return wrapper == null ? clazz : wrapper.primitiveType;
    }
    
    static boolean isPacking(final Class<?> clazz) = Wrapper.findWrapperType(clazz) != null;
    
    static boolean isSimple(final Class<?> clazz) = clazz.isPrimitive() || isPacking(clazz);
    
    static boolean isBasics(final Class<?> clazz) = isSimple(clazz) || clazz == String.class;
    
    static boolean isSubclass(final Class<?> supers, Class<?> clazz) {
        do
            if (supers == clazz)
                return true;
        while ((clazz = clazz.getSuperclass()) != null);
        return false;
    }
    
    static boolean isAnonymousClass(final Class<?> clazz) = clazz.getName().contains("/");
    
    static boolean isInstance(final Class<?> supers, final Class<?> clazz) = supers.isAssignableFrom(clazz) || boxClass(supers) == clazz;
    
    static <T, R> R cast(final T value) = (R) value;
    
    static Class<?> getRootComponentType(Class<?> clazz, final int p_dimensions[]) {
        while (clazz.isArray()) {
            clazz = clazz.getComponentType();
            p_dimensions[0]++;
        }
        return clazz;
    }

    static boolean isValueBased(final Class<?> clazz) = clazz.isAnnotationPresent(ValueBased.class);

    ClassLocal<Class<?>> arrayTypeLocal = { type -> Array.newInstance(type, 0).getClass() };
    
    static <T> Class<T[]> arrayType(final Class<T> type) = (Class<T[]>) arrayTypeLocal[type];
    
    static <T> IntFunction<T[]> arrayConstructor(final Class<T> type) = size -> (T[]) Array.newInstance(type, size);
    
    @Getter
    @SneakyThrows
    ClassLocal<MethodHandle> noArgConstructorLocal = { type -> MethodHandleHelper.lookup().findConstructor(type, MethodType.methodType(void.class)), true };
    
    static MethodHandle noArgConstructorHandle(final Class<?> type) = noArgConstructorLocal()[type];
    
    @SneakyThrows
    static <T> Supplier<T> noArgConstructor(final Class<T> type) {
        final MethodHandle handle = noArgConstructorHandle(type);
        return () -> (T) handle.invoke();
    }
    
    MethodType check = MethodType.methodType(boolean.class, Object.class);
    
    @SneakyThrows
    MethodHandle
            isInstance = MethodHandleHelper.lookup().findSpecial(Class.class, "isInstance", MethodType.methodType(boolean.class, Object.class), Class.class),
            test       = MethodHandleHelper.lookup().findVirtual(Predicate.class, "test", MethodType.methodType(boolean.class, Object.class));
    
    @SneakyThrows
    private static @Nullable MethodHandle mergeMethodHandle(final List<MethodHandle> handles, final StreamHelper.MatchType matchType) = switch (handles.size()) {
        case 0  -> null;
        case 1  -> handles.get(0);
        default -> test.bindTo(switch (matchType) {
            case ANY  -> (Predicate<?>) target -> handles.stream().anyMatch(handle -> (boolean) handle.invoke(target));
            case ALL  -> (Predicate<?>) target -> handles.stream().allMatch(handle -> (boolean) handle.invoke(target));
            case NONE -> (Predicate<?>) target -> handles.stream().noneMatch(handle -> (boolean) handle.invoke(target));
        });
    };
    
    static @Nullable MethodHandle typeParameterFilter(final @Nullable Type expectedType, final BoundType boundType) {
        if (expectedType == null)
            return null;
        if (expectedType instanceof Class expectedClass)
            return switch (boundType) {
                case EQUAL -> test.bindTo((Predicate<Object>) target -> target == null || target.getClass() == expectedClass);
                case LOWER -> test.bindTo((Predicate<Object>) target -> target == null || target.getClass().isAssignableFrom(expectedClass));
                case UPPER -> isInstance.bindTo(expectedType);
            };
        if (expectedType instanceof WildcardType wildcardType) {
            final List<MethodHandle> handles = new LinkedList<>();
            Stream.of(wildcardType.getLowerBounds())
                    .map(type -> typeParameterFilter(type, BoundType.LOWER))
                    .nonnull()
                    .forEach(handles::add);
            Stream.of(wildcardType.getUpperBounds())
                    .map(type -> typeParameterFilter(type, BoundType.UPPER))
                    .nonnull()
                    .forEach(handles::add);
            return mergeMethodHandle(handles, StreamHelper.MatchType.ALL);
        }
        throw new UnsupportedOperationException("Unsupported expectedType: %s(%s)".formatted(expectedType, expectedType.getClass()));
    }
    
    @SneakyThrows
    static @Nullable MethodHandle typeParameterFilter(final Class<?> owner, final TypeVariable<? extends Class<?>> typeParameter, final @Nullable Type expectedType) {
        final @Nullable MethodHandle checker = typeParameterFilter(expectedType, BoundType.EQUAL);
        if (checker == null)
            return null;
        final MethodHandles.Lookup lookup = MethodHandleHelper.lookup();
        final List<MethodHandle> handles = Stream.of(owner.getDeclaredFields())
                .filter(ReflectionHelper.noneMatch(ReflectionHelper.STATIC))
                .filter(field -> field.getGenericType() == typeParameter)
                .map(field -> MethodHandles.collectArguments(checker, 0, lookup.unreflectVarHandle(field).toMethodHandle(VarHandle.AccessMode.GET).asType(MethodType.methodType(Object.class, owner))))
                .toList();
        return mergeMethodHandle(handles, StreamHelper.MatchType.ANY);
    }
    
    static @Nullable MethodHandle typeParametersFilter(final Class<?> owner, final Type... expectedTypes) {
        final int index[] = { 0 };
        final List<MethodHandle> handles = Stream.of(owner.getTypeParameters())
                .map(typeParameter -> typeParameterFilter(owner, typeParameter, expectedTypes[index[0]++]))
                .nonnull()
                .toList();
        return mergeMethodHandle(handles, StreamHelper.MatchType.ALL);
    }
    
    static Class<?> erase(final Type type) = switch (type) {
        case Class<?> clazz                          -> clazz;
        case ParameterizedType parameterizedType     -> (Class<?>) parameterizedType.getRawType();
        case GenericArrayType genericArrayType       -> arrayType(erase(genericArrayType.getGenericComponentType()));
        case TypeVariable<?> typeVariable            -> {
            final Type bounds[] = typeVariable.getBounds();
            yield bounds.length > 0 ? erase(bounds[0]) : Object.class;
        }
        case WildcardType wildcardType               -> {
            final Type bounds[] = wildcardType.getUpperBounds();
            yield bounds.length > 0 ? erase(bounds[0]) : Object.class;
        }
        case InferredGenericType inferredGenericType -> inferredGenericType.erasedType();
        default                                      -> throw new IllegalStateException("Unexpected value: " + type);
    };
    
}
