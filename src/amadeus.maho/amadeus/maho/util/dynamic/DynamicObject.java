package amadeus.maho.util.dynamic;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.FieldNode;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.Unsupported;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.generator.MethodGenerator;
import amadeus.maho.util.runtime.DebugHelper;
import amadeus.maho.util.runtime.MethodHandleHelper;
import amadeus.maho.util.runtime.ObjectHelper;
import amadeus.maho.util.runtime.TypeHelper;

public sealed interface DynamicObject {
    
    interface BaseView {
        
        DynamicObject dynamic();
        
        @SneakyThrows
        ClassLocal<MethodHandle> viewConstructorHandleLocal = { it -> MethodHandleHelper.lookup().findConstructor(makeViewWrapper(it), MethodType.methodType(void.class, DynamicObject.class)) };
        
        Type TYPE_DYNAMIC_OBJECT = Type.getType(DynamicObject.class);
        
        Method
                GET             = { "GET", Type.getMethodDescriptor(TYPE_DYNAMIC_OBJECT, ASMHelper.TYPE_STRING) },
                PUT             = { "PUT", Type.getMethodDescriptor(Type.VOID_TYPE, ASMHelper.TYPE_STRING, ASMHelper.TYPE_OBJECT) },
                AS              = { "as", Type.getMethodDescriptor(ASMHelper.TYPE_OBJECT, ASMHelper.TYPE_CLASS) },
                isUndefined     = { "isUndefined", Type.getMethodDescriptor(Type.BOOLEAN_TYPE) },
                undefinedToNull = { "undefinedToNull", Type.getMethodDescriptor(TYPE_DYNAMIC_OBJECT) };
        
        static <T> Class<? extends T> makeViewWrapper(final Class<T> viewType) {
            if (!viewType.isInterface())
                throw DebugHelper.breakpointBeforeThrow(new IllegalArgumentException("View must be an interface"));
            final Wrapper<T> wrapper = { viewType, "View", BaseView.class };
            final Type wrapperType = wrapper.wrapperType();
            final FieldNode dynamicField = wrapper.field(DynamicObject.class, "dynamic");
            wrapper.copyAllConstructors(dynamicField);
            final Set<String> unimplementedMethods = wrapper.unimplementedMethods().map(Wrapper::methodKey).collect(Collectors.toUnmodifiableSet());
            wrapper.inheritableUniqueSignatureMethods().filter(ObjectHelper::nonObjectMethod).forEach(method -> {
                final Class<?> returnType = method.getReturnType();
                if (method.getParameterCount() == 0 && returnType != void.class) {
                    final MethodGenerator generator = wrapper.wrap(method);
                    generator.loadThis();
                    generator.getField(wrapperType, dynamicField);
                    if (!(method.getName().equals("dynamic") && returnType == DynamicObject.class)) {
                        generator.push(method.getName());
                        generator.invokeInterface(TYPE_DYNAMIC_OBJECT, GET);
                        if (returnType != DynamicObject.class) {
                            final boolean implemented = !unimplementedMethods.contains(Wrapper.methodKey(method));
                            @Nullable Label fallback = null, end = null;
                            if (implemented) {
                                fallback = generator.newLabel();
                                end = generator.newLabel();
                                generator.dup();
                                generator.invokeInterface(TYPE_DYNAMIC_OBJECT, isUndefined);
                                generator.ifZCmp(Opcodes.IFNE, fallback);
                            }
                            generator.invokeInterface(TYPE_DYNAMIC_OBJECT, undefinedToNull);
                            final Type targetType = Type.getType(returnType);
                            generator.push(targetType);
                            generator.invokeInterface(TYPE_DYNAMIC_OBJECT, AS);
                            generator.unbox(targetType);
                            if (implemented) {
                                generator.goTo(end);
                                generator.mark(fallback);
                                generator.loadThis();
                                generator.invokeInterface(wrapperType, Method.getMethod(method));
                                generator.mark(end);
                            }
                        }
                    }
                    generator.returnValue();
                    generator.endMethod();
                } else if (method.getParameterCount() == 1 && returnType == void.class) {
                    final MethodGenerator generator = wrapper.wrap(method);
                    generator.loadThis();
                    generator.getField(wrapperType, dynamicField);
                    generator.loadArg(0);
                    generator.push(method.getName());
                    generator.box(generator.argumentTypes[0]);
                    generator.invokeInterface(TYPE_DYNAMIC_OBJECT, PUT);
                    generator.returnValue();
                    generator.endMethod();
                } else if (unimplementedMethods.contains(Wrapper.methodKey(method))) {
                    final MethodGenerator generator = wrapper.wrap(method);
                    generator.throwException(Type.getType(UnsupportedOperationException.class), STR."Unsupported method: \{method}");
                    generator.endMethod();
                }
            });
            wrapper.context().markCompute(wrapper.node());
            final Class<? extends T> wrapperClass = wrapper.defineWrapperClass();
            return wrapperClass;
        }
        
