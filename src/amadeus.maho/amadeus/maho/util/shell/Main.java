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
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.internal.module.ModuleBootstrap;

import com.sun.tools.javac.tree.JCTree;

import amadeus.maho.core.Maho;
import amadeus.maho.core.MahoExport;
import amadeus.maho.core.extension.ModuleAdder;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.AOTTransformer;
import amadeus.maho.transform.TransformerManager;
import amadeus.maho.util.build.Dependencies;
import amadeus.maho.util.build.Module;
import amadeus.maho.util.control.ContextHelper;
import amadeus.maho.util.depend.Project;
import amadeus.maho.util.depend.Repository;
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
        local.lookup(MahoExport.MAHO_LLM_THROWABLE_ASSISTANT, true);
        if (local.lookup("amadeus.maho.shell.minimize", true))
            MahoExport.Setup.minimize();
        Maho.instrumentation();
    }
    
    @SneakyThrows
    public static void main(final String... args) {
        init();
        List.of("com.sun.tools.javac.", "amadeus.maho.lang.javac.").forEach(JIT.instance()::compileAll);
        final int exitCode;
        final Path scriptDir = Path.of("build", "src", "script"), moduleInfoSource = scriptDir / (MODULE_INFO + JAVA_SUFFIX);
        if (Files.isDirectory(scriptDir) && Files.isRegularFile(moduleInfoSource)) {
            final List<Path> dependencies = dependencies(moduleInfoSource);
            final Path scriptOutputPath = ~--Path.of("build", COMPILED_SCRIPT_DIR);
            final String scriptOutputDir = scriptOutputPath.toRealPath().toString();
            final List<Path> paths = Files.walk(scriptDir).filter(javaFileMatcher()::matches).toList();
            final List<String> options = injectDependencies(new ArrayList<>(runtimeOptions(false)).let(list -> {
                list += "-d";
                list += scriptOutputDir;
            }), dependencies);
            final Request request = { paths, options };
            final CompileTask<?> task = request.serialCompileTask();
            final ClassesNameListener listener = { task.context() };
            try (task) { task.compile(); }
            Shell.imports() += listener.names().stream()
                    .filter(StringHelper::nonEmptyOrNull)
                    .flatMap(name -> Stream.of(STR."import \{name};", STR."import static \{name}.*;"))
                    .collect(Collectors.joining("\n", "\n", "\n"));
            final List<String> runtimeOptions = runtimeOptions(), shellOptions = new ArrayList<>(runtimeOptions.size());
            boolean p = false, findP = false;
            for (final String option : runtimeOptions)
                if (p) {
                    shellOptions += scriptOutputDir + File.pathSeparator + option;
                    p = false;
                } else {
                    switch (option) {
                        case "-p" -> findP = p = true;
                    }
                    shellOptions += option;
                }
            if (!findP) {
                shellOptions += "-p";
                shellOptions += scriptOutputDir;
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
    
    @SneakyThrows
    private static List<Path> dependencies(final Path moduleInfoSource) {
        final String source = Files.readString(moduleInfoSource);
        if (source.contains(STR."@\{Dependencies.class.getSimpleName()}") || source.contains(STR."@\{Dependencies.class.getCanonicalName()}")) {
            final Request request = { List.of(moduleInfoSource), runtimeOptions(false) };
            final CompileTask<?> task = request.serialCompileTask();
            final @Nullable JCTree.JCModuleDecl decl = ~task.parse().getFirst().defs.stream().cast(JCTree.JCModuleDecl.class);
            task.checkErrors();
            if (decl != null && decl.mods.annotations != null) {
                final Set<String> names = Set.of(Dependencies.class.getSimpleName(), Dependencies.class.getCanonicalName());
                final @Nullable JCTree.JCAnnotation annotation = ~decl.mods.annotations.stream().filter(it -> names[it.annotationType.toString()] && it.args.nonEmpty());
                if (annotation != null) {
                    final @Nullable JCTree.JCExpression arg = ~annotation.args.stream().cast(JCTree.JCAssign.class).filter(it -> it.lhs.toString().equals("value")) ?? annotation.args.getFirst();
                    if (arg != null) {
                        final List<String> dependencies = switch (arg) {
                            case JCTree.JCNewArray newArray                                           -> newArray.elems.stream().cast(JCTree.JCLiteral.class).map(literal -> literal.value).cast(String.class).toList();
                            case JCTree.JCLiteral literal when literal.value instanceof String string -> List.of(string);
                            default                                                                   -> List.of();
                        };
                        if (dependencies.nonEmpty())
                            return Repository.maven().resolveModuleDependencies(new Project.Dependency.Holder().all(dependencies).dependencies()).stream().flatMap(Module.Dependency::flat).map(Module.SingleDependency::classes).toList();
                    }
                }
            }
        }
        return List.of();
    }
    
    private static List<String> args(final String... args) = Stream.of(args).map(Main::map).collect(Collectors.toCollection(ArrayList::new));
    
    private static String map(final String arg) = inlineArgMapping[arg] ?? (arg.codePoints().allMatch(Character::isJavaIdentifierPart) ? STR."\{arg}()" : arg);
    
}
