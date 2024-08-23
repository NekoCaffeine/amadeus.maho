package amadeus.maho.core;

import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.module.ModuleDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.internal.module.Modules;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import amadeus.maho.agent.AgentInjector;
import amadeus.maho.agent.LiveInjector;
import amadeus.maho.core.bootstrap.HookResultInjector;
import amadeus.maho.core.bootstrap.Injector;
import amadeus.maho.core.bootstrap.ReflectionInjector;
import amadeus.maho.core.bootstrap.UnsafeInjector;
import amadeus.maho.core.extension.AllClassesPublic;
import amadeus.maho.core.extension.ModuleAdder;
import amadeus.maho.core.extension.ReflectBreaker;
import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.APIStatus;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.AOTTransformer;
import amadeus.maho.transform.TransformerManager;
import amadeus.maho.util.build.HotSwap;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.ClassWriter;
import amadeus.maho.util.dynamic.CallerContext;
import amadeus.maho.util.logging.LogLevel;
import amadeus.maho.util.profile.Sampler;
import amadeus.maho.util.resource.ResourcePath;
import amadeus.maho.util.runtime.DebugHelper;
import amadeus.maho.util.runtime.ModuleHelper;
import amadeus.maho.util.runtime.ObjectHelper;
import amadeus.maho.util.runtime.PropertiesHelper;
import amadeus.maho.util.runtime.UnsafeHelper;

import static amadeus.maho.core.MahoExport.*;

@NoArgsConstructor(AccessLevel.PRIVATE)
@APIStatus(design = APIStatus.Stage.γ, implement = APIStatus.Stage.γ)
public final class Maho {
    
    public static final String
            MODULE_NAME   = "amadeus.maho",
            SHARE_PACKAGE = "amadeus.maho.share";
    
    // use instrumentation()
    private static volatile @Nullable Instrumentation instrumentation;
    
    public static boolean initialized() = instrumentation != null;
    
    public static synchronized void instrumentation(final Instrumentation instrumentation) = Maho.instrumentation = instrumentation;
    
    public static Instrumentation instrumentation() = instrumentation ?? injectAgent();
    
    private static synchronized Instrumentation injectAgent() {
        if (instrumentation == null)
            try {
                final @Nullable String provider = System.getProperty("amadeus.maho.instrumentation.provider");
                if (provider != null)
                    try {
                        final Class<?> providerClass = Class.forName(provider, true, ClassLoader.getSystemClassLoader());
                        final Field fieldInstrumentation = providerClass.getDeclaredField("instrumentation");
                        return installation(null, (Instrumentation) fieldInstrumentation.get(null));
                    } catch (final Throwable throwable) {
                        System.err.println(STR."Unable to load instrumentation from instrumentation provider: \{provider}");
                        throwable.printStackTrace();
                        DebugHelper.breakpoint();
                    }
                AgentInjector.inject(Maho.class.getName(), LiveInjector.class);
                // Maho may not be loaded by SystemClassLoader
                final Class<LiveInjector> classLiveInjector = (Class<LiveInjector>) ClassLoader.getSystemClassLoader().loadClass(LiveInjector.class.getName());
                final Field fieldInstrumentation = classLiveInjector.getField("instrumentation");
                return installation(null, (Instrumentation) fieldInstrumentation.get(null));
            } catch (final Throwable e) { throw new InternalError("can't inject instrumentation instance", e); }
        // noinspection DataFlowIssue
        return instrumentation;
    }
    
    public static void premain(final String agentArgs, final Instrumentation instrumentation) = installation(agentArgs, instrumentation);
    
    public static void agentmain(final String agentArgs, final Instrumentation instrumentation) = installation(agentArgs, instrumentation);
    
    public static void log(final LogLevel level, final String message) = logger()?.log("Maho", level, message);
    
    public static void stage(final String message) = debug(STR."Stage -> \{message}");
    
    public static void info(final String message) = log(LogLevel.INFO, message);
    
    public static void debug(final String message) = log(LogLevel.DEBUG, message);
    
    public static void trace(final String message) = log(LogLevel.TRACE, message);
    
    public static void warn(final String message) = log(LogLevel.WARNING, message);
    
    public static void error(final String message) = log(LogLevel.ERROR, message);
    
    public static void fatal(final Throwable throwable) {
        fatalLogger().log("Maho", LogLevel.FATAL, throwable.dump());
        DebugHelper.breakpoint();
    }
    
