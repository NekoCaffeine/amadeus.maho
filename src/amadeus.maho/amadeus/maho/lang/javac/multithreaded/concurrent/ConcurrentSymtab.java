package amadeus.maho.lang.javac.multithreaded.concurrent;

import java.lang.invoke.VarHandle;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Convert;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.javac.JavacContext;
import amadeus.maho.util.concurrent.ConcurrentWeakIdentityHashMap;
import amadeus.maho.util.dynamic.LookupHelper;
import amadeus.maho.util.function.FunctionHelper;

import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Kinds.Kind.*;

@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConcurrentSymtab extends Symtab {
    
    @FieldDefaults(level = AccessLevel.PUBLIC)
    public static class ClassLoading {
        
        public static ClassLoading instance(final Context context) = context.get(ClassLoading.class) ?? new ClassLoading(context);
        
        public ClassLoading(final Context context) = context.put(ClassLoading.class, this);
        
        @Nullable Symbol.ModuleSymbol moduleSymbol;
        
        @Nullable Name flatName;
        
        @Nullable Symbol.ClassSymbol loadingClassSymbol;
        
    }
    
    ConcurrentHashMap<Name, ConcurrentHashMap<Symbol.ModuleSymbol, Symbol.ClassSymbol>> classes = concurrentValueMap((Privilege) ((Symtab) this).classes);
    
    ConcurrentHashMap<Name, ConcurrentHashMap<Symbol.ModuleSymbol, Symbol.PackageSymbol>> packages = concurrentValueMap((Privilege) ((Symtab) this).packages);
    
    ConcurrentHashMap<Name, Symbol.ModuleSymbol> modules = { (Privilege) ((Symtab) this).modules };
    
    ConcurrentHashMap<Types.UniqueType, Symbol.VarSymbol> classFields = { (Privilege) ((Symtab) this).classFields };
    
    ConcurrentWeakIdentityHashMap<Name, ConcurrentWeakIdentityHashMap<Symbol.ModuleSymbol, Object>> tryLoadingMap = { };
    
    protected static <R, C, V> ConcurrentHashMap<R, ConcurrentHashMap<C, V>> concurrentValueMap(final Map<R, Map<C, V>> map) = map.entrySet().stream()
            .map(entry -> Map.entry(entry.getKey(), new ConcurrentHashMap<>(entry.getValue())))
            .collect(Collectors.toConcurrentMap(Map.Entry::getKey, Map.Entry::getValue, FunctionHelper.first(), ConcurrentHashMap::new));
    
    { ConcurrentHelper.overrideSuperFields(this); }
    
    public Map<Name, Map<Symbol.ModuleSymbol, Symbol.ClassSymbol>> classes() = (Map<Name, Map<Symbol.ModuleSymbol, Symbol.ClassSymbol>>) (classes ?? (Privilege) ((Symtab) this).classes);
    
    public Map<Name, Map<Symbol.ModuleSymbol, Symbol.PackageSymbol>> packages() = (Map<Name, Map<Symbol.ModuleSymbol, Symbol.PackageSymbol>>) (packages ?? (Privilege) ((Symtab) this).packages);
    
    public Map<Name, Symbol.ModuleSymbol> modules() = modules ?? (Privilege) ((Symtab) this).modules;
    
    public Map<Types.UniqueType, Symbol.VarSymbol> classFields() = classFields ?? (Privilege) ((Symtab) this).classFields;
    
    @Override
    public @Nullable Symbol.ClassSymbol getClass(final Symbol.ModuleSymbol moduleSymbol, final Name flatname) {
        final ClassLoading loading = ClassLoading.instance(JavacContext.instance().context);
        if (loading.moduleSymbol == moduleSymbol && loading.flatName == flatname)
            return loading.loadingClassSymbol;
        return classes()[flatname]?.get(moduleSymbol) ?? null;
    }
    
    @Override
    public void removeClass(final Symbol.ModuleSymbol moduleSymbol, final Name flatname) = classes()[flatname]?.remove(moduleSymbol);
    
    @Override
    public Iterable<Symbol.ClassSymbol> getAllClasses() = classes().entrySet().stream().flatMap(entry -> entry.getValue().values().stream()).collect(List.collector());
    
    @Override
    public Iterable<Symbol.ClassSymbol> getClassesForName(final Name candidate) = classes()[candidate]?.values() ?? List.<Symbol.ClassSymbol>nil();
    
    protected Map<Symbol.ModuleSymbol, Symbol.ClassSymbol> modules2classes(final Name flatname) = classes().computeIfAbsent(flatname, _ -> new ConcurrentHashMap<>());
    
    @Override
    public Symbol.ClassSymbol enterClass(final Symbol.ModuleSymbol moduleSymbol, final Name flatname) {
        final Symbol.PackageSymbol packageSymbol = lookupPackage(moduleSymbol, Convert.packagePart(flatname));
        final Map<Symbol.ModuleSymbol, Symbol.ClassSymbol> map = modules2classes(flatname);
        final @Nullable Symbol.ClassSymbol classSymbol = map[packageSymbol.modle];
        if (classSymbol != null)
            return classSymbol;
        final @Nullable JavacContext instance = JavacContext.instance();
        if (instance != null) { // null when invoke <init>
            final ClassLoading loading = ClassLoading.instance(instance.context);
            if (loading.moduleSymbol == moduleSymbol && loading.flatName == flatname)
                return loading.loadingClassSymbol;
        }
        return map.computeIfAbsent(packageSymbol.modle, _ -> defineClass(Convert.shortName(flatname), packageSymbol));
    }
    
    @Override
    public Symbol.ClassSymbol enterClass(final Symbol.ModuleSymbol moduleSymbol, final Name name, final Symbol.TypeSymbol owner) {
        final Name flatname = Symbol.TypeSymbol.formFlatName(name, owner);
        return modules2classes(flatname).compute(moduleSymbol, (m, c) -> {
            if (c == null)
                return defineClass(name, owner);
            if ((c.name != name || c.owner != owner) && owner.kind == TYP && c.owner.kind == PCK && (c.flags_field & FROM_SOURCE) == 0) {
                c.owner.members().remove(c);
                c.name = name;
                c.owner = owner;
                c.fullname = Symbol.ClassSymbol.formFullName(name, owner);
            }
            return c;
        });
    }
    
    @Override
    public @Nullable Symbol.PackageSymbol getPackage(final Symbol.ModuleSymbol moduleSymbol, final Name fullname) = packages()[fullname]?.get(moduleSymbol) ?? null;
    
    @Override
    public @Nullable Symbol.ModuleSymbol inferModule(final Name packageName) {
        if (packageName.isEmpty())
            return java_base == noModule ? noModule : unnamedModule;
        final @Nullable Map<Symbol.ModuleSymbol, Symbol.PackageSymbol> map = packages()[packageName];
        if (map == null)
            return null;
        @Nullable Symbol.ModuleSymbol moduleSymbol = null;
        for (final Map.Entry<Symbol.ModuleSymbol, Symbol.PackageSymbol> e : map.entrySet())
            if (!e.getValue().members().isEmpty())
                if (moduleSymbol == null)
                    moduleSymbol = e.getKey();
                else
                    return null;
        return moduleSymbol;
    }
    
    @Override
    public List<Symbol.ModuleSymbol> listPackageModules(final Name packageName) {
        if (packageName.isEmpty())
            return List.nil();
        List<Symbol.ModuleSymbol> result = List.nil();
        final @Nullable Map<Symbol.ModuleSymbol, Symbol.PackageSymbol> map = packages()[packageName];
        if (map != null)
            for (final Map.Entry<Symbol.ModuleSymbol, Symbol.PackageSymbol> e : map.entrySet())
                if (!e.getValue().members().isEmpty())
                    result = result.prepend(e.getKey());
        return result;
    }
    
    @Override
    public Iterable<Symbol.PackageSymbol> getPackagesForName(final Name candidate) = packages()[candidate]?.values() ?? List.<Symbol.PackageSymbol>nil();
    
    protected static final VarHandle enclosedPackages = LookupHelper.<Symbol.ModuleSymbol>varHandle(moduleSymbol -> moduleSymbol.enclosedPackages);
    
    private static void prependPackages(final Symbol.ModuleSymbol moduleSymbol, final Symbol.PackageSymbol packageSymbol) {
        while (true) {
            final List<Symbol> symbols = moduleSymbol.enclosedPackages;
            if (enclosedPackages.compareAndSet(moduleSymbol, symbols, symbols.prepend(packageSymbol)))
                break;
        }
    }
    
    protected synchronized void addRootPackageFor(final Symbol.ModuleSymbol moduleSymbol) {
        packages().computeIfAbsent(rootPackage.fullname, _ -> new ConcurrentHashMap<>()).computeIfAbsent(moduleSymbol, m -> {
            prependPackages(moduleSymbol, rootPackage);
            return rootPackage;
        });
        final Symbol.PackageSymbol unnamedPackage = new Symbol.PackageSymbol(((Privilege) names).empty, rootPackage) {
            @Override
            public String toString() = ((Privilege) messages).getLocalizedString("compiler.misc.unnamed.package");
        };
        unnamedPackage.modle = moduleSymbol;
        unnamedPackage.completer = s -> ((Privilege) initialCompleter).complete(s);
        unnamedPackage.flags_field |= EXISTS;
        moduleSymbol.unnamedPackage = unnamedPackage;
    }
    
    @Override
    public Symbol.PackageSymbol enterPackage(final Symbol.ModuleSymbol moduleSymbol, final Name fullname) {
        final Map<Symbol.ModuleSymbol, Symbol.PackageSymbol> map = packages().computeIfAbsent(fullname, _ -> new ConcurrentHashMap<>());
        final @Nullable Symbol.PackageSymbol definedPackageSymbol = map[moduleSymbol];
        if (definedPackageSymbol != null)
            return definedPackageSymbol;
        Assert.check(!fullname.isEmpty(), () -> STR."rootPackage missing!; currModule: \{moduleSymbol}");
        final Symbol.PackageSymbol owner = enterPackage(moduleSymbol, Convert.packagePart(fullname));
        return map.computeIfAbsent(moduleSymbol, m -> {
            final Symbol.PackageSymbol packageSymbol = { Convert.shortName(fullname), owner };
            packageSymbol.completer = (Privilege) this.initialCompleter;
            packageSymbol.modle = m;
            prependPackages(m, packageSymbol);
            return packageSymbol;
        });
    }
    
    @Override
    public Symbol.ModuleSymbol enterModule(final Name name) = modules().computeIfAbsent(name, n -> {
        final Symbol.ModuleSymbol moduleSymbol = Symbol.ModuleSymbol.create(name, ((Privilege) names).module_info);
        addRootPackageFor(moduleSymbol);
        moduleSymbol.completer = s -> ((Privilege) this.moduleCompleter).complete(s);
        return moduleSymbol;
    });
    
    @Override
    public Symbol.ModuleSymbol getModule(final Name name) = modules()[name];
    
    @Override
    public Collection<Symbol.ModuleSymbol> getAllModules() = modules().values();
    
    public Symbol.ClassSymbol tryLoadClass(final Symbol.ModuleSymbol moduleSymbol, final Name flatname) throws Symbol.CompletionFailure {
        final Symbol.PackageSymbol packageSymbol = lookupPackage(moduleSymbol, Convert.packagePart(flatname));
        final @Nullable Symbol.ClassSymbol definedClassSymbol = getClass(packageSymbol.modle, flatname);
        if (definedClassSymbol != null) {
            if (definedClassSymbol.members_field == null) {
                definedClassSymbol.complete();
                if ((definedClassSymbol.flags_field & UNNAMED_CLASS) != 0)
                    removeClass(packageSymbol.modle, flatname);
            }
            return definedClassSymbol;
        } else {
            final ConcurrentWeakIdentityHashMap<Symbol.ModuleSymbol, Object> map = tryLoadingMap.computeIfAbsent(flatname, _ -> new ConcurrentWeakIdentityHashMap<>());
            switch (map.computeIfAbsent(moduleSymbol, _ -> new Object())) {
                case Symbol.CompletionFailure failure -> throw failure;
                case Object lock                      -> {
                    synchronized (lock) {
                        final @Nullable Symbol.ClassSymbol retry = getClass(packageSymbol.modle, flatname);
                        if (retry != null)
                            return retry;
                        final Symbol.ClassSymbol loadingClassSymbol = defineClass(Convert.shortName(flatname), packageSymbol);
                        final ClassLoading loading = ClassLoading.instance(JavacContext.instance().context);
                        loading.moduleSymbol = moduleSymbol;
                        loading.flatName = flatname;
                        loading.loadingClassSymbol = loadingClassSymbol;
                        try {
                            loadingClassSymbol.complete();
                            if ((loadingClassSymbol.flags_field & UNNAMED_CLASS) == 0)
                                modules2classes(flatname)[moduleSymbol] = loadingClassSymbol;
                            return loadingClassSymbol;
                        } catch (final Symbol.CompletionFailure failure) {
                            map[moduleSymbol] = failure;
                            failure.dcfh.classSymbolRemoved(loadingClassSymbol);
                            throw failure;
                        } finally {
                            loading.moduleSymbol = null;
                            loading.flatName = null;
                            loading.loadingClassSymbol = null;
                        }
                    }
                }
            }
        }
    }
    
}