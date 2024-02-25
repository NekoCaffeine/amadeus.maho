package amadeus.maho.util.concurrent;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.util.dynamic.ReferenceCollector;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConcurrentWeakIdentityHashSet<E> extends AbstractSet<E> implements Set<E>, ReferenceCollector.Collectible<E> {
    
    @NoArgsConstructor
    public static class Managed<E> extends ConcurrentWeakIdentityHashSet<E> implements ReferenceCollector.Manageable<E> { }
    
    @Getter
    @Default
    ConcurrentWeakIdentityHashMap<E, Boolean> map = { };
    
    Set<E> set = map.keySet();
    
    public ConcurrentWeakIdentityHashSet(final int initialCapacity, final float loadFactor = 0.75F, final int concurrencyLevel = 1)
            = this(new ConcurrentWeakIdentityHashMap<>(initialCapacity, loadFactor, concurrencyLevel));
    
    public void clear() = map.clear();
    
    public int size() = map.size();
    
    public boolean isEmpty() = map.isEmpty();
    
    public boolean contains(final Object object) = map.containsKey(object);
    
    public boolean remove(final Object object) = map.remove(object) != null;
    
    public boolean add(final E element) = map.put(element, Boolean.TRUE) == null;
    
    public Iterator<E> iterator() = set.iterator();
    
    public Object[] toArray() = set.toArray();
    
    public <T> T[] toArray(final T[] array) = set.toArray(array);
    
    public String toString() = set.toString();
    
    public int hashCode() = set.hashCode();
    
    public boolean equals(final Object object) = object == this || set.equals(object);
    
    public boolean containsAll(final Collection<?> collection) = set.containsAll(collection);
    
    public boolean removeAll(final Collection<?> collection) = set.removeAll(collection);
    
    public boolean retainAll(final Collection<?> collection) = set.retainAll(collection);
    
    @Override
    public void forEach(final Consumer<? super E> action) = set.forEach(action);
    
    @Override
    public boolean removeIf(final Predicate<? super E> filter) = set.removeIf(filter);
    
    @Override
    public Spliterator<E> spliterator() = set.spliterator();
    
    @Override
    public Stream<E> stream() = set.stream();
    
    @Override
    public Stream<E> parallelStream() = set.parallelStream();
    
    @Override
    public ReferenceQueue<E> referenceQueue() = map().referenceQueue();
    
    @Override
    public void collect(final Reference<? extends E> reference) = map().collect(reference);
    
}
