package amadeus.maho.vm.tools.hotspot;

import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import amadeus.maho.core.Maho;
import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.concurrent.ConcurrentWeakIdentityHashMap;
import amadeus.maho.util.concurrent.ConcurrentWeakIdentityHashSet;
import amadeus.maho.util.control.Interrupt;
import amadeus.maho.util.misc.Environment;
import amadeus.maho.util.runtime.ClassHelper;

import static java.lang.Boolean.TRUE;

public enum JIT {
    
    @Getter
    instance;
    
    public interface Compiler {
        
        WhiteBox WB = WhiteBox.instance();
        
        static boolean isC2OrJVMCIIncluded() = WB.isC2OrJVMCIIncluded();
        
        static boolean isJITEnabled() = WB.getBooleanVMFlag("UseCompiler") == TRUE;
        
        static boolean isJVMCIEnabled() = WB.getBooleanVMFlag("EnableJVMCI") == TRUE;
        
        static boolean isTieredCompilationEnabled() = WB.getBooleanVMFlag("TieredCompilation") == TRUE;
        
        static int tieredStopAtLevel() = Math.toIntExact(WB.getIntxVMFlag("TieredStopAtLevel"));
        
        static boolean isProfileInterpreterEnabled() = WB.getBooleanVMFlag("ProfileInterpreter") == TRUE;
        
        static boolean isGraalEnabled() = isJITEnabled() && WB.getBooleanVMFlag("UseJVMCICompiler") == TRUE && !(isTieredCompilationEnabled() && tieredStopAtLevel() <= 3);
        
        static boolean isJVMCINativeEnabled() = isGraalEnabled() && WB.getBooleanVMFlag("UseJVMCINativeLibrary") == TRUE;
        
        static boolean isC2Enabled() = isJITEnabled() && isProfileInterpreterEnabled() && (isTieredCompilationEnabled() || tieredStopAtLevel() > 3) && !isGraalEnabled();
        
        static boolean isC1Enabled() = isJITEnabled() && (!isProfileInterpreterEnabled() || isTieredCompilationEnabled());
        
    }
    
    public enum Level {
        NONE, SIMPLE, LIMITED_PROFILE, FULL_PROFILE, FULL_OPTIMIZATION
    }
    
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static final class Spy implements ClassFileTransformer {
        
        Consumer<Supplier<Class<?>>> consumer;
        
        @Override
        public @Nullable byte[] transform(final @Nullable ClassLoader loader, final @Nullable String className, final @Nullable Class<?> classBeingRedefined, final @Nullable ProtectionDomain domain, final @Nullable byte bytecode[]) {
            if (classBeingRedefined != null)
                consumer.accept(() -> classBeingRedefined);
            else if (className != null)
                consumer.accept(() -> ClassHelper.tryLoad(className.replace('/', '.'), false, loader));
            return null;
        }
        
    }
    
    @Getter
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static final class Scheduler {
        
        LinkedBlockingQueue<Supplier<Class<?>>> pending = { };
        
        ConcurrentWeakIdentityHashSet<Class<?>> memory = { };
        
        Spy spy = { pending()::offer };
        
        CopyOnWriteArrayList<Function<? super Class, Level>> functions = { };
        
        { Maho.instrumentation().addTransformer(spy(), true); }
        
        @SneakyThrows
        public void enqueue() = Interrupt.doInterruptible(() -> Stream.generate(pending::poll)
                .nonnull()
                .map(supplier -> !functions().isEmpty() ? supplier.get() : null)
                .nonnull()
                .forEach(target -> functions.stream().anyMatch(function -> {
                    final @Nullable Level level = function.apply(target);
                    if (level != null) {
                        instance().compile(target, level);
                        return true;
                    }
                    return false;
                })));
        
        public Map<Class<?>, Map<Method, Level>> measure() {
            final HashMap<Class<?>, Map<Method, Level>> result = { };
            memory.forEach(clazz -> Stream.of(clazz.getDeclaredMethods()).forEach(method -> result.computeIfAbsent(clazz, _ -> new HashMap<>())[method] = Level.values()[Compiler.WB.getMethodCompilationLevel(method)]));
            return result;
        }
        
        public List<Method> measure(final Predicate<Level> predicate) = measure().values().stream().map(Map::entrySet).flatMap(Collection::stream).filter(entry -> predicate.test(entry.getValue())).map(Map.Entry::getKey).toList();
        
        public List<Method> measure(final Level target) = measure(level -> level < target);
        
    }
    
    @Getter
    Scheduler scheduler = { };
    
    @Getter
    private static final Level defautLevel = Level.valueOf(Environment.local().lookup("amadeus.maho.jit.level.default", Level.FULL_OPTIMIZATION.name()));
    
    private volatile boolean isReady = false;
    
    private final ConcurrentWeakIdentityHashMap<Executable, Level> waitingQueue = { };
    
    public boolean compilable(final Executable executable) = !Modifier.isNative(executable.getModifiers());
    
    public void compile(final Executable executable, final Level level = defautLevel()) {
        if (compilable(executable)) {
            if (!isReady)
                synchronized (waitingQueue) {
                    if (!isReady) {
                        waitingQueue[executable] = level;
                        return;
                    }
                }
            if (Compiler.WB.getMethodCompilationLevel(executable) < level.ordinal() && Compiler.WB.isMethodCompilable(executable, level.ordinal()))
                if (!Compiler.WB.enqueueMethodForCompilation(executable, level.ordinal()) && level > Level.SIMPLE)
                    Compiler.WB.enqueueMethodForCompilation(executable, Level.SIMPLE.ordinal());
        }
    }
    
    public void compile(final Class<?> clazz, final Level level = defautLevel()) {
        try {
            for (final Method method : clazz.getDeclaredMethods())
                compile(method, level);
        } catch (final LinkageError ignored) { }
    }
    
    public void compileLoaded(final Predicate<? super Class> predicate, final Level level = defautLevel()) = Stream.of(Maho.instrumentation().getAllLoadedClasses()).filter(predicate).forEach(clazz -> compile(clazz, level));
    
    public void compileLoaded(final String prefix) = compileLoaded(clazz -> clazz.getName().startsWith(prefix));
    
    public void compileLoaded(final Package pkg) = compileLoaded(STR."\{pkg.getName()}.");
    
    public void compileAll(final Predicate<? super Class> predicate, final Level level = defautLevel()) {
        scheduler.functions() += target -> predicate.test(target) ? level : null;
        compileLoaded(predicate);
    }
    
    public void compileAll(final String prefix) = compileAll(clazz -> clazz.getName().startsWith(prefix));
    
    public void compileAll(final Package pkg) = compileAll(STR."\{pkg.getName()}.");
    
    public synchronized void ready() {
        synchronized (waitingQueue) {
            isReady = true;
            waitingQueue.forEach(this::compile);
            waitingQueue.clear();
        }
        Maho.debug("JITCompiler: Ready!");
    }
}
