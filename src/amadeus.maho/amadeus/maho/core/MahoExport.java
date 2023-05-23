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
                PACKAGE_SIMULATION = "amadeus.maho.simulation";
        
        @Setter
        @Getter
        private static @Nullable Predicate<ResourcePath.ClassInfo> setupFilter;
        
        @Setter
        @Getter
        private static @Nullable Executor executor;
        
        public static void setupFilter(final Predicate<ResourcePath.ClassInfo> value) = setupFilter = setupFilter() == null ? value : setupFilter().and(value);
        
        public static void skip(final String packageName) {
            final String name = packageName + ".";
            setupFilter(info -> !info.className().startsWith(name));
        }
        
        @SneakyThrows
        public static void forEach(final Consumer<String> consumer) = Stream.of(Setup.class.getFields()).filter(field -> field.getType() == String.class).map(field -> (String) field.get(null)).forEach(consumer);
        
        public static void minimize() = forEach(Setup::skip);
        
        public static void serial() = executor(Runnable::run);
        
        public static void parallel() = executor(null);
        
        static { Stream.of(env.lookup(MAHO_SETUP_SKIP, "").split(";")).forEach(Setup::skip); }
        
    }
    
    public static final String VERSION = "DEV";
    
    public static final String
            MAHO_WORK_DIRECTORY           = "maho.work.directory", // string
            MAHO_AGENT_REDIRECT           = "maho.agent.redirect", // string
            MAHO_EXPERIMENTAL             = "maho.experimental", // boolean
            MAHO_SETUP_SKIP               = "maho.setup.skip", // String array split(";")
            MAHO_LOGS_ENABLE              = "maho.logs.enable", // boolean
            MAHO_LOGS_LEVEL               = "maho.logs.level", // Enum name
            MAHO_LOGS_ENCODE              = "maho.logs.encode", // String
            MAHO_LOGS_INSTANCE            = "maho.logs.instance", // String
            MAHO_LOGS_FORCED_INTERRUPTION = "maho.logs.forced.interruption", // boolean
            MAHO_LOGS_OUTPUT_FILE         = "maho.logs.output.file", // boolean
            MAHO_DEBUG_MODE               = "maho.debug", // boolean
            MAHO_DEBUG_DUMP_BYTECODE      = "maho.debug.dump.bytecode"; // boolean
    
    private static final Environment env = Environment.local();
    
    @Setter
    @Getter
    private static boolean
            experimental = env.lookup(MAHO_EXPERIMENTAL, true),
            debug        = env.lookup(MAHO_DEBUG_MODE, JDWP.isJDWPEnable());
    
    @SneakyThrows
    public static Path workDirectory() = Path.of(env.lookup(MAHO_WORK_DIRECTORY, Path.of("").toRealPath().toString()));
    
    @Setter
    @Getter
    private static boolean loggerState = env.lookup(MAHO_LOGS_ENABLE, true);
    
    @Setter
    @Getter
    private static @Nullable AsyncLogger logger;
    
    @Getter(lazy = true)
    private static final AsyncLogger fatalLogger = LoggerHelper.makeAsyncLogger("MAHO-FATAL", true);
    
    @Setter
    @Getter
    private static @Nullable System.LoggerFinder loggerFinder;
    
    public static void disableLogger() = loggerState(false);
    
    public static void enableLogger() = loggerState(true);
    
    public static @Nullable AsyncLogger logger() = loggerState() ? logger != null ? logger : (logger = LoggerHelper.makeAsyncLogger("MAHO", env.lookup(MAHO_LOGS_OUTPUT_FILE, true))) : null;
    
    public static void wrapperStdOut() = LoggerHelper.wrapperStdOut(logger());
    
    public static int bytecodeVersion() = Opcodes.V17;
    
    public static int asmAPIVersion() = Opcodes.ASM9;
    
}
