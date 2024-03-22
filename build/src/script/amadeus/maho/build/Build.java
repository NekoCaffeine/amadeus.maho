package amadeus.maho.build;

import java.lang.module.ModuleReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jdk.internal.loader.BuiltinClassLoader;

import amadeus.maho.core.Maho;
import amadeus.maho.core.MahoImage;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.AOTTransformer;
import amadeus.maho.util.build.Distributive;
import amadeus.maho.util.build.IDEA;
import amadeus.maho.util.build.Jar;
import amadeus.maho.util.build.Javac;
import amadeus.maho.util.build.Jlink;
import amadeus.maho.util.build.Module;
import amadeus.maho.util.build.Workspace;
import amadeus.maho.util.depend.Project;
import amadeus.maho.util.depend.Repository;
import amadeus.maho.util.misc.Environment;
import amadeus.maho.util.runtime.FileHelper;
import amadeus.maho.util.shell.Main;
import amadeus.maho.util.shell.Shell;
import amadeus.maho.vm.tools.hotspot.WhiteBox;

@SneakyThrows
public interface Build {
    
    Workspace workspace = Workspace.here();
    
    Repository maven = Repository.maven();
    
    static Set<Module.Dependency> dependencies() = maven.resolveModuleDependencies(new Project.Dependency.Holder().all(
            "org.ow2.asm:asm:+",
            "org.ow2.asm:asm-tree:+",
            "org.ow2.asm:asm-commons:+",
            "org.ow2.asm:asm-util:+",
            "net.java.dev.jna:jna:+",
            "net.java.dev.jna:jna-platform:+"
    ).dependencies());
    
    static Set<Module.Dependency> linkDependencies() = maven.resolveModuleDependencies(new Project.Dependency.Holder().all(
            "org.ow2.asm:asm:+",
            "org.ow2.asm:asm-tree:+",
            "org.ow2.asm:asm-commons:+",
            "org.ow2.asm:asm-util:+",
            "net.java.dev.jna:jna-jpms:+",
            "net.java.dev.jna:jna-platform-jpms:+"
    ).dependencies());
    
    Module module = { "amadeus.maho", dependencies() }, linkModule = { module.name(), linkDependencies() };
    
    static void sync() {
        IDEA.deleteLibraries(workspace);
        IDEA.generateAll(workspace, "21", true, List.of(Module.build(), module));
    }
    
    static Path build(final boolean aot = false, final boolean shouldUpgrade = false) {
        workspace.clean(module).flushMetadata();
        Javac.compile(workspace, module, path -> true, shouldUpgrade ? args -> {
            final Path upgrade = workspace.root() / Module.build().path() / "upgrade";
            if (Files.isDirectory(upgrade)) {
                args += "--upgrade-module-path";
                args += upgrade.toAbsolutePath().toString();
            }
        } : _ -> { });
        final Map<String, Jar.Result> pack = Jar.pack(workspace, module, Jar.manifest(Main.class.getCanonicalName(), new Jar.Agent(Maho.class.getCanonicalName())));
        final Path modulesDir = workspace.output(Jar.MODULES_DIR, module), targetDir = aot ? ~workspace.output(STR."aot-\{Jar.MODULES_DIR}", module) : modulesDir;
        if (aot)
            AOTTransformer.transform(modulesDir, targetDir);
        return Distributive.zip(workspace, module, root -> {
            workspace.root() / "includes" >> root;
            final Path modules = ~(root / "modules"), sources = ~(root / "sources");
            module.dependencies().forEach(dependency -> {
                dependency.sources() >> sources;
                dependency.classes() >> modules;
            });
            pack.values().forEach(result -> {
                result.sources() >> sources;
                (aot ? targetDir / (modulesDir % result.modules()).toString() : result.modules()) >> modules;
            });
        });
    }
    
    static Path aotBuild() = build(true);
    
    static void push(final Path build = build()) {
        System.out.println(STR."Push: \{build.toAbsolutePath() | "/"}");
        final Path path = Maho.jar(), home = Files.isRegularFile(path) ? -+-path :
                Optional.ofNullable(System.getenv("MAHO_HOME")).map(Path::of).orElseThrow(() -> new IllegalStateException("Environment variable 'MAHO_HOME' is missing"));
        Shell.Context.standardJavaFileManager().close();
        WhiteBox.instance().fullGC();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ((Privilege) ((BuiltinClassLoader) Maho.class.getClassLoader()).moduleToReader).values().forEach(ModuleReader::close); // Unlock 'modules'
            // Class loading is disabled from here
            FileHelper.retryWhenIOE(() -> {
                if (Files.isRegularFile(path) && !Files.isDirectory(home / "modules"))
                    build | root -> root / "modules" >> --+-path;
                else
                    build | root -> {
                        Files.list(root)
                                .filter(Files::isDirectory)
                                .forEach(dir -> --(home / dir.getFileName().toString()));
                        root >> home;
                    };
            });
        }, "Maho Build Task - Push to `MAHO_HOME`"));
        System.exit(0);
    }
    
    static void upgrade(final boolean aot = true) {
        final Path distributive = build(aot, true);
        distributive >> ~--workspace.output("upgrade", module);
        distributive | root -> root / "modules" >> ~--(workspace.buildDir() / "upgrade-modules");
    }
    
    static void aotPush() = push(aotBuild());
    
    static Path link(final Path output = workspace.output("maho-image", module)) {
        final Path result = Jlink.createMahoImage(workspace, linkModule, --output);
        final @Nullable String image = Environment.local().lookup(MahoImage.VARIABLE);
        if (image != null)
            result >> ~--Path.of(image);
        return result;
    }
    
    static void mark(final String type = "stable") {
        final Path build = aotBuild();
        final Path mark = Path.of("rollback.mark");
        final List<String> lines = Files.readAllLines(mark);
        final LinkedHashMap<String, String> map = { };
        lines.forEach(line -> {
            final String pair[] = line.trim().split(":");
            if (pair.length == 2)
                map.put(pair[0].trim(), pair[1].trim());
        });
        map[type] = build.getFileName().toString();
        Files.writeString(mark, map.entrySet().stream().map(entry -> STR."\{entry.getKey()}: \{entry.getValue()}").collect(Collectors.joining("\n")));
        push(build);
    }
    
    int debugPort = 36768;
    
    List<String> defaultArgs = List.of("-XX:+EnableDynamicAgentLoading", STR."-XX:CICompilerCount=\{Runtime.getRuntime().availableProcessors()}", "-XX:CompileThresholdScaling=0.05");
    
    static Process debug(final List<String> args = defaultArgs) = workspace.run(module, debugPort, args);
    
    static void bootstrap(final boolean aot = true) {
        build(aot);
        debug();
    }
    
}
