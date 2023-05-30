package amadeus.maho.core;

import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.module.ModuleDescriptor;
import java.lang.reflect.Field;
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
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.APIStatus;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.AOTTransformer;
import amadeus.maho.transform.TransformerManager;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.ClassWriter;
import amadeus.maho.util.dynamic.CallerContext;
import amadeus.maho.util.logging.LogLevel;
import amadeus.maho.util.profile.Sampler;
import amadeus.maho.util.resource.ResourcePath;
import amadeus.maho.util.runtime.DebugHelper;
import amadeus.maho.util.runtime.ModuleHelper;
import amadeus.maho.util.runtime.ObjectHelper;
import amadeus.maho.util.runtime.UnsafeHelper;

import static amadeus.maho.core.MahoExport.*;

@NoArgsConstructor(AccessLevel.PRIVATE)
@APIStatus(design = APIStatus.Stage.γ, implement = APIStatus.Stage.γ)
public final class Maho {
    
    public static final String SHARE_PACKAGE = "amadeus.maho.share";
    
    // use instrumentation()
    @Setter
    private static volatile @Nullable Instrumentation instrumentation;
    
    public static boolean initialized() = instrumentation != null;
    
    public static Instrumentation instrumentation() = instrumentation == null ? injectAgent() : instrumentation;
    
    private static synchronized @Nullable Instrumentation injectAgent() {
        if (instrumentation == null)
            try {
                final @Nullable String provider = System.getProperty("amadeus.maho.instrumentation.provider");
                if (provider != null)
                    try {
                        final Class<?> providerClass = Class.forName(provider, true, ClassLoader.getSystemClassLoader());
                        final Field fieldInstrumentation = providerClass.getDeclaredField("instrumentation");
                        installation(null, (Instrumentation) fieldInstrumentation.get(null));
                        return instrumentation;
                    } catch (final Throwable throwable) {
                        System.err.println("Unable to load instrumentation from instrumentation provider: " + provider);
                        throwable.printStackTrace();
                        DebugHelper.breakpoint();
                    }
                AgentInjector.inject(Maho.class.getName(), LiveInjector.class);
                // Maho may not be loaded by SystemClassLoader
                final Class<LiveInjector> classLiveInjector = (Class<LiveInjector>) ClassLoader.getSystemClassLoader().loadClass(LiveInjector.class.getName());
                final Field fieldInstrumentation = classLiveInjector.getField("instrumentation");
                installation(null, (Instrumentation) fieldInstrumentation.get(null));
            } catch (final Exception e) { throw new InternalError("can't inject instrumentation instance", e); }
        return instrumentation;
    }
    
    public static void premain(final String agentArgs, final Instrumentation instrumentation) = installation(agentArgs, instrumentation);
    
    public static void agentmain(final String agentArgs, final Instrumentation instrumentation) = installation(agentArgs, instrumentation);
    
    public static void log(final LogLevel level, final String message) = logger()?.log("Maho", level, message);
    
    public static void stage(final String message) = debug("Stage -> " + message);
    
    public static void info(final String message) = log(LogLevel.INFO, message);
    
    public static void debug(final String message) = log(LogLevel.DEBUG, message);
    
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
    
    @SneakyThrows
    private static void loadJavaSupport(final Instrumentation instrumentation) {
        checkInstrumentationSupport(instrumentation);
        instrumentation.addTransformer(AllClassesPublic.instance(), false);
        inject(instrumentation, ReflectionInjector.instance());
        inject(instrumentation, UnsafeInjector.instance());
        inject(instrumentation, HookResultInjector.instance());
        final Set<Module> extraReads = Set.of(Maho.class.getModule());
        jailbreak(instrumentation, ModuleLayer.boot().modules().stream(), extraReads);
        final Module maho = Maho.class.getModule();
        if (maho.getClassLoader() != null && maho.isNamed()) { // Gives module attribution and privileges to the classes shared to the BootClassLoader
            final ModuleDescriptor.Builder builder = ModuleDescriptor.newModule(maho.getName() + ".boot", Set.of(ModuleDescriptor.Modifier.OPEN, ModuleDescriptor.Modifier.SYNTHETIC));
            maho.getDescriptor().rawVersion().ifPresent(builder::version);
            final Module boot = Modules.defineModule(null, builder.packages(maho.getPackages()).packages(Set.of(SHARE_PACKAGE)).build(), null);
            jailbreak(instrumentation, Stream.concat(Stream.of(maho), ModuleLayer.boot().modules().stream()), Set.of(boot));
            jailbreak(instrumentation, Stream.of(boot), Set.of(maho));
            ModuleHelper.readAllBootModules(boot);
            ReflectBreaker.doBreak(maho, boot);
        } else {
            ReflectBreaker.doBreak(maho);
            ModuleAdder.injectMissingSystemModules();
        }
        stage("jailbreak");
    }
    
    public static void accessRequires(final Class<?> clazz = CallerContext.caller()) = accessRequires(clazz.getModule());
    
    public static void accessRequires(final Module module) {
        final @Nullable ModuleLayer layer = module.getLayer();
        if (layer != null)
            jailbreak(instrumentation(), module.getDescriptor().requires().stream().map(ModuleDescriptor.Requires::name).map(layer::findModule).filter(Optional::isPresent).map(Optional::get), Set.of(module));
    }
    
