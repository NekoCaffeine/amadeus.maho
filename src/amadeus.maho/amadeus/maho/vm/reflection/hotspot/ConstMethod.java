package amadeus.maho.vm.reflection.hotspot;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.ToString;

import static amadeus.maho.vm.reflection.hotspot.HotSpotBase.*;
import static amadeus.maho.vm.reflection.hotspot.InstanceKlass.*;

@ToString
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
public class ConstMethod {
    
    public interface Flags {
        
        short
                _caller_sensitive      = 1 << 0,
                _force_inline          = 1 << 1,
                _dont_inline           = 1 << 2,
                _hidden                = 1 << 3,
                _has_injected_profile  = 1 << 4,
                _intrinsic_candidate   = 1 << 5,
                _reserved_stack_access = 1 << 6,
                _scoped                = 1 << 7;
        
    }
    
    protected static final HotSpotType
            ConstMethod = jvm.type("ConstMethod");
    
    protected static final long
            _constants       = ConstMethod.offset("_constants"),
            _name_index      = ConstMethod.offset("_name_index"),
            _signature_index = ConstMethod.offset("_signature_index"),
            _flags           = ConstMethod.offset("_flags._flags");
    
    long address, pool = unsafe.getAddress(address + _constants);
    
    String name = name(), signature = signature();
    
    public int nameIndex() = unsafe.getShort(address + _name_index) & 0xFFFF;
    
    public int signatureIndex() = unsafe.getShort(address + _signature_index) & 0xFFFF;
    
    public String name() = jvm.getSymbol(pool + ConstantPool.size + nameIndex() * oopSize);
    
    public String signature() = jvm.getSymbol(pool + ConstantPool.size + signatureIndex() * oopSize);
    
    public short flags() = unsafe.getShort(address + _flags);
    
    public void flags(final short value) = unsafe.putShort(address + _flags, value);
    
    public void flag(final short flag, final boolean mark) = flags((short) (mark ? flags() | flag : flags() & ~flag));
    
    public boolean flag(final short flag) = (flags() & flag) != 0;
    
}
