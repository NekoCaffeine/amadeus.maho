package amadeus.maho.lang.javac.multithreaded.concurrent;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.util.Name;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.javac.JavacContext;
import amadeus.maho.lang.javac.multithreaded.MultiThreadedContext;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.control.LinkedIterator;

@TransformProvider
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
public class ConcurrentWriteableScope extends Scope.WriteableScope {
    
    private static final Predicate<Symbol> noFilter = _ -> true;
    
    HashMap<Name, LinkedList<Symbol>> symbolTables = { };
    
    LinkedList<Symbol> symbols = { };
    
    ReentrantReadWriteLock lock = { };
    
    @Default
    @Nullable ConcurrentWriteableScope next = null;
    
    protected Queue<Symbol> queue(final Name name) = symbolTables.computeIfAbsent(name, _ -> new LinkedList<>());
    
    @Override
    public void enter(final Symbol symbol) {
        lock.writeLock().lock();
        try {
            queue(symbol.name) += symbol;
            symbols >> symbol;
            (Privilege) listeners.symbolAdded(symbol, this);
        } finally { lock.writeLock().unlock(); }
    }
    
    @Override
    public void enterIfAbsent(final Symbol symbol) {
        lock.writeLock().lock();
        try {
            final Queue<Symbol> queue = queue(symbol.name);
            if (queue.stream().noneMatch(it -> it.kind == symbol.kind)) {
                queue += symbol;
                symbols >> symbol;
                (Privilege) listeners.symbolAdded(symbol, this);
            }
        } finally { lock.writeLock().unlock(); }
    }
    
    @Override
    public void remove(final Symbol symbol) {
        lock.writeLock().lock();
        try {
            queue(symbol.name) -= symbol;
            symbols -= symbol;
            (Privilege) listeners.symbolRemoved(symbol, this);
        } finally { lock.writeLock().unlock(); }
    }
    
    @Override
    public ConcurrentWriteableScope dup(final Symbol newOwner) = dupUnshared(newOwner);
    
    @Override
    public ConcurrentWriteableScope leave() = next;
    
    @Override
    public ConcurrentWriteableScope dupUnshared(final Symbol newOwner) = { newOwner, this };
    
    public Stream<Symbol> symbols(final @Nullable Predicate<Symbol> sf, final LookupKind lookupKind = LookupKind.RECURSIVE) = (switch (lookupKind) {
        case RECURSIVE     -> scopes().map(scope -> scope.symbols).flatMap(Collection::stream);
        case NON_RECURSIVE -> symbols.stream();
    }).filter(sf ?? noFilter);
    
    public Stream<Symbol> symbolsByName(final Name name, final @Nullable Predicate<Symbol> sf, final LookupKind lookupKind = LookupKind.RECURSIVE) = (switch (lookupKind) {
        case RECURSIVE     -> scopes().map(scope -> scope.symbolTables[name]).nonnull().flatMap(Collection::stream);
        case NON_RECURSIVE -> symbolTables[name]?.stream() ?? Stream.<Symbol>empty();
    }).filter(sf ?? noFilter);
    
    @Override
    public Iterable<Symbol> getSymbols(final Predicate<Symbol> sf, final LookupKind lookupKind) {
        lock.readLock().lock();
        try {
            return symbols(sf, lookupKind).toList();
        } finally { lock.readLock().unlock(); }
    }
    
    @Override
    public Iterable<Symbol> getSymbolsByName(final Name name, final Predicate<Symbol> sf, final LookupKind lookupKind) {
        lock.readLock().lock();
        try {
            return symbolsByName(name, sf, lookupKind).toList();
        } finally { lock.readLock().unlock(); }
    }
    
    @Override
    public @Nullable Symbol findFirst(final Name name, final Predicate<Symbol> sf) {
        lock.readLock().lock();
        try {
            return ~symbolsByName(name, sf);
        } finally { lock.readLock().unlock(); }
    }
    
    @Override
    public boolean anyMatch(final @Nullable Predicate<Symbol> sf) {
        lock.readLock().lock();
        try {
            return symbols(sf, LookupKind.NON_RECURSIVE).anyMatch(sf ?? noFilter);
        } finally { lock.readLock().unlock(); }
    }
    
    @Override
    public boolean includes(final Symbol sym, final LookupKind lookupKind) {
        lock.readLock().lock();
        try {
            return symbolsByName(sym.name, t -> t == sym, lookupKind).findFirst().isPresent();
        } finally { lock.readLock().unlock(); }
    }
    
    @Override
    public boolean isEmpty() {
        lock.readLock().lock();
        try {
            return symbols.isEmpty();
        } finally { lock.readLock().unlock(); }
    }
    
    @Override
    public @Nullable Scope getOrigin(final Symbol byName) = includes(byName) ? this : null;
    
    @Override
    public boolean isStaticallyImported(final Symbol byName) = false;
    
    @Override
    public String toString() {
        lock.readLock().lock();
        try {
            return scopes().map(scope -> scope.symbolTables.values().stream().flatMap(Collection::stream).map(Symbol::toString).collect(Collectors.joining(", "))).collect(Collectors.joining(" | ", "Scope[", "]"));
        } finally { lock.readLock().unlock(); }
    }
    
    public Stream<ConcurrentWriteableScope> scopes() = new LinkedIterator<>(scope -> scope.next, this).stream(true);
    
    @Hook(value = WriteableScope.class, isStatic = true)
    public static Hook.Result create(final Symbol owner) {
        if (JavacContext.instance()?.context ?? null instanceof MultiThreadedContext)
            return { new ConcurrentWriteableScope(owner) };
        return Hook.Result.VOID;
    }
    
}