    public static void checkInstrumentationSupport(final Instrumentation instrumentation) {
        if (!instrumentation.isRedefineClassesSupported())
            throw new UnsupportedOperationException("RedefineClasses");
        if (!instrumentation.isRetransformClassesSupported())
            throw new UnsupportedOperationException("RetransformClasses");
    }
    
    /*
        HotSpot VM, very rare( < 0.1% ), possibly related to C1(JIT)
        Fixed:
          java.lang.ClassCircularityError: java/lang/WeakPairMap$Pair$Weak
            at java.base/java.lang.WeakPairMap$Pair.weak(WeakPairMap.java:201)
            at java.base/java.lang.WeakPairMap.putIfAbsent(WeakPairMap.java:123)
            at java.base/java.lang.Module.implAddReads(Module.java:488)
            at java.base/java.lang.Module.implAddReads(Module.java:449)
            at java.base/java.lang.System$2.addReads(System.java:2335)
            at java.base/jdk.internal.module.Modules.addReads(Modules.java:90)
            ...
            Such as: loadJavaSupport -> jailbreak -> java.lang.instrument.Instrumentation#redefineModule
     */
    private static void workaroundClassCircularityError() = tryLoadSuperClass("java.lang.WeakPairMap$Pair$Weak$1");
    
    private static void tryLoadSuperClass(final String name, final @Nullable ClassLoader loader = Class.class.getClassLoader()) {
        try {
            final int indexOf = name.lastIndexOf('$');
            if (indexOf != -1)
                tryLoadSuperClass(name.substring(0, indexOf), loader);
            Class.forName(name, true, loader);
        } catch (final ClassNotFoundException ignored) { DebugHelper.breakpoint(); }
    }
    
    private static Module defineBootModule(final Module maho) {
        final ModuleDescriptor.Builder builder = ModuleDescriptor.newModule(STR."\{maho.getName()}.boot", Set.of(ModuleDescriptor.Modifier.OPEN, ModuleDescriptor.Modifier.SYNTHETIC));
        maho.getDescriptor().rawVersion().ifPresent(builder::version);
        return Modules.defineModule(null, builder.packages(maho.getPackages()).packages(Set.of(SHARE_PACKAGE)).build(), null);
    }
    
    @SneakyThrows
    private static void loadJavaSupport(final Instrumentation instrumentation) {
        checkInstrumentationSupport(instrumentation);
        workaroundClassCircularityError();
        if (System.getProperty("amadeus.maho.skip.acp") == null)
            instrumentation.addTransformer(AllClassesPublic.instance(), false);
        if (!MahoImage.isImage()) {
            inject(instrumentation, ReflectionInjector.instance());
            inject(instrumentation, UnsafeInjector.instance());
            inject(instrumentation, HookResultInjector.instance());
        }
        final Set<Module> extraReads = Set.of(Maho.class.getModule());
        jailbreak(instrumentation, ModuleLayer.boot().modules().stream(), extraReads); // TODO image
        final Module maho = Maho.class.getModule();
        if (maho.getClassLoader() != null)
            if (maho.isNamed()) { // Gives module attribution and privileges to the classes shared to the BootClassLoader
                final Module boot = defineBootModule(maho);
                jailbreak(instrumentation, Stream.concat(Stream.of(maho), ModuleLayer.boot().modules().stream()), Set.of(boot));
                jailbreak(instrumentation, Stream.of(boot), Set.of(maho));
                ModuleHelper.readAllBootModules(boot);
                ReflectBreaker.doBreak(maho, boot);
            } else {
                ModuleHelper.openAllBootModule();
                ReflectBreaker.doBreak(maho);
                ModuleAdder.injectMissingSystemModules();
            }
        stage("jailbreak");
    }
    
    public static void accessRequires(final Class<?> clazz = CallerContext.caller()) = accessRequires(clazz.getModule());
    
    public static void accessRequires(final Module module) {
        final @Nullable ModuleLayer layer = module.getLayer();
        if (layer != null)
            jailbreak(module.getDescriptor().requires().stream().map(ModuleDescriptor.Requires::name).map(layer::findModule).filter(Optional::isPresent).map(Optional::get), Set.of(module));
    }
    
    public static void jailbreak(final Instrumentation instrumentation = instrumentation(), final Stream<Module> modules, final Set<Module> extraReads) = modules.forEach(module -> {
        final Map<String, Set<Module>> extra = module.getPackages().stream().collect(Collectors.toMap(Function.identity(), _ -> extraReads));
        instrumentation.redefineModule(module, extraReads, extra, extra, Set.of(), Map.of());
    });
    
