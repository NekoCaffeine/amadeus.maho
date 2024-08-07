package amadeus.maho.util.runtime;

import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterators;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import amadeus.maho.lang.Extension;
import amadeus.maho.lang.inspection.Nullable;

@Extension
public interface CollectionHelper {
    
    static <C extends Collection<T>, T> C sort(final C collection, final Comparator<? super T> comparator = (v1, v2) -> ((Comparable<T>) v1).compareTo(v2)) {
        final List<T> sorted = collection.stream().sorted(comparator).toList();
        collection.clear();
        collection.addAll(sorted);
        return collection;
    }
    
    static <L extends List<T>, T> boolean startsWith(final L $this, final List<T> prefix) {
        final int size = prefix.size();
        return $this.size() >= size && $this.subList(0, size).equals(prefix);
    }
    
    static <L extends List<T>, T> boolean endsWith(final L $this, final List<T> suffix) {
        final int size = suffix.size(), offset = $this.size() - size;
        return offset >= 0 && $this.subList(offset, offset + size).equals(suffix);
    }
    
    static <L extends List<T>, T> T GET(final L list, final int index) {
        final int size = list.size(), i = index < 0 ? size + index : index;
        // noinspection DataFlowIssue
        return i > -1 && i < size ? list.get(i) : null;
    }
    
    static <L extends List<T>, T> @Nullable T PUT(final L list, final int index, final @Nullable T element) {
        list.set(index < 0 ? list.size() + index : index, element);
        return element;
    }
    
    static <C extends Collection<T>, T> C PLUSEQ(final C collection, final @Nullable T value) {
        collection.add(value);
        return collection;
    }
    
    static <C extends Collection<T>, T> C MULEQ(final C collection, final Collection<? extends T> value) {
        collection.addAll(value);
        return collection;
    }
    
    static <C extends Collection<T>, T> C MINUSEQ(final C collection, final @Nullable T value) {
        collection.remove(value);
        return collection;
    }
    
    static <C extends Collection<T>, T> C DIVEQ(final C collection, final Collection<?> value) {
        collection.removeAll(value);
        return collection;
    }
    
    static <C extends Collection<T>, T> C GTGTGTEQ(final C collection, final Collection<? extends T> value) {
        collection.clear();
        collection.addAll(value);
        return collection;
    }
    
    static <M extends Map<K, V>, K, V> M MULEQ(final M map, final Map<K, V> value) {
        map.putAll(value);
        return map;
    }
    
    static <M extends Map<K, V>, K, V> M MINUSEQ(final M map, final @Nullable K key) {
        map.remove(key);
        return map;
    }
    
    static <T> boolean notContains(final Collection<T> $this, final T value) = !$this.contains(value);
    
    static <T> boolean nonEmpty(final Collection<T> $this) = !$this.isEmpty();
    
    static <T> boolean GET(final Set<T> set, final @Nullable Object value) = set.contains(value);
    
    static <K, V> @Nullable V GET(final Map<K, V> map, final @Nullable K key) = map.get(key);
    
    static <K, V> @Nullable V PUT(final Map<K, V> map, final @Nullable K key, final @Nullable V value) = map.put(key, value);
    
    static <K, V> boolean nonEmpty(final Map<K, V> $this) = !$this.isEmpty();
    
    static <M extends Map<K, V>, K, V> M PLUSEQ(final M map, final Map<K, V> value) {
        map.putAll(value);
        return map;
    }
    
    static <T> @Nullable Optional<T> lookup(final Collection<T> $this, final Predicate<T> predicate) = $this.stream().filter(predicate).findFirst();
    
    static <T> @Nullable Optional<T> lookup(final Collection<T> $this, final Class<T> type) = $this.stream().cast(type).findFirst();
    
    static <L extends List<T>, T> L LTLT(final L list, final @Nullable T value) {
        list.addLast(value);
        return list;
    }

    static <L extends List<T>, T> L GTGT(final L list, final @Nullable T value) {
        list.addFirst(value);
        return list;
    }

    static <L extends List<T>, T> L PREDEC(final L list) {
        list.removeFirst();
        return list;
    }

    static <L extends List<T>, T> L POSTDEC(final L list) {
        list.removeLast();
        return list;
    }
    
    static <T> Iterator<T> descendingListIterator(final List<T> $this) {
        final ListIterator<T> iterator = $this.listIterator($this.size());
        return new Iterator<>() {
            
            public T next() = iterator.previous();
            
            public boolean hasNext() = iterator.hasPrevious();
            
        };
    }
    
    static <T> Stream<T> descendingListStream(final List<T> $this) = StreamSupport.stream(Spliterators.spliteratorUnknownSize(descendingListIterator($this), 0), false);
    
    static <T> Stream<T> descendingStream(final Deque<T> $this) = StreamSupport.stream(Spliterators.spliteratorUnknownSize($this.descendingIterator(), 0), false);
    
    @Extension.Operator("&")
    static <T> Collection<T> intersection(final Collection<T> a, final Collection<T> b) = a.stream().filter(b::contains).toList();
    
}
