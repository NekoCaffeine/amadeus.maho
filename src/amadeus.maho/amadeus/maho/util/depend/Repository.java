package amadeus.maho.util.depend;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.build.Module;
import amadeus.maho.util.concurrent.AsyncHelper;
import amadeus.maho.util.depend.maven.MavenRepository;
import amadeus.maho.util.function.FunctionHelper;
import amadeus.maho.util.link.http.HttpSetting;
import amadeus.maho.util.misc.Environment;
import amadeus.maho.util.runtime.ArrayHelper;
import amadeus.maho.util.runtime.ObjectHelper;
import amadeus.maho.util.throwable.ExtraInformationThrowable;
import amadeus.maho.util.tuple.Tuple;
import amadeus.maho.util.tuple.Tuple2;
import amadeus.maho.util.tuple.Tuple3;

import static amadeus.maho.util.concurrent.AsyncHelper.*;

public interface Repository {
    
    @NoArgsConstructor
    class DependencyConflictException extends IllegalArgumentException {
        
        public DependencyConflictException(final ConflictResolution resolution, final Project.Dependency a, final Project.Dependency b, final Collection<List<Project>> aPaths, final Collection<List<Project>> bPaths)
                = super("""
                ConflictResolution: %s
                %s
                  | %s
                %s
                  | %s
                """.formatted(resolution, a.project(), pathToString(aPaths), b.project(), pathToString(bPaths)));
        
        private static String pathToString(final Collection<List<Project>> paths) = paths.stream().map(path -> path.stream().map(Project::toString).collect(Collectors.joining(" -> "))).collect(Collectors.joining("\n  | "));
        
    }
    
    @Getter
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    class Combined implements Repository {
        
        @NoArgsConstructor
        static class Failed extends IOException { }
        
        Collection<Repository> repositories;
        
        ConcurrentHashMap<Project.Dependency, Collection<Project.Dependency>> recursiveResolveCache = { };
        
        public Combined(final Repository... repositories) = this(List.of(repositories));
        
        @Override
        public String debugInfo() = repositories().stream().map(Repository::debugInfo).map("    "::concat).collect(Collectors.joining("\n", "Combined:\n", ""));
        
        @SneakyThrows
        public <T> T delegate(final Project.Dependency dependency, final BiFunction<Repository, Project.Dependency, T> delegate) throws IOException {
            final @Nullable Repository source = dependency.source();
            if (source != null)
                return delegate.apply(source, dependency);
            final LinkedList<Throwable> throwables = { };
            return repositories.stream().safeMap(repository -> delegate.apply(repository, dependency), Throwable.class, (it, e) -> {
                e.addSuppressed(new ExtraInformationThrowable(it.debugInfo()));
                throwables += e;
                return null;
            }).findFirst().orElseThrow(() -> failed(dependency, throwables));
        }
        
        @Override
        @SneakyThrows
        public Tuple2<Project.Dependency, Collection<Project.Dependency>> resolveDependency(final Project.Dependency dependency) throws IOException = delegate(dependency, Repository::resolveDependency);
        
        @Override
        @SneakyThrows
        public int versionIndex(final Project.Dependency dependency) throws IOException = delegate(dependency, Repository::versionIndex);
        
        @Override
        @SneakyThrows
        public Module.SingleDependency resolveModuleDependency(final Project.Dependency dependency, final boolean classes, final boolean sources, final boolean javadoc) throws IOException
                = delegate(dependency, (repository, dep) -> repository.resolveModuleDependency(dep, classes, sources, javadoc));
        
        @Override
        public void dropResolveCache() = repositories().forEach(Repository::dropResolveCache);
        
        protected Failed failed(final Project.Dependency dependency, final Collection<Throwable> throwables) {
            final Failed failed = { "Resolving failed: %s".formatted(dependency) };
            throwables.forEach(failed::addSuppressed);
            return failed;
        }
        
    }
    
    String debugInfo();
    
    Map<Project.Dependency, Collection<Project.Dependency>> recursiveResolveCache();
    
    int versionIndex(final Project.Dependency dependency) throws IOException;
    
    Tuple2<Project.Dependency, Collection<Project.Dependency>> resolveDependency(final Project.Dependency dependency) throws IOException;
    
    @SneakyThrows
    private void resolveDependenciesRecursive(final Project.Dependency dependency, final boolean allowMissing, final ConcurrentHashMap<Project, Collection<Project.Dependency>> context, final Set<Project.Dependency> dependencies,
            final ConcurrentLinkedQueue<CompletableFuture<Void>> futures, final Function<Project.Dependency, ConcurrentLinkedQueue<List<Project>>> dependenciesPath, final List<Project> path)
            = context.computeIfAbsent(dependency.project(), it -> {
                try {
                    final Tuple2<Project.Dependency, Collection<Project.Dependency>> resolved = resolveDependency(dependency);
                    dependencies += resolved.v1;
                    return resolved.v2;
                } catch (final IOException e) {
                    if (allowMissing)
                        return List.of();
                    throw e;
                }
            })
            .stream()
            .peek(subDependency -> dependenciesPath.apply(subDependency).add(path))
            .filter(subDependency -> !context.containsKey(subDependency.project()))
            .map(subDependency -> async(() -> resolveDependenciesRecursive(subDependency, allowMissing, context, dependencies, futures, dependenciesPath, new ArrayList<>(path).let(copy -> copy += dependency.project()))))
            .forEach(futures::add);
    
