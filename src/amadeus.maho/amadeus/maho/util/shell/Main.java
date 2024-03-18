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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.internal.module.ModuleBootstrap;

import amadeus.maho.core.Maho;
import amadeus.maho.core.MahoExport;
import amadeus.maho.core.extension.ModuleAdder;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.transform.AOTTransformer;
import amadeus.maho.transform.TransformerManager;
import amadeus.maho.util.build.Javac;
import amadeus.maho.util.control.ContextHelper;
import amadeus.maho.util.logging.LogLevel;
import amadeus.maho.util.misc.Environment;
import amadeus.maho.util.misc.ExitCodeException;
import amadeus.maho.util.resource.ResourcePath;
import amadeus.maho.util.runtime.DebugHelper;
import amadeus.maho.util.runtime.StringHelper;
import amadeus.maho.vm.tools.hotspot.JIT;

import static amadeus.maho.util.build.Javac.*;

public class Main {
    
    public static final String COMPILED_SCRIPT_DIR = "compiled-script";
    
    private static final Map<String, String> inlineArgMapping = Map.of(".", "/exit");
    
    private static void init() {
        new URLConnection(null) {
            
            @Override
            public void connect() throws IOException { }
            
        }.setDefaultUseCaches(false);
        final Environment local = Environment.local();
        local.lookup(MahoExport.MAHO_LOGS_LEVEL, LogLevel.WARNING.name());
        if (local.lookup("amadeus.maho.shell.minimize", true))
            MahoExport.Setup.minimize();
        Maho.instrumentation();
    }
    
    @SneakyThrows
    public static void main(final String... args) {
        init();
        List.of("com.sun.tools.javac.", "amadeus.maho.lang.javac.").forEach(JIT.instance()::compileAll);
        final int exitCode;
        final Path scriptDir = Path.of("build", "src", "script");
        if (Files.isDirectory(scriptDir)) {
            final Path scriptOutputPath = ~--Path.of("build", COMPILED_SCRIPT_DIR);
            final String scriptOutputDir = scriptOutputPath.toRealPath().toString();
            final List<Path> paths = Files.walk(scriptDir).filter(javaFileMatcher()::matches).toList();
            final List<String> options = new ArrayList<>(runtimeOptions(false)).let(list -> {
                list += "-d";
                list += scriptOutputDir;
            });
            final Request request = { paths, options };
            final Javac.CompileTask<?> task = request.serialCompileTask();
            final ClassesNameListener listener = { task.context() };
            try (task) { task.compile(); }
            Shell.imports() += listener.names().stream()
                    .filter(StringHelper::nonEmptyOrNull)
                    .flatMap(name -> Stream.of(STR."import \{name};", STR."import static \{name}.*;"))
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
    
    private static String map(final String arg) = inlineArgMapping[arg] ?? (arg.codePoints().allMatch(Character::isJavaIdentifierPart) ? STR."\{arg}()" : arg);
    
}
