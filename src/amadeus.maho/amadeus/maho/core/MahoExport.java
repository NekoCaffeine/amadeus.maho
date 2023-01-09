package amadeus.maho.core;

import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.objectweb.asm.Opcodes;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.APIStatus;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.logging.AsyncLogger;
import amadeus.maho.util.logging.LoggerHelper;
import amadeus.maho.util.misc.Environment;
import amadeus.maho.util.resource.ResourcePath;
import amadeus.maho.vm.JDWP;

@NoArgsConstructor(AccessLevel.PRIVATE)
@APIStatus(design = APIStatus.Stage.β, implement = APIStatus.Stage.γ)
public final class MahoExport {
    
    public static class Setup {
        
        public static final String
                MAHO_HACKING    = "amadeus.maho.hacking",
                MAHO_SIMULATION = "amadeus.maho.simulation";
        
        private static @Nullable Predicate<ResourcePath.ClassInfo> setupFilter;
        
        private static @Nullable Executor executor;
        
        private static boolean state = env.lookup(MAHO_SETUP_ENABLE, true);
        
        static @Nullable Predicate<ResourcePath.ClassInfo> setupFilter() = setupFilter;
        
        public static void setupFilter(final Predicate<ResourcePath.ClassInfo> value) = setupFilter = setupFilter() == null ? value : setupFilter().and(value);
        
        public static void skip(final String packageName) {
            final String name = packageName + ".";
            setupFilter(info -> !info.className().startsWith(name));
        }
        
        @SneakyThrows
        public static void forEach(final Consumer<String> consumer) = Stream.of(Setup.class.getFields()).filter(field -> field.getType() == String.class).map(field -> (String) field.get(null)).forEach(consumer);
        
        public static void minimize() = forEach(Setup::skip);
        
        public static Executor executor() = executor;
        
        public static void executor(final @Nullable Executor value) = executor = value;
        
        public static void serial() {
            executor(Runnable::run);
            env.value(MAHO_SETUP_PARALLEL, false);
        }
        
        public static void parallel() = executor(null);
        
        public static void disable() = state = false;
        
        public static void enable() = state = true;
        
        public static boolean state() = state;
        
        public static void disableScanAnnotation() = env.value(MAHO_SETUP_SCAN_ANNOTATION, false);
        
        public static void enableScanAnnotation() = env.value(MAHO_SETUP_SCAN_ANNOTATION, true);
        
        public static void disableScanProvider() = env.value(MAHO_SETUP_SCAN_PROVIDER, false);
        
        public static void enableScanProvider() = env.value(MAHO_SETUP_SCAN_PROVIDER, true);
        
        static {
            if (!env.lookup(MAHO_SETUP_PARALLEL, true))
                serial();
            Stream.of(env.lookup(MAHO_SETUP_SKIP, "").split(";"))
                    .nonnull()
                    .forEach(Setup::skip);
        }
        
    }
    
    public static final String VERSION = "DEV";
    
    public static final String
            MAHO_WORK_DIRECTORY           = "maho.work.directory", // string
            MAHO_EXPERIMENTAL_ENABLE      = "maho.experimental", // boolean
            MAHO_SETUP_ENABLE             = "maho.setup.enable", // boolean
            MAHO_SETUP_PARALLEL           = "maho.setup.parallel", // boolean
            MAHO_SETUP_SKIP               = "maho.setup.skip", // String array split(";")
            MAHO_SETUP_SCAN_ANNOTATION    = "maho.setup.scan.annotation", // boolean
            MAHO_SETUP_SCAN_PROVIDER      = "maho.setup.scan.provider", // boolean
            MAHO_LOGS_ENABLE              = "maho.logs.enable", // boolean
            MAHO_LOGS_LEVEL               = "maho.logs.level", // Enum name
            MAHO_LOGS_ENCODE              = "maho.logs.encode", // String
            MAHO_LOGS_INSTANCE            = "maho.logs.instance", // String
            MAHO_LOGS_ASYNC               = "maho.logs.async", // boolean
            MAHO_LOGS_FORCED_INTERRUPTION = "maho.logs.forced_interruption", // boolean
            MAHO_LOGS_LOG_FILE            = "maho.logs.log_file", // boolean
            MAHO_DEBUG_MODE               = "maho.debug", // boolean
            MAHO_DEBUG_DUMP_BYTECODE      = "maho.debug.dump_bytecode", // boolean
            MAHO_SIMULATOR_DYNAMIC        = "maho.simulator.dynamic", // boolean
            MAHO_HACKING_LSF              = "maho.hacking.lsf"; // boolean
    
    private static final Environment env = Environment.local();
    
    @Setter
    @Getter
    private static boolean
            experimental = env.lookup(MAHO_EXPERIMENTAL_ENABLE, true),
            debug        = env.lookup(MAHO_DEBUG_MODE, JDWP.isJDWPEnable());
    
    @SneakyThrows
    public static Path workDirectory() = Path.of(env.lookup(MAHO_WORK_DIRECTORY, Path.of("").toRealPath().toString()));
    
    private static boolean loggerState = env.lookup(MAHO_LOGS_ENABLE, true);
    
    private static @Nullable AsyncLogger logger;
    
    @Getter(lazy = true)
    private static final AsyncLogger fatalLogger = LoggerHelper.makeAsyncLogger("MAHO-FATAL", true);
    
    private static @Nullable System.LoggerFinder loggerFinder;
    
    public static boolean loggerState() = loggerState;
    
    public static void disableLogger() = loggerState = false;
    
    public static void enableLogger() = loggerState = true;
    
    public static @Nullable AsyncLogger logger() = loggerState() ? logger != null ? logger : (logger = LoggerHelper.makeAsyncLogger("MAHO", env.lookup(MAHO_LOGS_LOG_FILE, true))) : null;
    
    public static void logger(final AsyncLogger value) = logger = value;
    
    public static @Nullable System.LoggerFinder loggerFinder() = loggerFinder;
    
    public static void loggerFinder(final @Nullable System.LoggerFinder value) = loggerFinder = value;
    
    public static void wrapperStdOut() = LoggerHelper.wrapperStdOut(logger());
    
    public static int bytecodeVersion() = Opcodes.V17;
    
    public static int asmAPIVersion() = Opcodes.ASM9;
    
}