    private static void jailbreak(final Instrumentation instrumentation, final Stream<Module> modules, final Set<Module> extraReads) = modules.forEach(module -> {
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
            MAHO_SHADOW_PACKAGE_NAME = MAHO_PACKAGE_NAME + "shadow.";
    
    @SneakyThrows
    private static void setupTransformer() {
        stage("setupTransformer");
        final Predicate<ResourcePath.ClassInfo> filter = info -> info.className().startsWith(MAHO_PACKAGE_NAME) && !info.className().startsWith(MAHO_SHADOW_PACKAGE_NAME), exportFilter = Setup.setupFilter();
        setupFromClass(Maho.class, exportFilter == null ? filter : filter.and(exportFilter));
    }
    
    @SneakyThrows
    public static Path jar() throws URISyntaxException = ResourcePath.classMapperChain().apply(Maho.class).map(URL::toURI).map(Path::of).orElseThrow();
    
    @SneakyThrows
    public static void dump(final List<String> list, final String subHead) {
        list += "PID: " + ProcessHandle.current().pid();
        list += "VersionInfo: " + VERSION;
        list += "MahoImage: " + MahoImage.isImage();
        list += "Experimental: " + experimental();
        list += "Debug: " + MahoExport.debug();
        list += "JVM: " + System.getProperty("java.vm.name");
        list += "RuntimeVersion: " + Runtime.version();
        list += "JavaHome: " + Path.of(System.getProperty("java.home")).toRealPath();
        list += "Location: " + Path.of("").toRealPath();
        list += "ClassLoader: " + Maho.class.getClassLoader();
        list += "Instrumentation: " + instrumentation();
    }
    
    @SneakyThrows
    public static void installation(final @Nullable String agentArgs = null, final Instrumentation instrumentation) {
        instrumentation(ObjectHelper.requireNonNull(instrumentation));
        new ArrayList<String>().let(result -> dump(result, " ".repeat(4))).forEach(Maho::debug);
        loadJavaSupport(instrumentation);
        loadClassLoaderBridge();
        setupTransformer();
    }
    
    public static void setupFromClass(final Class<?> clazz = CallerContext.caller(), final Predicate<ResourcePath.ClassInfo> filter = info -> true) {
        instrumentation();
        try (final var path = ResourcePath.of(clazz)) {
            final Module module = clazz.getModule();
            if (module != Maho.class.getModule())
                accessRequires(module);
            TransformerManager.runtime().setup(clazz.getClassLoader(), path, AOTTransformer.Level.RUNTIME, module.isNamed() ? module.getDescriptor().toNameAndVersion() + "#" + clazz.getSimpleName() : clazz.getSimpleName(), filter);
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
                // May have been defined.
                return Class.forName(name, false, loader);
            } catch (final Throwable notFoundEx) {
                // Some mentally handicapped class loaders will always throw previous failures after a failure.
                // In order to solve this situation we need to iterate through all the loaded classes.
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
    
    public static @Nullable MethodNode getMethodNodeFromClass(final Class<?> target, final String name, final String desc, final boolean mustRetransform = false)
            = Stream.ofNullable(ASMHelper.newClassNode(getBytecodeFromClass(target, mustRetransform)))
            .map(node -> node.methods)
            .flatMap(List::stream)
            .filter(targetMethod -> targetMethod.name.equals(name) && targetMethod.desc.equals(desc))
            .findAny()
            .orElse(null);
    
    public static MethodNode getMethodNodeFromClassNonNull(final Class<?> target, final String name, final String desc, final boolean mustRetransform = false)
            = Optional.ofNullable(getMethodNodeFromClass(target, name, desc, mustRetransform)).orElseThrow(() -> new UnsupportedOperationException("Unable to get MethodNode form: " + target + "#" + name + desc));
    
    public static @Nullable ClassNode getClassNodeFromClass(final Class<?> target, final boolean mustRetransform = false) = ASMHelper.newClassNode(getBytecodeFromClass(target, mustRetransform));
    
    public static ClassNode getClassNodeFromClassNonNull(final Class<?> target, final boolean mustRetransform = false)
            = Optional.ofNullable(getClassNodeFromClass(target, mustRetransform)).orElseThrow(() -> new UnsupportedOperationException("Unable to get ClassNode form: " + target));
    
    private static final Sampler<String> sampler = MahoProfile.sampler("GetBytecode");
    
    @SneakyThrows
    public static @Nullable byte[] getBytecodeFromClass(final Class<?> target, final boolean mustRetransform = false) {
        if (!mustRetransform) {
            final @Nullable InputStream stream = (target.getClassLoader() ?? ClassLoader.getPlatformClassLoader()).getResourceAsStream(target.getName().replace('.', '/') + ".class");
            if (stream != null)
                return stream.readAllBytes();
        }
        final Instrumentation instrumentation = instrumentation();
        if (!instrumentation.isModifiableClass(target))
            return null;
        try (final var handle = sampler.handle(target.getCanonicalName())) {
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
        
        @Nullable byte bytecode[];
        
        @Override
        public @Nullable byte[] transform(final @Nullable ClassLoader loader, final @Nullable String name, final @Nullable Class<?> clazz, final @Nullable ProtectionDomain domain, final @Nullable byte[] bytecode) {
            if (clazz == target)
                this.bytecode = bytecode;
            return null;
        }
        
    }
    
}
