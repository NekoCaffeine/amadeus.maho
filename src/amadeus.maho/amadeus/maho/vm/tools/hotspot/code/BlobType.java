package amadeus.maho.vm.tools.hotspot.code;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.util.EnumSet;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.vm.tools.hotspot.WhiteBox;

import static java.lang.Boolean.TRUE;

@RequiredArgsConstructor(AccessLevel.PRIVATE)
@FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
public enum BlobType {
    
    // Execution level 1 and 4 (non-profiled) nmethods (including native nmethods)
    MethodNonProfiled(0, "CodeHeap 'non-profiled nmethods'", "NonProfiledCodeHeapSize") {
        @Override
        public boolean allowTypeWhenOverflow(final BlobType type) = super.allowTypeWhenOverflow(type) || type == MethodProfiled;
    },
    // Execution level 2 and 3 (profiled) nmethods
    MethodProfiled(1, "CodeHeap 'profiled nmethods'", "ProfiledCodeHeapSize") {
        @Override
        public boolean allowTypeWhenOverflow(final BlobType type) = super.allowTypeWhenOverflow(type) || type == MethodNonProfiled;
    },
    // Non-nmethods like Buffers, Adapters and Runtime Stubs
    NonNMethod(2, "CodeHeap 'non-nmethods'", "NonNMethodCodeHeapSize") {
        @Override
        public boolean allowTypeWhenOverflow(final BlobType type) = super.allowTypeWhenOverflow(type) || type == MethodNonProfiled || type == MethodProfiled;
    },
    // All types (No code cache segmentation)
    All(3, "CodeCache", "ReservedCodeCacheSize");
    
    int id;
    String beanName, sizeOptionName;
    
    public @Nullable MemoryPoolMXBean getMemoryPool() = ~ManagementFactory.getMemoryPoolMXBeans().stream().filter(bean -> beanName.equals(bean.getName()));
    
    public boolean allowTypeWhenOverflow(final BlobType type) = type == this;
    
    public static EnumSet<BlobType> getAvailable() {
        final WhiteBox whiteBox = WhiteBox.instance();
        if (whiteBox.getBooleanVMFlag("SegmentedCodeCache") != TRUE) // only All for non segmented world
            return EnumSet.of(All);
        if (System.getProperty("java.vm.info").startsWith("interpreted ")) // // only NonNMethod for -Xint
            return EnumSet.of(NonNMethod);
        final EnumSet<BlobType> result = EnumSet.complementOf(EnumSet.of(All));
        if (whiteBox.getBooleanVMFlag("TieredCompilation") != TRUE || whiteBox.getIntxVMFlag("TieredStopAtLevel") <= 1) // there is no MethodProfiled in non tiered world or pure C1
            result -= MethodProfiled;
        return result;
    }
    
    public long getSize() = WhiteBox.instance().getUintxVMFlag(sizeOptionName);
    
}
