package amadeus.maho.core;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
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
import amadeus.maho.util.annotation.mark.IndirectCaller;
import amadeus.maho.util.dynamic.CallerContext;
import amadeus.maho.util.logging.AsyncLogger;
import amadeus.maho.util.logging.LogLevel;
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
        
        public static void setupFilter(final Predicate<ResourcePath.ClassInfo> value) = setupFilter = setupFilter()?.and(value) ?? value;
        
        public static void skip(final String packageName) {
            final String name = STR."\{packageName}.";
            setupFilter(info -> !info.className().startsWith(name));
        }
        
        @SneakyThrows
        public static void forEach(final Consumer<String> consumer) = Stream.of(Setup.class.getFields()).filter(field -> field.getType() == String.class).map(field -> (String) field.get(null)).forEach(consumer);
        
        public static void minimize() = forEach(Setup::skip);
        
        public static void serial() = executor(Runnable::run);
        
        public static void parallel() = executor(null);
        
        static { Stream.of(env.lookup(MAHO_SETUP_SKIP, "").split(";")).forEach(Setup::skip); }
        
    }
    
    public static final String VERSION = Maho.class.getModule().getDescriptor()?.version().map(Object::toString).orElse("DEV") ?? "DEV";
    
    public static final String MAHO_HOME_VARIABLE = "MAHO_HOME";
    
    public static final String
            MAHO_WORK_DIRECTORY           = "amadeus.maho.work.directory", // string
            MAHO_AGENT_REDIRECT           = "amadeus.maho.agent.redirect", // string
            MAHO_EXPERIMENTAL             = "amadeus.maho.experimental", // boolean
            MAHO_SETUP_SKIP               = "amadeus.maho.setup.skip", // String array split(";")
            MAHO_LLM_THROWABLE_ASSISTANT  = "amadeus.maho.llm.throwable.assistant", // boolean
            MAHO_LOGS_ENABLE              = "amadeus.maho.logs.enable", // boolean
            MAHO_LOGS_LEVEL               = "amadeus.maho.logs.level", // Enum name
            MAHO_LOGS_ENCODE              = "amadeus.maho.logs.encode", // String
            MAHO_LOGS_INSTANCE            = "amadeus.maho.logs.instance", // String
            MAHO_LOGS_FORCED_INTERRUPTION = "amadeus.maho.logs.forced.interruption", // boolean
            MAHO_LOGS_OUTPUT_FILE         = "amadeus.maho.logs.output.file", // boolean
            MAHO_DEBUG_MODE               = "amadeus.maho.debug", // boolean
            MAHO_DEBUG_HOTSWAP            = "amadeus.maho.debug.hotswap", // boolean
            MAHO_DEBUG_DUMP_BYTECODE      = "amadeus.maho.debug.dump.bytecode"; // boolean
    
    private static final Environment env = Environment.local();
    
    @Setter
    @Getter
    private static boolean
            experimental = env.lookup(MAHO_EXPERIMENTAL, true),
            debug        = env.lookup(MAHO_DEBUG_MODE, JDWP.isJDWPEnable()),
            hotswap      = env.lookup(MAHO_DEBUG_HOTSWAP, debug());
    
    @IndirectCaller
    public static String subKey(final Class<?> caller = CallerContext.caller(), final String key) = STR."amadeus.maho.\{caller.getSimpleName().toLowerCase(Locale.ENGLISH)}.\{key}";
    
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
    
    public static BiConsumer<LogLevel, String> namedLogger(final String name = CallerContext.caller().getSimpleName()) = logger()?.namedLogger(name) ?? (BiConsumer<LogLevel, String>) (_, _) -> { };
    
    public static void wrapperStdOut() = LoggerHelper.wrapperStdOut(logger());
    
    public static int bytecodeVersion() = Opcodes.V21;
    
    public static int asmAPIVersion() = Opcodes.ASM9;
    
}
