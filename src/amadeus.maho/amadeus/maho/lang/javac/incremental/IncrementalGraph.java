package amadeus.maho.lang.javac.incremental;

import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.Todo;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Names;

import amadeus.maho.core.MahoExport;
import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.javac.JavacContext;
import amadeus.maho.lang.javac.multithreaded.dispatch.DispatchCompiler;
import amadeus.maho.lang.javac.multithreaded.dispatch.DispatchContext;
import amadeus.maho.lang.javac.multithreaded.parallel.ParallelCompiler;
import amadeus.maho.util.logging.LogLevel;
import amadeus.maho.util.runtime.DebugHelper;

import static amadeus.maho.util.concurrent.AsyncHelper.await;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class IncrementalGraph {
    
    DispatchContext context;
    
    IncrementalContext incrementalContext;
    
    DispatchCompiler compiler = DispatchCompiler.instance(context);
    
    Todo todo = Todo.instance(context);
    
    Names names = Names.instance(context);
    
    Symtab symtab = Symtab.instance(context);
    
    BiConsumer<LogLevel, String> log = MahoExport.namedLogger();
    
    Set<Symbol.ClassSymbol> recompile = ConcurrentHashMap.newKeySet();
    
    ConcurrentHashMap<Symbol.ModuleSymbol, Boolean> outdated = { };
    
    ConcurrentHashMap<Symbol.ClassSymbol, Boolean> processed = { };
    
    ConcurrentHashMap<Symbol.ClassSymbol, Set<Symbol.ClassSymbol>> dependencies = { };
    
    ConcurrentHashMap<DependencyItem.Class, Symbol.ClassSymbol> classCache = { };
    
    ConcurrentHashMap<DependencyItem.Member, Symbol> memberCache = { };
    
    ConcurrentHashMap<Symbol, String> signatureCache = { };
    
    public Set<Symbol.ClassSymbol> dependencies(final Symbol.ClassSymbol symbol) = dependencies.computeIfAbsent(symbol, _ -> ConcurrentHashMap.newKeySet());
    
    public @Nullable Symbol symbol(final JavacContext.SignatureGenerator signatureGenerator, final DependencyItem item) = switch (item) {
        case DependencyItem.Module moduleItem -> symtab.getModule(names.fromString(moduleItem.name()));
        case DependencyItem.Class classItem   -> classCache().computeIfAbsent(classItem, it -> lookupClass(signatureGenerator, it));
        case DependencyItem.Member memberItem -> memberCache().computeIfAbsent(memberItem, it -> lookupMember(signatureGenerator, (Symbol.ClassSymbol) symbol(signatureGenerator, it.owner()), it.name(), it.signature()));
    };
    
    public @Nullable Symbol.ClassSymbol lookupClass(final JavacContext.SignatureGenerator signatureGenerator, final DependencyItem.Class item)
        = symtab.getModule(names().fromString(item.module())) instanceof Symbol.ModuleSymbol moduleSymbol ? symtab().getClass(moduleSymbol, names().fromString(item.name())) : null;
    
    public @Nullable Symbol lookupMember(final JavacContext.SignatureGenerator signatureGenerator, final @Nullable Symbol.ClassSymbol owner, final String name, final String signature)
        = owner?.members().findFirst(names().fromString(name), it -> signature.equals(signature(signatureGenerator, it))) ?? null;
    
    public String signature(final JavacContext.SignatureGenerator signatureGenerator, final Symbol symbol) = signatureCache().computeIfAbsent(symbol, it -> signatureGenerator.signature(it.type));
    
    public Queue<Env<AttrContext>> mark() {
        if (!incrementalContext().compatible()) {
            log()[LogLevel.INFO] = "Incompatible version detected, clearing IncrementalContext";
            incrementalContext().clear();
            return todo();
        }
        markBy(this::markByTimestamp);
        markBy(this::markByDependency);
        processDependencies();
        return todo().stream().filter(env -> recompile().contains(env.enclClass.sym)).collect(ListBuffer::new, ListBuffer::append, ListBuffer::appendList);
    }
    
    public void markBy(final Predicate<Symbol.ClassSymbol> marker)
        = todo().stream().parallel().map(env -> env.enclClass.sym).filterNot(recompile()::contains).filter(marker).forEach(recompile()::add);
    
    public void markBy(final BiPredicate<ParallelCompiler, Symbol.ClassSymbol> marker)
        = await(compiler().dispatch(todo().stream().map(env -> env.enclClass.sym).filterNot(recompile()::contains), marker > (_, symbol) -> recompile() += symbol));
    
    public boolean markByTimestamp(final Symbol.ClassSymbol symbol) {
        final @Nullable Long recordTime = incrementalContext().timestamps()[symbol.sourcefile.getName()];
        return recordTime == null || symbol.sourcefile.getLastModified() > recordTime;
    }
    
    public boolean outdated(final Symbol.ModuleSymbol symbol) = outdated().computeIfAbsent(symbol, it -> {
        final @Nullable String recordVersion = incrementalContext().moduleVersions()[symbol.name.toString()];
        return recordVersion == null || !recordVersion.equals(IncrementalContext.moduleVersion(symbol));
    });
    
    public boolean markByDependency(final ParallelCompiler compiler, final Symbol.ClassSymbol symbol) {
        final @Nullable Set<DependencyItem> items = incrementalContext().dependencies()[incrementalContext().asClassDependencyItem(symbol)];
        if (items == null) {
            DebugHelper.breakpoint(new IllegalStateException("The IncrementalContext may be corrupted"));
            return true;
        }
        final JavacContext.SignatureGenerator signatureGenerator = JavacContext.SignatureGenerator.instance(compiler.context);
        return items.stream().anyMatch(it -> switch (symbol(signatureGenerator, it)) {
            case null                           -> recompileByMissing(symbol, it);
            case Symbol.ModuleSymbol dependency -> outdated(dependency) && recompileByChanged(symbol, dependency);
            case Symbol.ClassSymbol dependency  -> {
                if (recompile()[dependency])
                    yield recompileByChanged(symbol, dependency);
                else
                    dependencies(symbol) += dependency;
                yield false;
            }
            default                             -> false;
        });
    }
    
    public void processDependencies() = dependencies().keySet().stream().parallel().forEach(this::processDependenciesWithCache);
    
    public boolean processDependenciesWithCache(final Symbol.ClassSymbol symbol) {
        @Nullable Boolean processed = processed()[symbol];
        if (processed == null)
            processed()[symbol] = processed = processDependencies(symbol);
        return processed;
    }
    
    public boolean processDependencies(final Symbol.ClassSymbol symbol) {
        if (recompile()[symbol])
            return true;
        final @Nullable Set<Symbol.ClassSymbol> dependencies = dependencies()[symbol];
        if (dependencies == null)
            return false;
        for (final Symbol.ClassSymbol dependency : dependencies)
            if (processDependenciesWithCache(dependency)) {
                recompile() += symbol;
                return recompileByChanged(symbol, dependency);
            }
        return false;
    }
    
    public boolean recompileByChanged(final Symbol.ClassSymbol symbol, final Symbol dependency) {
        log()[LogLevel.DEBUG] = STR."Recompiling \{symbol} due to dependency changed: \{dependency}";
        return true;
    }
    
    public boolean recompileByMissing(final Symbol.ClassSymbol symbol, final DependencyItem dependencyItem) {
        log()[LogLevel.DEBUG] = STR."Recompiling \{symbol} due to missing dependency: \{dependencyItem}";
        return true;
    }
    
}
