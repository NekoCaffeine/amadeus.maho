package amadeus.maho.util.build;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import amadeus.maho.core.MahoExport;
import amadeus.maho.core.MahoImage;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.misc.Environment;

import static com.sun.jna.Platform.*;

public interface ScriptHelper {
    
    String
            D_PAIR        = "-D%s=%s",
            X_FLAG        = "-X%s",
            X_PAIR        = "-X%s:%s",
            XX_PAIR       = "-XX:%s=%s",
            XX_ENABLE     = "-XX:+%s",
            XX_DISABLE    = "-XX:-%s",
            ENABLE_ASSERT = "-ea";
    
    String MAHO_JAVA_EXECUTION = "maho.java.execution";
    
    static void useMahoImageIfPossible() {
       final @Nullable String image = System.getenv(MahoImage.VARIABLE);
       if (image != null) {
           final Path imagePath = Path.of(image);
           if (Files.isDirectory(imagePath)) {
               final Path java = imagePath / "bin" / (getOSType() == WINDOWS ? "java.exe" : "java");
               if (Files.isRegularFile(java))
                   Environment.local()[MAHO_JAVA_EXECUTION] = java.toAbsolutePath().toString();
           }
       }
    }
    
    static void useDefaultImage() = Environment.local()[MAHO_JAVA_EXECUTION] = "java";
    
    static void useContextImage() = Environment.local()[MAHO_JAVA_EXECUTION] = (Path.of(System.getProperty("java.home")) / "bin" / "java").toAbsolutePath().toString();
    
    static Process run(final Workspace workspace, final Module module, final int debugPort = -1, final List<String> jvmArgs = List.of(), final boolean openTerminal = true,
            final Path runDir = workspace.root() / module.path() / "run", final Predicate<Path> useModulePath = path -> true) = run(runDir, openTerminal, runArgs(workspace, module, debugPort, jvmArgs, useModulePath));
    
    static List<String> runArgs(final Workspace workspace, final Module module, final int debugPort = -1, final List<String> jvmArgs = List.of(), final Predicate<Path> useModulePath = path -> true) {
        final ArrayList<String> args = { };
        final ArrayList<Path> p = { }, cp = { };
        args += Environment.local().lookup(MAHO_JAVA_EXECUTION, (Path.of(System.getProperty("java.home")) / "bin" / "java").toAbsolutePath().toString());
        if (debugPort != -1)
            args += "-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=localhost:" + debugPort;
        args *= jvmArgs;
        p += (workspace.root() / workspace.output(Jar.MODULES_DIR, module)).toAbsolutePath();
        module.dependencies().stream()
                .filter(Module.SingleDependency::runtime)
                .map(Module.SingleDependency::classes)
                .forEach(path -> useModulePath.test(path) ? p : cp += path);
        if (p.nonEmpty()) {
            args += "-p";
            args += p.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
        }
        if (cp.nonEmpty()) {
            args += "-cp";
            args += cp.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
        }
        args += "-m";
        args += module.name();
        return args;
    }
    
    @SneakyThrows
    static Process run(final Path directory = Path.of(""), final boolean openTerminal = true, final List<String> args) {
        final ArrayList<String> commands = { };
        if (openTerminal) {
            final String command = String.join(" ", args);
            switch (getOSType()) {
                case WINDOWS -> commands *= List.of("cmd", "/c", "start", "/wait", "cmd", "/c", "%s & pause > nul".formatted(command));
                case LINUX   -> commands *= List.of("xterm", "-e", "%s ; read -n 1 -s".formatted(command));
                case MAC     -> commands *= List.of("osascript", "-e", "tell application 'Terminal' to do script '%s ; read -n 1 -s'".formatted(command));
                default      -> throw unsupportedOSType();
            }
        } else
            commands *= args;
        return new ProcessBuilder()
                .directory((~directory).toAbsolutePath().toFile())
                .command(commands)
                .start();
    }
    
    static UnsupportedOperationException unsupportedOSType() = { "Unsupported OS Type: %s %s (%s)".formatted(System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch")) };
    
    String
            ALL_DEFAULT     = "ALL-DEFAULT",
            ALL_SYSTEM      = "ALL-SYSTEM",
            ALL_MODULE_PATH = "ALL-MODULE-PATH";
    
    static void addModules(final Collection<String> args, final String... modules) = Stream.of(modules).forEach(module -> {
        args += "--add-modules";
        args += module;
    });
    
    static void addAgent(final Collection<String> args, final Path agentJar) = args += "-javaagent:%s".formatted(agentJar.toAbsolutePath() | "/");
    
    static void println(final Object msg) = System.out.println(msg);
    
    static void info(final Object msg) = System.out.println("INFO: " + msg);
    
    static void debug(final Object msg) {
        if (MahoExport.debug())
            System.out.println("DEBUG: " + msg);
    }
    
    static void warning(final Object msg) = System.err.println("WARNING: " + msg);
    
    static void error(final Object msg) = System.err.println("ERROR: " + msg);
    
    static void fatal(final Object msg) {
        System.err.println("FATAL: " + msg);
        System.exit(-1);
    }
    
}
