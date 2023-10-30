package amadeus.maho.vm.tools.hotspot.code;

import java.lang.reflect.Executable;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.vm.tools.hotspot.WhiteBox;

@ToString
@EqualsAndHashCode
@FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
public class NMethod extends CodeBlob {
    
    public static @Nullable NMethod get(final Executable method, final boolean isOsr) {
        final @Nullable Object nMethod[] = WhiteBox.instance().getNMethod(method, isOsr);
        return nMethod == null ? null : new NMethod(nMethod);
    }
    
    private NMethod(final Object obj[]) {
        super((Object[]) obj[0]);
        assert obj.length == 5;
        comp_level = (Integer) obj[1];
        insts = (byte[]) obj[2];
        compile_id = (Integer) obj[3];
        entry_point = (Long) obj[4];
    }
    
    byte insts[];
    int  comp_level;
    int  compile_id;
    long entry_point;
    
}
