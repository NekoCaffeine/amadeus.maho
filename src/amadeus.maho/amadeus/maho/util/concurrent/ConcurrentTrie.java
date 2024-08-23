package amadeus.maho.util.concurrent;

import java.lang.invoke.VarHandle;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.control.LinkedIterator;
import amadeus.maho.util.dynamic.LookupHelper;
import amadeus.maho.util.runtime.DebugHelper;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConcurrentTrie<P, E, V> implements ConcurrentMap<P, V> {
    
    public static class Array<E, V> extends ConcurrentTrie<E[], E, V> {
        
        public Array(final Supplier<ConcurrentMap<E, Node<E[], E, V>>> mapMaker = ConcurrentHashMap::new) = super(Function.identity(), Function.identity(), mapMaker);
        
    }
    
    @Getter
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Node<P, E, V> {
        
        protected static final VarHandle
                childrenHandle = LookupHelper.<Node>varHandle(it -> it.children),
                valueHandle    = LookupHelper.<Node>varHandle(it -> it.value);
        
        final ConcurrentTrie<P, E, V> trie;
        
        @Default
        final @Nullable Node<P, E, V> parent = null;
        
        final E path;
        
        @Setter
        volatile @Nullable V value;
        
        @Getter(lazy = true)
        final ConcurrentMap<E, Node<P, E, V>> children = trie().createMap();
        
        { trie().nodes() += this; }
        
        public boolean isEmpty() = value == null && isChildrenEmpty();
        
        public boolean isChildrenEmpty() = children == null || children.isEmpty();
        
        public E[] paths() = (E[]) LinkedIterator.of(Node::parent, this).stream(true).toList().descendingListStream().toArray();
        
        public @Nullable Node<P, E, V> next(final E path) = ((ConcurrentMap<E, Node<P, E, V>>) childrenHandle.getOpaque(this))?.get(path) ?? null;
        
        public @Nullable Node<P, E, V> locate(final E... paths) {
            @Nullable Node<P, E, V> node = this;
            for (final E path : paths) {
                node = next(path);
                if (node == null)
                    return null;
            }
            return node;
        }
        
        public Node<P, E, V> reach(final E... paths) {
            final Function<E, Node<P, E, V>> factory = trie().factory();
            Node<P, E, V> node = this;
            for (final E path : paths)
                node = node.children().computeIfAbsent(path, factory);
            return node;
        }
        
        public Node<P, E, V> reach(final V value, final E... paths) {
            final Node<P, E, V> reached = reach(paths);
            reached.value(value);
            return reached;
        }
        
        public @Nullable Node<P, E, V> remove() {
            final @Nullable Node<P, E, V> parent = parent();
            if (parent == null)
                throw DebugHelper.breakpointBeforeThrow(new IllegalStateException("remove root element"));
            final @Nullable Node<P, E, V> removed = parent.children().remove(path());
            if (removed != null) {
                trie().nodes() -= this;
                children?.values().forEach(Node::remove);
            }
            return removed;
        }
        
        public void checkRemove(final boolean valueIsNull) {
            if (valueIsNull ? isChildrenEmpty() : isEmpty())
                remove();
        }
        
        public @Nullable V put(final @Nullable V value = null) {
            try {
                return (V) valueHandle.getAndSet(this, value);
            } finally { checkRemove(value == null); }
        }
        
        public @Nullable V putIfAbsent(final @Nullable V value) {
            try {
                return (V) valueHandle.compareAndExchange(this, null, value);
            } finally { checkRemove(value == null); }
        }
        
        public @Nullable V getOrCreate(final Supplier<V> creator) {
            final @Nullable V oldValue = (V) valueHandle.getVolatile(this);
            if (oldValue != null)
                return oldValue;
            return (V) valueHandle.compareAndExchange(this, null, creator.get()) ?? (V) valueHandle.getVolatile(this);
        }
        
        public boolean remove(final @Nullable V value) {
            try {
                return valueHandle.compareAndSet(this, value, null);
            } finally { checkRemove(true); }
        }
        
        public boolean replace(final @Nullable V oldValue, final @Nullable V newValue) = valueHandle.compareAndSet(this, oldValue, newValue);
        
        public @Nullable V replaceNonNull(final @Nullable V newValue) {
            while (value != null) {
                final @Nullable V exchange = (V) valueHandle.compareAndExchange(this, value, newValue);
                if (exchange != null)
                    return exchange;
            }
            return null;
        }
        
        public void clear() {
            if (children != null)
                List.copyOf(children.values()).forEach(Node::remove);
        }
        
    }
    
    Function<P, E[]> splitter;
    
    Function<E[], P> merger;
    
    @Default
    Supplier<ConcurrentMap<E, Node<P, E, V>>> mapMaker = ConcurrentHashMap::new;
    
    Function<E, Node<P, E, V>> factory = this::createNode;
    
    Set<Node<P, E, V>> nodes = ConcurrentHashMap.newKeySet();
    
    Node<P, E, V> root = createNode();
    
    protected ConcurrentMap<E, Node<P, E, V>> createMap() = mapMaker().get();
    
    public Node<P, E, V> createNode(final @Nullable E path = null) = { this, path };
    
    public E[] as(final Object key) = splitter().apply((P) key);
    
    @Override
    public int size() = nodes().size();
    
    @Override
    public boolean isEmpty() = nodes().isEmpty();
    
    @Override
    public boolean containsKey(final Object key) = root().locate(as(key))?.value() ?? null != null;
    
    @Override
    public boolean containsValue(final Object value) = values().contains(value);
    
    @Override
    public @Nullable V get(final Object key) = root().locate(as(key))?.value() ?? null;
    
    @Override
    public @Nullable V put(final P key, final V value) = root().reach(as(value)).put(value);
    
    @Override
    public @Nullable V remove(final Object key) = root().locate(as(key))?.put() ?? null;
    
    @Override
    public void putAll(final Map<? extends P, ? extends V> map) = map.forEach(this::put);
    
    @Override
    public void clear() = root().clear();
    
    @Override
    public Set<P> keySet() = nodes().stream().map(Node::paths).map(merger()).collect(Collectors.toSet());
    
    @Override
    public Collection<V> values() = nodes().stream().map(Node::value).nonnull().toList();
    
    @Override
    public Set<Entry<P, V>> entrySet() = nodes().stream().map(node -> Map.entry(merger().apply(node.paths()), node.value())).collect(Collectors.toSet());
    
    @Override
    public V putIfAbsent(final P key, final V value) = root().reach(as(value)).putIfAbsent(value);
    
    @Override
    public boolean remove(final Object key, final Object value) = root().locate(as(key))?.remove((V) value) ?? false;
    
    @Override
    public boolean replace(final P key, final V oldValue, final V newValue) = root().locate(as(key))?.replace(oldValue, newValue) ?? false;
    
    @Override
    public V replace(final P key, final V value) = root().locate(as(key))?.replaceNonNull(value) ?? null;
    
}