    @SneakyThrows
    public static void inject(final Instrumentation instrumentation = instrumentation(), final Injector injector) {
        instrumentation.addTransformer(injector, true);
        try {
            instrumentation.retransformClasses(Class.forName(injector.target()));
        } catch (final VerifyError | InternalError error) { throw DebugHelper.breakpointBeforeThrow(error); }
    }
    
    public static final String FIELD_BRIDGE_CLASS_LOADER = "bridgeClassLoader";
    
    @SneakyThrows
    private static void loadClassLoaderBridge() {
        if (Maho.class.getClassLoader() == null)
            return;
        stage("loadClassLoaderBridge");
        shareClass(MahoBridge.class);
        final Class<?> bridge = Class.forName(MahoBridge.class.getName(), true, null);
        final Field target = bridge.getDeclaredField(FIELD_BRIDGE_CLASS_LOADER);
        target.setAccessible(true);
        target.set(null, Maho.class.getClassLoader());
    }
    
    public static final String
            MAHO_PACKAGE_NAME        = Maho.class.getPackage().getName().replaceFirst("(?<upperPackageName>.*\\.).*", "$1"),
            MAHO_SHADOW_PACKAGE_NAME = STR."\{MAHO_PACKAGE_NAME}shadow.";
    
    @SneakyThrows
    private static void setupTransformer() {
        stage("setupTransformer");
        final Predicate<ResourcePath.ClassInfo> filter = info -> info.className().startsWith(MAHO_PACKAGE_NAME) && !info.className().startsWith(MAHO_SHADOW_PACKAGE_NAME);
        final @Nullable Predicate<ResourcePath.ClassInfo> exportFilter = Setup.setupFilter();
        setupFromClass(Maho.class, exportFilter == null ? filter : filter.and(exportFilter));
    }
    
    @SneakyThrows
    public static Path jar() throws URISyntaxException = ResourcePath.classMapperChain().apply(Maho.class).map(URL::toURI).map(Path::of).orElseThrow();
    
    @SneakyThrows
    public static void dump(final List<String> list, final String subHead) {
        list += STR."PID: \{ProcessHandle.current().pid()}";
        list += STR."VersionInfo: \{VERSION}";
        list += STR."MahoImage: \{MahoImage.isImage()}";
        list += STR."Experimental: \{experimental()}";
        list += STR."Debug: \{MahoExport.debug()}";
        list += STR."JVM: \{System.getProperty("java.vm.name")}";
        list += STR."RuntimeVersion: \{Runtime.version()}";
        list += STR."JavaHome: \{Path.of(System.getProperty("java.home")).toRealPath()}";
        list += STR."Location: \{Path.of("").toRealPath()}";
        list += STR."ClassLoader: \{Maho.class.getClassLoader()}";
        list += STR."Instrumentation: \{instrumentation()}";
    }
    
    @SneakyThrows
    public static Instrumentation installation(final @Nullable String agentArgs = null, final Instrumentation instrumentation) {
        PropertiesHelper.Overrider.overrideByMahoHome();
        instrumentation(ObjectHelper.requireNonNull(instrumentation));
        new ArrayList<String>().let(result -> dump(result, " ".repeat(4))).forEach(Maho::debug);
        loadJavaSupport(instrumentation);
        loadClassLoaderBridge();
        setupTransformer();
        if (hotswap())
            HotSwap.watch();
        return instrumentation;
    }
    
    public static void setupFromClass(final Class<?> clazz = CallerContext.caller(), final Predicate<ResourcePath.ClassInfo> filter = _ -> true) {
        instrumentation();
        try (final var path = ResourcePath.of(clazz)) {
            final Module module = clazz.getModule();
            if (module != Maho.class.getModule())
                accessRequires(module);
            TransformerManager.runtime().setup(clazz.getClassLoader(), path, AOTTransformer.Level.RUNTIME, module.isNamed() ? STR."\{module.getDescriptor().toNameAndVersion()}#\{clazz.getSimpleName()}" : clazz.getSimpleName(), filter);
        }
    }
    
    public static void setupFromCallerSelf() {
        final Class<?> caller = CallerContext.caller();
        setupFromClass(caller, info -> info.className().equals(caller.getName()));
    }
    
    public static Stream<Class> findLoadedClassByName(final String name) = Stream.of(instrumentation().getAllLoadedClasses()).filter(target -> target.getName().equals(name));
    
    public static Class<?> shareClass(final Class<?> clazz, final @Nullable ClassLoader loader = null) = shareClass(clazz.getName(), getBytecodeFromClass(clazz), loader);
    
