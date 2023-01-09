package amadeus.maho.vm.tools.hotspot.gc;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.vm.tools.hotspot.WhiteBox;

@Getter
@RequiredArgsConstructor(AccessLevel.PRIVATE)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum GC {

    // Enum values must match CollectedHeap::Name
    Serial(1),
    Parallel(2),
    G1(3),
    Epsilon(4),
    Z(5),
    Shenandoah(6);
    
    int nameId;
    
    public boolean isSupported() = WhiteBox.instance().isGCSupported(nameId);
    
    public boolean isSupportedByJVMCICompiler() = WhiteBox.instance().isGCSupportedByJVMCICompiler(nameId);
    
    public boolean isSelected() = WhiteBox.instance().isGCSelected(nameId);
    
    public static boolean isSelectedErgonomically() = WhiteBox.instance().isGCSelectedErgonomically();
    
    public static GC selected() {
        for (final GC gc : values())
            if (gc.isSelected())
                return gc;
        throw new IllegalStateException("No selected GC found");
    }
    
}
