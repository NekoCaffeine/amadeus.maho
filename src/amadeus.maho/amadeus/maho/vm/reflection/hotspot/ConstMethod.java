package amadeus.maho.vm.reflection.hotspot;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.ToString;

import static amadeus.maho.vm.reflection.hotspot.InstanceKlass.*;

@ToString
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
public class ConstMethod implements HotSpotBase {
    
    protected static final HotSpotType
            ConstMethod = jvm.type("ConstMethod");
    
    protected static final long
            _constants = ConstMethod.offset("_constants"),
            _name_index = ConstMethod.offset("_name_index"),
            _signature_index = ConstMethod.offset("_signature_index");
    
    long address, pool = jvm.getAddress(address + _constants);
    
    String name = name(), signature = signature();
    
    public int nameIndex() = jvm.getShort(address + _name_index) & 0xFFFF;
    
    public int signatureIndex() = jvm.getShort(address + _signature_index) & 0xFFFF;
    
    public String name() = jvm.getSymbol(pool + ConstantPool.size + nameIndex() * oopSize);
    
    public String signature() = jvm.getSymbol(pool + ConstantPool.size + signatureIndex() * oopSize);
    
}
