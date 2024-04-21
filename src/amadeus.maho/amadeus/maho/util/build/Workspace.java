package amadeus.maho.util.build;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.ToString;
import amadeus.maho.util.config.Config;
import amadeus.maho.util.misc.Environment;

import static com.sun.jna.Platform.*;

@ToString
@EqualsAndHashCode
public record Workspace(Path root, Config config = Config.of(Config.Locator.ofFileSystem(root / Path.of("build"))), Path buildDir = Path.of("build"), Path outputDir = Path.of("output")) {
    
    public Path buildOutputDir() = buildDir() / outputDir();
    
    public Path output(final String type) = buildOutputDir() / type;
    
    public Path output(final String type, final Module module) = buildOutputDir() / module.path() / type;
    
    public BuildMetadata metadata() = config().load(new BuildMetadata());
    
    public self flushMetadata(final BuildMetadata metadata = metadata().let(it -> it.buildCount++)) = config().save(metadata);
    
    public self clean(final Module module) = --(root() / module.path() / buildOutputDir());
    
    public Process run(final Module module, final int debugPort = -1, final List<String> jvmArgs = List.of(), final boolean openTerminal = true,
            final Path runDir = root() / module.path() / "run", final Predicate<Path> useModulePath = path -> true) = run(runDir, openTerminal, runArgs(module, debugPort, jvmArgs, useModulePath));
    
    public List<String> runArgs(final Module module, final int debugPort = -1, final List<String> jvmArgs = List.of(), final Predicate<Path> useModulePath = path -> true) {
        final ArrayList<String> args = { };
        final ArrayList<Path> p = { }, cp = { };
        args += Environment.local().lookup(ScriptHelper.MAHO_JAVA_EXECUTION, (Path.of(System.getProperty("java.home")) / "bin" / "java").toAbsolutePath().toString());
        if (debugPort != -1)
            args += STR."-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=localhost:\{debugPort}";
        args *= jvmArgs;
        p += (root() / output(Jar.MODULES_DIR, module)).toAbsolutePath();
        module.dependencies().stream()
                .filter(Module.SingleDependency::runtime)
                .map(Module.SingleDependency::classes)
                .forEach(path -> useModulePath.test(path) ? p : cp += path);
        if (p.nonEmpty()) {
            args += "-p";
            args += ScriptHelper.W_QUOTES.formatted(p.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
        }
        if (cp.nonEmpty()) {
            args += "-cp";
            args += ScriptHelper.W_QUOTES.formatted(cp.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
        }
        args += "-m";
        args += module.name();
        return args;
    }
    
    @SneakyThrows
    public Process run(final Path directory = Path.of(""), final boolean openTerminal = true, final List<String> args) {
        final ArrayList<String> commands = { };
        if (openTerminal) {
            final String command = String.join(" ", args);
            switch (getOSType()) {
                case WINDOWS -> commands *= List.of("cmd", "/c", "start", "/wait", "cmd", "/c", STR."\{command} & pause > nul");
                case LINUX   -> commands *= List.of("xterm", "-e", STR."\{command} ; read -n 1 -s");
                case MAC     -> commands *= List.of("osascript", "-e", STR."tell application 'Terminal' to do script '\{command} ; read -n 1 -s'");
                default      -> throw unsupportedOSType();
            }
        } else
            switch (getOSType()) {
                case WINDOWS -> commands *= List.of("cmd", "/c", String.join(" ", args));
                default      -> commands *= args;
            }
        return new ProcessBuilder()
                .directory((~directory).toAbsolutePath().toFile())
                .command(commands)
                .start();
    }
    
    static UnsupportedOperationException unsupportedOSType() = { STR."Unsupported OS Type: \{System.getProperty("os.name")} \{System.getProperty("os.version")} (\{System.getProperty("os.arch")})" };
    
    public static Workspace here(final Path buildDir = Path.of("build"), final Path outputDir = Path.of("output"))
            = { Path.of(""), Config.of(Config.Locator.ofFileSystem(buildDir)), buildDir, outputDir };
    
}
