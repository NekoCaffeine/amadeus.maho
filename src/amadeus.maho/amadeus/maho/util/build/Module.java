package amadeus.maho.util.build;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import amadeus.maho.core.Maho;
import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.annotation.mark.IndirectCaller;
import amadeus.maho.util.depend.Project;
import amadeus.maho.util.depend.Repository;
import amadeus.maho.util.dynamic.CallerContext;

@ToString
@EqualsAndHashCode
public record Module(Path path = Path.of(""), String name, Map<String, Path> subModules = Map.of(name, Path.of("src", name)), Set<? extends Dependency> rawDependencies, List<Module> dependencyModules = List.of(),
                     Set<SingleDependency> dependencies = rawDependencies.stream().flatMap(Dependency::flat).collect(Collectors.toSet())) {
    
    @ToString
    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PUBLIC)
    public static class Metadata {
        
        String version = "0.0.1";
        
    }
    
    public sealed interface Dependency {
        
        Stream<SingleDependency> flat();
        
    }
    
    @ToString
    @EqualsAndHashCode
    public record SingleDependency(Path classes, @Nullable Path sources = null, @Nullable Path javadoc = null, boolean compile = true, boolean runtime = true) implements Dependency {
        
        @Override
        public Stream<SingleDependency> flat() = Stream.of(this);
        
        @SneakyThrows
        public static Set<SingleDependency> maho() throws FileNotFoundException = allOf(-+-Maho.jar());
        
        @SneakyThrows
        public static Set<SingleDependency> allOf(final Path home) throws FileNotFoundException = Files.walk(home / "modules")
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(Jar.SUFFIX))
                .map(path -> {
                    final Path sources = -+-path / "sources" / path.getFileName().toString().replaceLast(Jar.SUFFIX, STR."-sources\{Jar.SUFFIX}");
                    return new SingleDependency(path, Files.isRegularFile(sources) ? sources : null);
                })
                .collect(Collectors.toSet());
        
    }
    
    @ToString
    @EqualsAndHashCode
    public record DependencySet(String name, Set<SingleDependency> dependencies, boolean compile = true, boolean runtime = true, boolean sameSources = false) implements Dependency {
        
        @Override
        public Stream<SingleDependency> flat() = dependencies.stream();
        
        @SneakyThrows
        public static DependencySet maho() throws FileNotFoundException = { STR."Maho \{Maho.class.getModule().getDescriptor().rawVersion().orElse("SNAPSHOTS")}", SingleDependency.maho() };
        
        @SneakyThrows
        public static DependencySet of(final Path home) throws FileNotFoundException = { STR."\{home.fileName()} \{readVersion(home)}", SingleDependency.allOf(home) };
        
        private static String readVersion(final Path home) {
            final Path version = home / "version";
            if (Files.isReadable(version))
                try {
                    return Files.readString(version);
                } catch (final IOException ignored) { }
            return "SNAPSHOTS";
        }
        
    }
    
    @SneakyThrows
    public static Set<Dependency> buildDependencies() {
        final HashSet<Dependency> result = { };
        final java.lang.Module module = CallerContext.caller().getModule();
        final @Nullable Dependencies dependencies = module.getAnnotation(Dependencies.class);
        if (dependencies != null) {
            final Set<SingleDependency> set = Repository.maven().resolveModuleDependencies(new Project.Dependency.Holder().all(dependencies.value()).dependencies()).stream().flatMap(Dependency::flat).collect(Collectors.toSet());
            final DependencySet moduleDependencySet = { module.getName(), set };
            result += moduleDependencySet;
        }
        return result;
    }
    
    @SneakyThrows
    public static Set<Dependency> buildDependenciesWithMaho() = buildDependencies() += DependencySet.maho();
    
    @SneakyThrows
    @IndirectCaller
    public static Module build(final Set<Dependency> dependencies = buildDependenciesWithMaho()) = { Path.of("build"), "script", dependencies };
    
}
