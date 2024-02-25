package amadeus.maho.lang.javac.multithreaded.dispatch;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import javax.tools.JavaFileManager;

import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.comp.Check;
import com.sun.tools.javac.comp.CompileStates;
import com.sun.tools.javac.comp.Modules;
import com.sun.tools.javac.comp.TypeEnvs;
import com.sun.tools.javac.jvm.Target;
import com.sun.tools.javac.main.Arguments;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Options;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.javac.multithreaded.MultiThreadedContext;
import amadeus.maho.lang.javac.multithreaded.SharedComponent;
import amadeus.maho.lang.javac.multithreaded.parallel.ParallelContext;
import amadeus.maho.lang.javac.multithreaded.concurrent.ConcurrentCheck;
import amadeus.maho.lang.javac.multithreaded.concurrent.ConcurrentCompileStates;
import amadeus.maho.lang.javac.multithreaded.concurrent.ConcurrentNames;
import amadeus.maho.lang.javac.multithreaded.concurrent.ConcurrentSymtab;
import amadeus.maho.lang.javac.multithreaded.concurrent.ConcurrentTypeEnvs;

import static amadeus.maho.util.concurrent.AsyncHelper.await;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DispatchContext extends Context implements MultiThreadedContext {
    
    @Default
    int parallelism = Runtime.getRuntime().availableProcessors();
    
    AtomicInteger workerIndex = { 0 };
    
    @Getter(lazy = true)
    List<ParallelContext> parallelContexts = initParallelContexts();
    
    ConcurrentHashMap<Key<?>, Boolean> sharedKeys = { };
    
    ConcurrentHashMap<Class<?>, Key<?>> class2Key = { };
    
    ConcurrentHashMap<Key<?>, Class<?>> key2Class = { };
    
    @Override
    public <T> Key<T> key(final Class<T> clazz) = (Key<T>) class2Key.computeIfAbsent(clazz, c -> {
        final Key<?> identity = { };
        key2Class[identity] = c;
        return identity;
    });
    
    @Override
    public synchronized <T> void put(final Key<T> key, final T data) = super.put(key, data);
    
    @Override
    public synchronized <T> void put(final Key<T> key, final Factory<T> fac) = super.put(key, fac);
    
    @Override
    public synchronized <T> T get(final Key<T> key) = super.get(key);
    
    { initSharedKeys(); }
    
    {
        put(Names.namesKey, new ConcurrentNames(this));
        put((Privilege) TypeEnvs.typeEnvsKey, new ConcurrentTypeEnvs(this));
        put((Privilege) CompileStates.compileStatesKey, new ConcurrentCompileStates(this));
        put((Privilege) Symtab.symtabKey, (Factory<Symtab>) ConcurrentSymtab::new);
    }
    
    { put((Privilege) Check.checkKey, (Factory<Check>) ConcurrentCheck::new); }
    
    protected void initSharedKeys() = List.of(
            DispatchCompiler.dispatchCompilerKey,
            Log.errKey,
            key(JavaFileManager.class),
            Names.namesKey,
            (Privilege) Target.targetKey,
            (Privilege) Symtab.symtabKey,
            key(Modules.class),
            (Privilege) TypeEnvs.typeEnvsKey,
            (Privilege) CompileStates.compileStatesKey,
            Options.optionsKey,
            Arguments.argsKey
    ).forEach(key -> sharedKeys()[key] = true);
    
    public boolean isolated(final Key<?> key) = !sharedKeys().computeIfAbsent(key, it -> {
        final @Nullable Class<?> clazz = key2Class[it];
        return clazz != null && SharedComponent.class.isAssignableFrom(clazz);
    });
    
    public ParallelContext newParallelContext() = { this };
    
    public List<ParallelContext> initParallelContexts() {
        final List<ParallelContext> contexts = IntStream.range(0, parallelism).mapToObj(_ -> newParallelContext()).toList();
        await(contexts.stream().map(context -> context.initialization));
        return contexts;
    }
    
}
