package amadeus.maho.util.math;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;

import static amadeus.maho.util.math.MathHelper.*;

@FieldDefaults(level = AccessLevel.PROTECTED)
public class PowerSetIterator<E> implements Iterator<LinkedList<E>> {
    
    int minSize, maxSize;
    E elements[];
    long bits, count;
    
    public PowerSetIterator(final Collection<E> elements, final int minSize = 0, final int maxSize = elements.size()) {
        if (elements.size() > 63)
            throw new UnsupportedOperationException("elements.size() = " + elements.size() + " > 63");
        if (minSize < 0)
            throw new IllegalArgumentException("minSize = " + minSize + " < 0");
        this.minSize = minSize;
        this.maxSize = min(maxSize, elements.size());
        this.elements = (E[]) elements.toArray();
        reset();
    }
    
    public void reset() {
        for (int n = 0; n < minSize; n++)
            bits |= 1L << n;
        count = minSize;
    }
    
    @Override
    public boolean hasNext() = (bits & 1L << elements.length) == 0;
    
    @Override
    public LinkedList<E> next() {
        final LinkedList<E> result = { };
        for (int i = 0; i < elements.length; i++)
            if ((bits & 1L << i) != 0)
                result += elements[i];
        do {
            final long lowestIndex = lowestIndex(bits);
            if (count < minSize && lowestIndex > 0)
                for (int i = 0; i < min(lowestIndex, minSize - count); i++)
                    bits |= 1L << i;
            else
                bits++;
            count = Long.bitCount(bits);
        } while (count < minSize);
        return result;
    }
    
    @Override
    public void remove() { throw new UnsupportedOperationException("Not Supported!"); }
    
}
