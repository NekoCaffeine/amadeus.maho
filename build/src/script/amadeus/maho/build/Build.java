package amadeus.maho.build;

import java.lang.module.ModuleReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import jdk.internal.loader.BuiltinClassLoader;

import amadeus.maho.core.Maho;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.transform.AOTTransformer;
import amadeus.maho.util.build.Distributive;
import amadeus.maho.util.build.IDEA;
import amadeus.maho.util.build.Jar;
import amadeus.maho.util.build.Javac;
import amadeus.maho.util.build.Module;
import amadeus.maho.util.build.Workspace;
import amadeus.maho.util.depend.Project;
import amadeus.maho.util.depend.Repository;
import amadeus.maho.util.runtime.FileHelper;
import amadeus.maho.util.shell.Main;
import amadeus.maho.util.shell.Shell;

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
    
    Module module = { "amadeus.maho", dependencies() };
    
    static void sync() {
        IDEA.deleteLibraries(workspace);
        IDEA.generateAll(workspace, "17", true, List.of(Module.build(), module));
    }
    
    static Path build(final boolean aot = false) {
        workspace.clean(module).flushMetadata();
        Javac.compile(workspace, module, path -> true, args -> {
            final Path upgrade = workspace.root() / Module.build().path() / "upgrade";
            if (Files.isDirectory(upgrade)) {
                args += "--upgrade-module-path";
                args += upgrade.toAbsolutePath().toString();
            }
        });
        final Map<String, Jar.Result> pack = Jar.pack(workspace, module, Jar.manifest(Main.class.getCanonicalName(), new Jar.Agent(Maho.class.getCanonicalName())));
        final Path modulesDir = workspace.output(Jar.MODULES_DIR, module), targetDir = aot ? ~workspace.output("aot-" + Jar.MODULES_DIR, module) : modulesDir;
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
        System.out.println("Push: %s".formatted(build.toAbsolutePath() | "/"));
        final Path path = Maho.jar(), home = Files.isRegularFile(path) ? -+-path :
                Optional.ofNullable(System.getenv("MAHO_HOME")).map(Path::of).orElseThrow(() -> new IllegalStateException("Environment variable 'MAHO_HOME' is missing"));
        Shell.Context.standardJavaFileManager().close();
        final Consumer<Path> consumer = root -> root >> home;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> closeModuleReaderAndPush(build, home, consumer), "Maho Build Task - Push to `MAHO_HOME`"));
        System.exit(0);
    }
    
    static void aotPush() = push(aotBuild());
    
    @SneakyThrows
    private static void closeModuleReaderAndPush(final Path build, final Path home, final Consumer<Path> copy) throws InterruptedException {
        ((Privilege) ((BuiltinClassLoader) Maho.class.getClassLoader()).moduleToReader).values().forEach(ModuleReader::close); // Unlock 'modules'
        FileHelper.retryWhenIOE(() -> {
            --(home / "modules");
            --(home / "sources");
        });
        build | copy;
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
        Files.writeString(mark, map.entrySet().stream().map(entry -> "%s: %s".formatted(entry.getKey(), entry.getValue())).collect(Collectors.joining("\n")));
        push(build);
    }
    
    int debugPort = 36768;
    
    static Process debug(final List<String> args = List.of()) = workspace.run(module, debugPort, args);
    
}