        @SneakyThrows
        static <T> T view(final DynamicObject dynamic, final Class<T> viewType) = (T) viewConstructorHandleLocal[viewType].invoke(dynamic);
        
    }
    
    @Unsupported
    @EqualsAndHashCode
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true) final class ObjectUnit implements DynamicObject {
        
        Object object;
        
        @Override
        public DynamicObject GET(final int index) = switch (object) {
            case List<?> list   -> checkIndex(index, list.size()) ? wrap(list[index]) : undefined;
            case Object[] array -> checkIndex(index, array.length) ? wrap(array[index]) : undefined;
            default             -> object.getClass().isArray() && checkIndex(index, Array.getLength(object)) ? wrap(Array.get(object, index)) : undefined;
        };
        
        @Override
        public void PUT(final int index, final DynamicObject value) = switch (object) {
            case List<?> list   -> {
                if (checkIndex(index, list.size()))
                    list[index] = value.as();
            }
            case Object[] array -> {
                if (checkIndex(index, array.length))
                    array[index] = value.as();
            }
            default             -> {
                if (object.getClass().isArray() && checkIndex(index, Array.getLength(object)))
                    Array.set(object, index, value.as());
            }
        };
        
        @Override
        public DynamicObject GET(final String key) {
            final @Nullable FieldsMap.Info info = FieldsMap.fieldsMapLocal()[object.getClass()][key];
            return info == null ? undefined : wrap(info.handle().get(object));
        }
        
        @Override
        public void PUT(final String key, final DynamicObject value) {
            final @Nullable FieldsMap.Info info = FieldsMap.fieldsMapLocal()[object.getClass()][key];
            info?.handle().set(object, value.as());
        }
        
        @Override
        public <T> T as() = (T) object;
        
        @Override
        public String asString() = as();
        
        @Override
        public BigDecimal asDecimal() = as();
        
        @Override
        public Number asNumber() = as();
        
        @Override
        public byte asByte() = object instanceof Number number ? number.byteValue() : as();
        
        @Override
        public short asShort() = object instanceof Number number ? number.shortValue() : as();
        
        @Override
        public int asInt() = object instanceof Number number ? number.intValue() : as();
        
        @Override
        public long asLong() = object instanceof Number number ? number.longValue() : as();
        
        @Override
        public float asFloat() = object instanceof Number number ? number.floatValue() : as();
        
        @Override
        public double asDouble() = object instanceof Number number ? number.doubleValue() : as();
        
        @Override
        public boolean asBoolean() = as();
        
        @Override
        public String toString() = object.toString();
        
    }
    
    @Unsupported
    @EqualsAndHashCode
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true) final class MapUnit implements DynamicObject {
        
        @Getter
        @Default
        Map<String, DynamicObject> asMap = new LinkedHashMap<>();
        
        @Override
        public <T> @Nullable T as() = (T) asMap;
        
        @Override
        public DynamicObject GET(final String key) = asMap.containsKey(key) ? asMap[key] ?? undefined : undefined;
        
        @Override
        public void PUT(final String key, final DynamicObject value) = asMap[key] = value;
        
        @Override
        public String toString() = asMap.toString();
        
    }
    
    @Getter
    @Unsupported
    @EqualsAndHashCode
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true) final class ArrayUnit implements DynamicObject {
        
        @Default
        List<DynamicObject> asList = new ArrayList<>();
        
        @Override
        public <T> @Nullable T as() = (T) asList;
        
        @Override
        public <T> @Nullable T as(final Class<T> type) {
            if (type.isArray())
                return (T) asList.stream().map(DynamicObject::as).toArray(size -> (Object[]) Array.newInstance(type.getComponentType(), size));
            return DynamicObject.super.as(type);
        }
        
        @Override
        public DynamicObject GET(final int index) = asList[index] ?? undefined;
        
        @Override
        public void PUT(final int index, final DynamicObject value) = asList[index] = value;
        
        @Override
        public String toString() = asList.toString();
        
    }
    
    @Getter
    @Unsupported
    @EqualsAndHashCode
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true) final class StringUnit implements DynamicObject {
        
        String asString;
        
        @Override
        public DynamicObject.StringUnit GET(final int index) = { asString.substring(index, index + 1) };
        
        @Override
        public <T> @Nullable T as() = (T) asString;
        
        @Override
        public String toString() = asString;
        
    }
    
    @Getter
    @Unsupported
    @EqualsAndHashCode
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true) final class DecimalUnit implements DynamicObject {
        
        Number asNumber;
        
        @Override
        public <T> @Nullable T as() = (T) asNumber;
        
        @Override
        public String asString() = asNumber.toString();
        
        @Override
        public byte asByte() = asNumber.byteValue();
        
        @Override
        public short asShort() = asNumber.shortValue();
        
        @Override
        public int asInt() = asNumber.intValue();
        
        @Override
        public long asLong() = asNumber.longValue();
        
        @Override
        public float asFloat() = asNumber.floatValue();
        
        @Override
        public double asDouble() = asNumber.doubleValue();
        
        @Override
        public String toString() = asNumber.toString();
        
    }
    
    @Getter
    @Unsupported
    @EqualsAndHashCode
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true) final class BooleanUnit implements DynamicObject {
        
        boolean asBoolean;
        
        @Override
        public <T> @Nullable T as() = (T) (Boolean) asBoolean;
        
        @Override
        public String toString() = Boolean.toString(asBoolean);
        
    }
    
    @Getter
    @ToString
    @Unsupported
    @NoArgsConstructor(AccessLevel.PRIVATE)
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true) final class NullUnit implements DynamicObject {
        
        private static final NullUnit instance = { };
        
        @Override
        public <T> @Nullable T as() = null;
        
        @Override
        public boolean isNull() = true;
        
        @Override
        public String toString() = "null";
        
    }
    
    @Getter
    @ToString
    @Unsupported
    @NoArgsConstructor(AccessLevel.PRIVATE)
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true) final class UndefinedUnit implements DynamicObject {
        
        private static final UndefinedUnit instance = { };
        
        @Override
        public DynamicObject GET(final int index) { throw unsupported(); }
        
        @Override
        public void PUT(final int index, final DynamicObject value) { throw unsupported(); }
        
        @Override
        public DynamicObject GET(final String key) { throw unsupported(); }
        
        @Override
        public void PUT(final String key, final DynamicObject value) { throw unsupported(); }
        
        @Override
        public boolean isUndefined() = true;
        
        @Override
        public String toString() = "undefined";
        
        private static UnsupportedOperationException unsupported() = { "undefined" };
        
    }
    
    UndefinedUnit undefined = UndefinedUnit.instance();
    
    @SneakyThrows
    ClassLocal<MethodHandle> recordConstructorHandleLocal
            = { it -> MethodHandleHelper.lookup().findConstructor(it, MethodType.methodType(void.class, Stream.of(it.getRecordComponents()).map(RecordComponent::getType).toArray(Class[]::new))) };
    
    default DynamicObject GET(final int index) = undefined;
    
    default void PUT(final int index, final DynamicObject value) { }
    
    default void PUT(final int index, @Nullable final Object value) = PUT(index, wrap(value));
    
    default DynamicObject GET(final String key) = undefined;
    
    default void PUT(final String key, final DynamicObject value) { }
    
    default void PUT(final String key, @Nullable final Object value) = PUT(key, wrap(value));
    
    default void PLUSEQ(final DynamicObject value) = asList() += value;
    
    default void PLUSEQ(final Object value) = asList() += wrap(value);
    
    <T> @Nullable T as();
    
    default <T> @Nullable T as(final Class<T> type) {
        if (isNull())
            return null;
        if (type.isRecord())
            return (T) record((Class<? extends Record>) type);
        final Object object = as()!;
        if (TypeHelper.boxClass(type).isAssignableFrom(object.getClass()))
            return (T) object;
        if (type.isInterface())
            return view(type);
        if (type == byte.class || type == Byte.class)
            return (T) (Byte) asByte();
        if (type == short.class || type == Short.class)
            return (T) (Short) asShort();
        if (type == int.class || type == Integer.class)
            return (T) (Integer) asInt();
        if (type == long.class || type == Long.class)
            return (T) (Long) asLong();
        if (type == float.class || type == Float.class)
            return (T) (Float) asFloat();
        if (type == double.class || type == Double.class)
            return (T) (Double) asDouble();
        if (type == char.class || type == Character.class)
            return (T) (Character) asChar();
        if (type == boolean.class || type == Boolean.class)
            return (T) (Boolean) asBoolean();
        if (type == BigDecimal.class)
            return (T) asDecimal();
        if (type == Number.class)
            return (T) asNumber();
        throw new ClassCastException(STR."Cannot cast \{object} to \{type}");
    }
    
    List<DynamicObject> asList();
    
    Map<String, DynamicObject> asMap();
    
    String asString();
    
    default BigDecimal asDecimal() = asNumber() instanceof BigDecimal decimal ? decimal : new BigDecimal(asString());
    
    default Number asNumber() = asDecimal();
    
    default byte asByte() = Byte.parseByte(asString());
    
    default short asShort() = Short.parseShort(asString());
    
    default int asInt() = Integer.parseInt(asString());
    
    default long asLong() = Long.parseLong(asString());
    
    default float asFloat() = Float.parseFloat(asString());
    
    default double asDouble() = Double.parseDouble(asString());
    
    default char asChar() = asString().charAt(0);
    
    default boolean asBoolean() = Boolean.parseBoolean(asString());
    
    default boolean isNull() = false;
    
    default boolean isUndefined() = false;
    
    default DynamicObject undefinedToNull() = isUndefined() ? NullUnit.instance() : this;
    
    default <T> T view(final Class<T> viewType) = BaseView.view(this, viewType);
    
    @SneakyThrows
    default <R extends Record> R record(final Class<R> recordType) = (R) recordConstructorHandleLocal[recordType].invokeWithArguments(Stream.of(recordType.getRecordComponents()).map(component -> {
        final DynamicObject object = this[component.getName()];
        if (object == undefined && !component.isAnnotationPresent(Nullable.class))
            throw new IllegalArgumentException(STR."Missing required component: \{component.getName()}");
        return object == undefined ? null : object.as(component.getType());
    }).toList());
    
    static DynamicObject wrap(final @Nullable Object object) = switch (object) {
        case null                        -> NullUnit.instance();
        case DynamicObject dynamicObject -> dynamicObject;
        case String string               -> new StringUnit(string);
        case Character character         -> new StringUnit(character.toString());
        case Number decimal              -> new DecimalUnit(decimal);
        case Boolean bool                -> new BooleanUnit(bool);
        default                          -> new ObjectUnit(object);
    };
    
    private static boolean checkIndex(final int index, final int length) = index >= 0 && index < length;
    
}