    public static Class<?> shareClass(final ClassNode node, final @Nullable ClassLoader loader = null) = shareClass(ASMHelper.sourceName(node.name), ClassWriter.toBytecode(node::accept), loader);
    
    // loader => null when the ClassLoader is BootClassLoader
    public static Class<?> shareClass(final String name, final byte bytecode[], final @Nullable ClassLoader loader = null) {
        try {
            TransformerManager.DebugDumper.dumpBytecode(name.replace('.', '/'), bytecode, TransformerManager.DebugDumper.dump_transform_share);
            return UnsafeHelper.unsafe().defineClass(name, bytecode, 0, bytecode.length, loader, null);
        } catch (final LinkageError linkageError) {
            try {
                return Class.forName(name, false, loader);
            } catch (final Throwable notFoundEx) {
                return Stream.of(instrumentation().getAllLoadedClasses())
                        .filter(target -> target.getClassLoader() == loader)
                        .filter(target -> target.getName().equals(name))
                        .findFirst()
                        .orElseThrow(() -> {
                            linkageError.addSuppressed(notFoundEx);
                            return linkageError;
                        });
            }
        }
    }
    
    public static @Nullable MethodNode getMethodNodeFromMethod(final Method method) = getMethodNodeFromClass(method.getDeclaringClass(), method.getName(), Type.getMethodDescriptor(method));
    
    public static MethodNode getMethodNodeFromMethodNonNull(final Method method) = getMethodNodeFromClassNonNull(method.getDeclaringClass(), method.getName(), Type.getMethodDescriptor(method));
    
    public static @Nullable MethodNode getMethodNodeFromClass(final Class<?> target, final String name, final String desc, final boolean mustRetransform = false) {
        final @Nullable byte bytecode[] = getBytecodeFromClass(target, mustRetransform);
        return bytecode == null ? null : ~Stream.ofNullable(ASMHelper.newClassNode(bytecode))
                .map(node -> node.methods)
                .flatMap(List::stream)
                .filter(targetMethod -> targetMethod.name.equals(name) && targetMethod.desc.equals(desc));
    }
    
    public static MethodNode getMethodNodeFromClassNonNull(final Class<?> target, final String name, final String desc, final boolean mustRetransform = false)
            = Optional.ofNullable(getMethodNodeFromClass(target, name, desc, mustRetransform))
            .orElseThrow(() -> DebugHelper.breakpointBeforeThrow(new UnsupportedOperationException(STR."Unable to get MethodNode form: \{target}#\{name}\{desc}")));
    
    public static @Nullable ClassNode getClassNodeFromClass(final Class<?> target, final boolean mustRetransform = false) {
        final @Nullable byte bytecode[] = getBytecodeFromClass(target, mustRetransform);
        return bytecode != null ? ASMHelper.newClassNode(bytecode) : null;
    }
    
    public static ClassNode getClassNodeFromClassNonNull(final Class<?> target, final boolean mustRetransform = false)
            = Optional.ofNullable(getClassNodeFromClass(target, mustRetransform))
            .orElseThrow(() -> DebugHelper.breakpointBeforeThrow(new UnsupportedOperationException(STR."Unable to get ClassNode form: \{target}")));
    
    private static final Sampler<String> sampler = MahoProfile.sampler("GetBytecode");
    
    @SneakyThrows
    public static @Nullable byte[] getBytecodeFromClass(final Class<?> target, final boolean mustRetransform = false) {
        if (!mustRetransform) {
            final @Nullable InputStream stream = (target.getClassLoader() ?? ClassLoader.getPlatformClassLoader()).getResourceAsStream(STR."\{target.getName().replace('.', '/')}.class");
            if (stream != null)
                return stream.readAllBytes();
        }
        final Instrumentation instrumentation = instrumentation();
        if (!instrumentation.isModifiableClass(target))
            return null;
        try (final var _ = sampler[target.getName()]) {
            final GetBytecode transformer = { target };
            instrumentation.addTransformer(transformer, true);
            try { instrumentation.retransformClasses(target); } catch (final UnmodifiableClassException e) { return null; } finally { instrumentation.removeTransformer(transformer); }
            return transformer.bytecode;
        }
    }
    
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    private static class GetBytecode implements ClassFileTransformer {
        
        final Class<?> target;
        
        @Nullable
        byte bytecode[];
        
        @Override
        public @Nullable byte[] transform(final @Nullable ClassLoader loader, final @Nullable String name, final @Nullable Class<?> clazz, final @Nullable ProtectionDomain domain, final @Nullable byte bytecode[]) {
            if (clazz == target)
                this.bytecode = bytecode;
            return null;
        }
        
    }
    
}
