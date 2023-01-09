package amadeus.maho.vm.reflection.hotspot;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.ToString;

@ToString
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
public class KlassMethod implements HotSpotBase {
    
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
    
    public static final HotSpotType
            Method       = jvm.type("Method");
    public static final long
            _constMethod = Method.offset("_constMethod"),
            _flags       = Method.offset("_flags");
    
    long address;
    
    ConstMethod constMethod = { jvm.getAddress(address + _constMethod) };
    
    public short flags() = jvm.getShort(address + _flags);
    
    public void flags(final short value) = jvm.putShort(address + _flags, value);
    
    public void flag(final short flag, final boolean mark) = flags((short) (mark ? flags() | flag : flags() & ~flag));
    
    public boolean flag(final short flag) = (flags() & flag) != 0;
    
}
