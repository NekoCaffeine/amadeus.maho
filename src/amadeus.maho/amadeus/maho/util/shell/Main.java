package amadeus.maho.util.shell;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.JavaFileManager;

import amadeus.maho.core.Maho;
import amadeus.maho.core.MahoExport;
import amadeus.maho.core.extension.ModuleAdder;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.util.logging.LogLevel;
import amadeus.maho.util.misc.Environment;
import amadeus.maho.util.runtime.StringHelper;

import static amadeus.maho.util.build.Javac.*;

public class Main {
    
    private static final Map<String, String> inlineArgMapping = Map.of(".", "/exit");
    
    private static void init() {
        new URLConnection(null) {
            
            @Override
            public void connect() throws IOException { }
            
        }.setDefaultUseCaches(false);
        final Environment local = Environment.local();
        local.lookup(MahoExport.MAHO_LOGS_LEVEL, LogLevel.WARNING.name());
        if (local.lookup("maho.shell.minimize", true))
            MahoExport.Setup.minimize();
        System.out.println("Initializing maho shell, this may take a few seconds.");
        Maho.instrumentation();
    }
    
    @SneakyThrows
    public static void main(final String... args) {
        init();
        final int exitCode;
        final Path scriptDir = Path.of("build", "src", "script");
        if (Files.isDirectory(scriptDir)) {
            final Path scriptOutputPath = ~--Path.of("build", "compiled-script");
            final String scriptOutputDir = scriptOutputPath.toRealPath().toString();
            final ClassesNameListener p_worker[] = { null };
            final JavaFileManager p_manager[] = { null };
            compile(Files.walk(scriptDir).filter(javaFileMatcher()::matches).toList(), new ArrayList<>(runtimeOptions(false)).let(list -> {
                list += "-d";
                list += scriptOutputDir;
            }), StandardCharsets.UTF_8, Locale.getDefault(), task -> p_worker[0] = { task }, manager -> p_manager[0] = manager);
            p_manager[0].close();
            Shell.imports(Shell.imports() + p_worker[0].collection().stream()
                    .filter(StringHelper::nonEmptyOrNull)
                    .map("import static %s.*;"::formatted)
                    .collect(Collectors.joining("\n", "\n", "\n")));
            final List<String> runtimeOptions = runtimeOptions(), shellOptions = new ArrayList<>(runtimeOptions.size());
            boolean p = false;
            for (final String option : runtimeOptions)
                if (p) {
                    shellOptions += scriptOutputDir + File.pathSeparator + option;
                    p = false;
                } else {
                    switch (option) {
                        case "-p",
                             "-cp" -> p = true;
                    }
                    shellOptions += option;
                }
            ModuleAdder.injectMissingSystemModules(scriptOutputPath / (MODULE_INFO + CLASS_SUFFIX));
            Thread.currentThread().setContextClassLoader(new URLClassLoader(new URL[]{ scriptOutputPath.toUri().toURL() }, Main.class.getClassLoader()));
            Shell.extra() *= args(args);
            exitCode = Shell.attachTool(shellOptions);
        } else {
            Shell.extra() *= args(args);
            exitCode = Shell.attachTool();
        }
        if (exitCode != 0)
            throw new RuntimeException("Shell exits abnormally, exitCode: " + exitCode);
        else
            System.exit(0);
    }
    
    private static List<String> args(final String... args) = Stream.of(args).map(Main::map).collect(Collectors.toCollection(ArrayList::new));
    
    private static String map(final String arg) = inlineArgMapping[arg] ?? (arg.codePoints().allMatch(Character::isJavaIdentifierPart) ? arg + "()" : arg);
    
}
