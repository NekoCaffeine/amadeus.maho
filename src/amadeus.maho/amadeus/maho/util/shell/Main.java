package amadeus.maho.util.shell;

import java.io.File;
import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
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
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.JavaFileManager;

import jdk.internal.module.ModuleBootstrap;

import amadeus.maho.core.Maho;
import amadeus.maho.core.MahoExport;
import amadeus.maho.core.extension.ModuleAdder;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.transform.AOTTransformer;
import amadeus.maho.transform.TransformerManager;
import amadeus.maho.util.control.ContextHelper;
import amadeus.maho.util.logging.LogLevel;
import amadeus.maho.util.misc.Environment;
import amadeus.maho.util.misc.ExitCodeException;
import amadeus.maho.util.resource.ResourcePath;
import amadeus.maho.util.runtime.DebugHelper;
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
            final ConcurrentLinkedQueue<String> names = p_worker[0].collection();
            Shell.imports() += names.stream()
                    .filter(StringHelper::nonEmptyOrNull)
                    .flatMap(name -> Stream.of("import %s;".formatted(name), "import static %s.*;".formatted(name)))
                    .collect(Collectors.joining("\n", "\n", "\n"));
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
            final URLClassLoader loader = { new URL[]{ scriptOutputPath.toUri().toURL() }, Main.class.getClassLoader() };
            final ModuleFinder finder = ModuleFinder.of(scriptOutputPath);
            final List<ModuleDescriptor> modules = finder.findAll().stream().map(ModuleReference::descriptor).toList();
            final ModuleLayer parentLayer = Main.class.getModule().getLayer();
            final Configuration config = parentLayer.configuration().resolveAndBind(finder, ModuleBootstrap.unlimitedFinder(), modules.stream().map(ModuleDescriptor::name).collect(Collectors.toSet()));
            final ModuleLayer scriptLayer = parentLayer.defineModulesWithOneLoader(config, loader);
            scriptLayer.modules().forEach(Maho::accessRequires);
            try (final var path = ResourcePath.of(loader)) {
                TransformerManager.runtime().setup(loader, path, AOTTransformer.Level.RUNTIME, modules.stream().map(ModuleDescriptor::toNameAndVersion).collect(Collectors.joining(";")));
            }
            try (final var ignored = ContextHelper.rollbackThreadContextClassLoader(loader)) {
                Shell.extra() *= args(args);
                exitCode = Shell.attachTool(shellOptions);
            }
        } else {
            Shell.extra() *= args(args);
            exitCode = Shell.attachTool();
        }
        if (exitCode != 0)
            throw DebugHelper.breakpointBeforeThrow(new ExitCodeException("Shell exited abnormally: %d".formatted(exitCode), exitCode));
        else
            System.exit(0);
    }
    
    private static List<String> args(final String... args) = Stream.of(args).map(Main::map).collect(Collectors.toCollection(ArrayList::new));
    
    private static String map(final String arg) = inlineArgMapping[arg] ?? (arg.codePoints().allMatch(Character::isJavaIdentifierPart) ? arg + "()" : arg);
    
}
