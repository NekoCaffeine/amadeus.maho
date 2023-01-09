package amadeus.maho.util.runtime;

import amadeus.maho.lang.Extension;

@Extension
public interface ComparableHelper {
    
    static <V extends Comparable<V>> boolean LT(final V a, final V b) = a.compareTo(b) < 0;
    
    static <V extends Comparable<V>> boolean LE(final V a, final V b) = a.compareTo(b) <= 0;
    
    static <V extends Comparable<V>> boolean GT(final V a, final V b) = a.compareTo(b) > 0;
    
    static <V extends Comparable<V>> boolean GE(final V a, final V b) = a.compareTo(b) >= 0;
    
}
