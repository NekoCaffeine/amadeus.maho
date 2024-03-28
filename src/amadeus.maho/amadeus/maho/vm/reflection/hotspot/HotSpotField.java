package amadeus.maho.vm.reflection.hotspot;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.inspection.Nullable;

@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
public class HotSpotField implements Comparable<HotSpotField> {
    
    String name;
    @Nullable String typeName;
    long offset;
    boolean isStatic;
    
    @Override
    public int compareTo(final HotSpotField o) {
        if (isStatic != o.isStatic)
            return isStatic ? -1 : 1;
        return Long.compare(offset, o.offset);
    }
    
    @Override
    public String toString() {
        if (isStatic)
            return STR."static \{typeName}\{' '}\{name} @ 0x\{Long.toHexString(offset)}";
        else
            return STR."\{typeName}\{' '}\{name} @ \{offset}";
    }
    
}
