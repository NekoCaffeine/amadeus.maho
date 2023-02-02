package amadeus.maho.vm.reflection.hotspot;

import java.util.stream.Stream;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.ToString;
import amadeus.maho.util.dynamic.ClassLocal;
import amadeus.maho.util.runtime.StreamHelper;

@ToString
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
public class InstanceKlass implements HotSpotBase {
    
    public static final HotSpotType
            InstanceKlass = jvm.type("InstanceKlass"),
            ConstantPool  = jvm.type("ConstantPool");
    
    public static final long
            oopSize       = jvm.longConstant("oopSize"),
            _klass_offset = unsafe.getInt(jvm.type("java_lang_Class").global("_klass_offset")),
            _constants    = InstanceKlass.offset("_constants"),
            _fields       = InstanceKlass.offset("_fields"),
            _fields_data  = jvm.type("Array<u2>").offset("_data"),
            _methods      = InstanceKlass.offset("_methods"),
            _methods_data = jvm.type("Array<Method*>").offset("_data");
    
    @Getter
    private static final ClassLocal<InstanceKlass> klassLocals = { InstanceKlass::new };
    
    Class<?> clazz;
    
    long address = oopSize == 8 ? unsafe.getLong(clazz, _klass_offset) : unsafe.getInt(clazz, _klass_offset) & 0xFFFFFFFFL, pool = unsafe.getAddress(address + _constants);
    
    public Stream<KlassField> fields() {
        final long fieldArray = unsafe.getAddress(address + _fields);
        final int methodCount = unsafe.getInt(fieldArray);
        final long fields = fieldArray + _fields_data;
        final int p_index[] = { -1 };
        return StreamHelper.takeWhileNonNull(() -> ++p_index[0] < methodCount ? new KlassField(unsafe.getAddress(fields + p_index[0] * oopSize), pool) : null);
    }
    
    public Stream<KlassMethod> methods() {
        final long methodArray = unsafe.getAddress(address + _methods);
        final int methodCount = unsafe.getInt(methodArray);
        final long methods = methodArray + _methods_data;
        final int p_index[] = { -1 };
        return StreamHelper.takeWhileNonNull(() -> ++p_index[0] < methodCount ? new KlassMethod(unsafe.getAddress(methods + p_index[0] * oopSize)) : null);
    }
    
}
