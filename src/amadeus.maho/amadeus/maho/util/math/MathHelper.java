package amadeus.maho.util.math;

import amadeus.maho.lang.Include;

@Include(Math.class)
public interface MathHelper {
    
    static long lowestIndex(long i) {
        int n = 0;
        while (n < 64 && (i & 1L) == 0) {
            n++;
            i = i >>> 1;
        }
        return n;
    }
    
    static int log2(final int value) {
        if(value < 1)
            throw new IllegalArgumentException(STR."value: \{value}");
        return 31 - Integer.numberOfLeadingZeros(value);
    }
    
    static int log2(final long value) {
        if(value < 1)
            throw new IllegalArgumentException(STR."value: \{value}");
        return 63 - Long.numberOfLeadingZeros(value);
    }
    
    static boolean anyMatch(final long flags, final long mask) = (flags & mask) != 0;
    
    static boolean allMatch(final long flags, final long mask) = (flags & mask) == mask;
    
    static boolean noneMatch(final long flags, final long mask) = (flags & mask) == 0;
    
    static boolean anyMatch(final int flags, final int mask) = (flags & mask) != 0;
    
    static boolean allMatch(final int flags, final int mask) = (flags & mask) == mask;
    
    static boolean noneMatch(final int flags, final int mask) = (flags & mask) == 0;
    
}