    @SneakyThrows
    default Collection<Project.Dependency> resolveDependencies(final Collection<Project.Dependency> dependencies, final boolean allowMissing = false, final ConflictResolution resolution = ConflictResolution.LATEST) throws IOException {
        resolveConflict(dependencies.stream(), ConflictResolution.ERROR);
        final Collection<Project> baseProjects = dependencies.stream().map(Project.Dependency::project).collect(Collectors.toSet());
        final ConcurrentHashMap<Project.Dependency, ConcurrentLinkedQueue<List<Project>>> dependenciesPath = { };
        final ConcurrentHashMap<Project, Collection<Project.Dependency>> context = { };
        final Function<Project.Dependency, ConcurrentLinkedQueue<List<Project>>> dependenciesPathGetter = key -> dependenciesPath.computeIfAbsent(key, FunctionHelper.abandon(ConcurrentLinkedQueue::new));
        return resolveConflict(dependencies.stream().map(dependency -> async(() -> recursiveResolveCache().computeIfAbsent(dependency, it -> {
            final ConcurrentLinkedQueue<CompletableFuture<Void>> futures = { };
            final HashSet<Project.Dependency> subDependencies = { };
            futures += async(() -> resolveDependenciesRecursive(it, allowMissing, context, subDependencies, futures, dependenciesPathGetter, new ArrayList<>()));
            Stream.generate(futures::poll).takeWhile(ObjectHelper::nonNull).forEach(AsyncHelper::await);
            return subDependencies;
        }))).toList().stream().map(AsyncHelper::await).flatMap(Collection::stream), resolution, baseProjects, dependenciesPath);
    }
    
    @SneakyThrows
    default Collection<Project.Dependency> resolveConflict(final Stream<Project.Dependency> stream, final ConflictResolution resolution = ConflictResolution.LATEST, final Collection<Project> projects = Set.of(),
            final Map<Project.Dependency, ? extends Collection<List<Project>>> dependenciesPath = Map.of()) throws IOException {
        final ConcurrentHashMap<Tuple3<String, String, String[]>, Project.Dependency> result = { };
        await(stream.map(dependency -> async(() -> result.compute(Tuple.tuple(dependency.project().group(), dependency.project().artifact(), dependency.project().classifiers()),
                (key, value) -> value == null ? dependency : merge(value, dependency, resolution, projects, dependenciesPath)))));
        return result.values();
    }
    
    default Project.Dependency merge(final Project.Dependency a, final Project.Dependency b, final ConflictResolution resolution = ConflictResolution.LATEST, final Collection<Project> projects = Set.of(),
            final Map<Project.Dependency, ? extends Collection<List<Project>>> dependenciesPath = Map.of()) throws IOException = a.equals(b) ? a : a.project().equals(b.project()) ? mergeAttr(a, b) : switch (resolution) {
        case LATEST -> versionIndex(a) >= versionIndex(b) ? mergeAttr(a, b) : mergeAttr(b, a);
        case ASSIGN -> new Project.Dependency(projects.stream()
                .filter(project -> project.group().equals(a.project().group()) && project.artifact().equals(a.project().artifact()) && ArrayHelper.deepArrayEquals(project.classifiers(), a.project().classifiers()))
                .findAny()
                .orElseThrow(() -> conflict(resolution, a, b, dependenciesPath)), a.compile() || b.compile(), a.runtime() || b.runtime(), a.source());
        case ERROR  -> throw conflict(resolution, a, b, dependenciesPath);
    };
    
    private Project.Dependency mergeAttr(final Project.Dependency a, final Project.Dependency b) = { a.project(), a.compile() || b.compile(), a.runtime() || b.runtime(), a.source() };
    
    private DependencyConflictException conflict(final ConflictResolution resolution, final Project.Dependency a, final Project.Dependency b, final Map<Project.Dependency, ? extends Collection<List<Project>>> dependenciesPath)
            = { resolution, a, b, dependenciesPath[a], dependenciesPath[b] };
    
    Module.SingleDependency resolveModuleDependency(Project.Dependency dependency, boolean classes = true, boolean sources = true, boolean javadoc = true) throws IOException;
    
    @SneakyThrows
    default Set<Module.Dependency> resolveModuleDependencies(final Collection<Project.Dependency> dependencies, final boolean allowMissing = false, final ConflictResolution resolution = ConflictResolution.LATEST,
            final boolean classes = true, final boolean sources = true, final boolean javadoc = true) throws IOException
            = resolveDependencies(dependencies, allowMissing, resolution)
            .stream()
            .map(dependency -> async(() -> resolveModuleDependency(dependency, classes, sources, javadoc)))
            .toList()
            .stream()
            .map(AsyncHelper::await)
            .collect(Collectors.toSet());
    
    default void dropResolveCache() = recursiveResolveCache().clear();
    
    static Path defaultCachePath() = ~(Path.of(Environment.local().lookup("MAHO_CACHE", System.getProperty("user.home") + "/Maho/cache")) / "maven");
    
    static Combined maven(final HttpSetting setting = HttpSetting.defaultInstance())
            = { MavenRepository.defaultRepositories().stream().map(it -> it.apply(setting)).collect(Collectors.toCollection(ArrayList::new)) };
    
}
