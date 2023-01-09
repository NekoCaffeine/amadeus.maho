package amadeus.maho.vm.reflection.hotspot;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.ToString;

import static amadeus.maho.vm.reflection.hotspot.InstanceKlass.*;
import static amadeus.maho.vm.reflection.hotspot.KlassField.Flags.*;

@ToString
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
public class KlassField implements HotSpotBase {
    
    public interface Flags {
        
        int
                access_flags_offset    = 0,
                name_index_offset      = 1,
                signature_index_offset = 2,
                initval_index_offset   = 3,
                low_packed_offset      = 4,
                high_packed_offset     = 5,
                field_slots            = 6;
        
    }
    
    public static final long field_slots = jvm.intConstant("FieldInfo::field_slots");
    
    long address, pool;
    
    String name = name(), signature = signature();
    
    public int nameIndex() = jvm.getShort(address + name_index_offset) & 0xFFFF;
    
    public int signatureIndex() = jvm.getShort(address + signature_index_offset) & 0xFFFF;
    
    public String name() = jvm.getSymbol(pool + ConstantPool.size + nameIndex() * oopSize);
    
    public String signature() = jvm.getSymbol(pool + ConstantPool.size + signatureIndex() * oopSize);
    
    public short flags() = jvm.getShort(address + access_flags_offset);
    
    public void flags(final short value) = jvm.putShort(address + access_flags_offset, value);
    
    public void flag(final short flag, final boolean mark) = flags((short) (mark ? flags() | flag : flags() & ~flag));
    
    public boolean flag(final short flag) = (flags() & flag) != 0;
    
}
