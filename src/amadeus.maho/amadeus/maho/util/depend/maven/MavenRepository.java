package amadeus.maho.util.depend.maven;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.build.Module;
import amadeus.maho.util.container.MapTable;
import amadeus.maho.util.data.XML;
import amadeus.maho.util.depend.CacheableHttpRepository;
import amadeus.maho.util.depend.JarRequirements;
import amadeus.maho.util.depend.Project;
import amadeus.maho.util.depend.Repository;
import amadeus.maho.util.depend.RepositoryFileNotFoundException;
import amadeus.maho.util.link.http.HttpSetting;
import amadeus.maho.util.logging.LogLevel;
import amadeus.maho.util.misc.Environment;
import amadeus.maho.util.runtime.ArrayHelper;
import amadeus.maho.util.runtime.ObjectHelper;
import amadeus.maho.util.tuple.Tuple;
import amadeus.maho.util.tuple.Tuple2;

import static amadeus.maho.util.concurrent.AsyncHelper.*;

@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MavenRepository extends CacheableHttpRepository {
    
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
    public static abstract class PomVisitor extends XML.Visitor {
        
        private static final List<String>
                projectPrefix    = List.of("project"),
                parentPrefix     = List.of("project", "parent"),
                propertiesPrefix = List.of("project", "properties");
        
        LinkedList<String>        tags      = { };
        LinkedList<StringBuilder> dataStack = { };
        
        HashMap<String, String>
                projectProperties = { },
                properties        = { };
        
        { dataStack << new StringBuilder(); }
        
        protected String data() {
            final String data = dataStack.getLast().toString();
            final StringBuilder builder = { }, var = { };
            final boolean p_flag[] = { false, false };
            data.codePoints().forEach(c -> {
                switch (c) {
                    case '$' -> {
                        if (p_flag[1]) {
                            builder.append('$').append('{').append('$');
                            p_flag[1] = false;
                        } else if (p_flag[0]) {
                            builder.append('$');
                            p_flag[0] = false;
                        } else
                            p_flag[0] = true;
                    }
                    case '{' -> {
                        if (p_flag[1]) {
                            builder.append('$').append('{').append('{');
                            p_flag[1] = false;
                        } else if (p_flag[0]) {
                            p_flag[0] = false;
                            p_flag[1] = true;
                        } else
                            builder.append('{');
                    }
                    case '}' -> {
                        if (p_flag[1]) {
                            final String key = var.toString();
                            final @Nullable String value = projectProperties[key] ?? properties[key];
                            if (value != null)
                                builder.append(value);
                            else
                                builder.append('$').append('{').append(var).append('}');
                            var.setLength(0);
                            p_flag[1] = false;
                        } else if (p_flag[0]) {
                            p_flag[0] = false;
                            builder.append('$').append('}');
                        } else
                            builder.append('}');
                    }
                    default  -> {
                        if (p_flag[1])
                            var.appendCodePoint(c);
                        else {
                            if (p_flag[0]) {
                                builder.append('$');
                                p_flag[0] = false;
                            }
                            builder.appendCodePoint(c);
                        }
                    }
                }
            });
            if (p_flag[0])
                builder.append('$');
            else if (p_flag[1])
                builder.append('$').append('{');
            return builder.toString();
        }
        
        protected boolean startsWish(final List<String> prefix) {
            if (tags.size() < prefix.size())
                return false;
            final Iterator<String> prefixIterator = prefix.iterator(), tagsIterator = tags.iterator();
            while (prefixIterator.hasNext())
                if (!prefixIterator.next().equals(tagsIterator.next()))
                    return false;
            return true;
        }
        
        protected boolean inProperties() = startsWish(propertiesPrefix);
        
        @Override
        public void visitCharData(final String data) {
            dataStack.getLast().append(data);
            super.visitCharData(data);
        }
        
        @Override
        public void visitTagBegin(final String tag, final Map<String, String> attr) {
            tags << tag;
            dataStack << new StringBuilder();
            super.visitTagBegin(tag, attr);
        }
        
        @Override
        public void visitTagEnd(final String tag) {
            if (inProperties())
                properties[tags.stream().skip(2L).collect(Collectors.joining("."))] = data();
            tags--;
            if (projectPrefix.equals(tags) || parentPrefix.equals(tags) && !properties.containsKey(tag))
                projectProperties[STR."project.\{tag}"] = data();
            dataStack--;
            super.visitTagEnd(tag);
        }
        
    }
    
    @ToString
    @EqualsAndHashCode
    public record SnapshotVersion(String classifier, String extension, String value) {
        
        @NoArgsConstructor
        @FieldDefaults(level = AccessLevel.PRIVATE)
        public static final class Visitor extends PomVisitor {
            
            private static final List<String> snapshotVersionPrefix = List.of("metadata", "versioning", "snapshotVersions", "snapshotVersion");
            
            @Nullable String classifier, extension, value;
            
            @Getter
            ArrayList<SnapshotVersion> result = { };
            
            @Override
            public void visitTagEnd(final String tag) {
                if (startsWish(snapshotVersionPrefix))
                    switch (tag) {
                        // @formatter:off
                        case "classifier"      -> classifier = data();
                        case "extension"       -> extension  = data();
                        case "value"           -> value      = data();
                        case "snapshotVersion" -> result    += peek();
                        // @formatter:on
                    }
                super.visitTagEnd(tag);
            }
            
            public SnapshotVersion peek() {
                final SnapshotVersion result = { classifier ?? "", extension ?? "", ObjectHelper.requireNonNull(value) };
                classifier = extension = value = null;
                return result;
            }
            
        }
        
    }
    
    @ToString
    @EqualsAndHashCode
    public record VersionInfo(String latest, String release, String lastUpdated, String... versions) {
        
        @NoArgsConstructor
        @FieldDefaults(level = AccessLevel.PRIVATE)
        public static final class Visitor extends PomVisitor {
            
            private static final List<String> versioningPrefix = List.of("metadata", "versioning");
            
            @Nullable String latest, release, lastUpdated;
            LinkedList<String> versions = { };
            
            @Override
            public void visitTagEnd(final String tag) {
                if (startsWish(versioningPrefix))
                    switch (tag) {
                        // @formatter:off
                        case "latest"      -> latest      = data();
                        case "release"     -> release     = data();
                        case "lastUpdated" -> lastUpdated = data();
                        case "version"     -> versions   += data();
                        // @formatter:on
                    }
                super.visitTagEnd(tag);
            }
            
            public VersionInfo result() = { latest!, release!, lastUpdated!, versions.toArray(String[]::new) };
            
        }
        
    }
    
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static final class PomDependenciesVisitor extends PomVisitor {
        
        private static final List<String> dependencyPrefix = List.of("project", "dependencies", "dependency");
        
        final HashSet<Project.Dependency> dependencies = { };
        
        @Nullable String group, artifact, version, scope, optional;
        
        @Override
        public void visitTagEnd(final String tag) {
            if (startsWish(dependencyPrefix) && tags.size() == dependencyPrefix.size() + (tag.equals("dependency") ? 0 : 1))
                switch (tag) {
                    // @formatter:off
                    case "groupId"    -> group    = data();
                    case "artifactId" -> artifact = data();
                    case "version"    -> version  = data();
                    case "scope"      -> scope    = data();
                    case "optional"   -> optional = data();
                    case "dependency" -> {
                        if (!Boolean.parseBoolean(optional)) {
                            boolean compile = false, runtime = false;
                            switch (scope ?? "compile") {
                                case "compile" -> compile = runtime = true;
                                case "provided"-> compile = true;
                                case "runtime" -> runtime = true;
                            }
                            if (compile || runtime) {
                                final Project project = { group!, artifact!, version ?? "+" };
                                final Project.Dependency dependency = { project, compile, runtime };
                                dependencies += dependency;
                            }
                        }
                        group = artifact = version = scope = optional = null;
                    }
                    // @formatter:on
                }
            super.visitTagEnd(tag);
        }
        
        public Collection<Project.Dependency> result() = dependencies;
        
    }
    
    private static final String POM = "pom", JAR = "jar", MD5 = "md5", MD5_SUFFIX = STR.".\{MD5}", SOURCES = "sources", JAVADOC = "javadoc";
    
    @Getter
    boolean hasReleases, hasSnapshots;
    
    ConcurrentHashMap<Project.Dependency, CompletableFuture<Tuple2<Project.Dependency, Collection<Project.Dependency>>>> resolveCache = { };
    
    ConcurrentHashMap<Project, Collection<SnapshotVersion>> snapshotVersionsCache = { };
    
    MapTable<String, String, VersionInfo> versionInfoCache = MapTable.ofConcurrentHashMapTable();
    
    public String projectDir(final Project project) = STR."\{project.group().replace('.', '/')}/\{project.artifact()}/\{project.version()}/";
    
    public String projectFileName(final Project project, final String extension)
            = project.classifiers().length > 0 ? STR."\{project.artifact()}-\{projectVersion(project, extension)}-\{String.join("-", project.classifiers())}" : STR."\{project.artifact()}-\{projectVersion(project, extension)}";
    
    @SneakyThrows
    public String projectVersion(final Project project, final String extension) {
        if (project.version().endsWith("-SNAPSHOT")) {
            if (!hasSnapshots())
                throw new RepositoryFileNotFoundException(STR."\{project}.\{extension}", this);
            final Path relative = Path.of(STR."\{project.group().replace('.', '/')}/\{project.artifact()}/\{project.version()}/maven-metadata.xml");
            final String classifier = String.join("-", project.classifiers());
            return snapshotVersionsCache.computeIfAbsent(project.dropClassifier(), _ -> {
                        try {
                            final Path path = tryUpdateCache(relative);
                            final SnapshotVersion.Visitor visitor = { };
                            XML.read(path, visitor, relative | "/");
                            return visitor.result();
                        } catch (final RepositoryFileNotFoundException e) { return List.of(); }
                    })
                    .stream()
                    .filter(version -> classifier.equals(version.classifier()) && extension.equals(version.extension))
                    .findFirst()
                    .map(SnapshotVersion::value)
                    .orElseGet(project::version);
        }
        if (!hasReleases())
            throw new RepositoryFileNotFoundException(STR."\{project}.\{extension}", this);
        return project.version();
    }
    
    @Override
    public String uri(final Project project, final String extension) = projectDir(project) + projectFileName(project, extension);
    
    public static String readMD5(final Path path) throws IOException = Files.readString(path).split(" ")[0];
    
    @Override
    @SneakyThrows
    public boolean checkCacheCompleteness(final Path cache) {
        final Path checksum = cache << MD5_SUFFIX;
        try {
            return Files.exists(checksum) && cache.checksum(MD5).equalsIgnoreCase(readMD5(checksum).strip());
        } catch (final IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    @Override
    @SneakyThrows
    public void onDownloadCompleted(final Path relative, final Path cache, final boolean completenessMetadata) {
        if (!completenessMetadata) {
            final String relativeChecksum = cache.checksum(MD5), actualChecksum = readMD5(downloadDataFormRemote(relative << MD5_SUFFIX, cache << MD5_SUFFIX, true));
            if (!relativeChecksum.equalsIgnoreCase(actualChecksum))
                throw new IllegalStateException(STR."relative=\{cache}, checksum=\{relativeChecksum}, actual=\{actualChecksum}");
            else
                setting().logger()[LogLevel.DEBUG] = STR."Checksum verified: \{cache} (\{relativeChecksum})";
        }
    }
    
    @SneakyThrows
    public VersionInfo resolveVersionInfo(final String group, final String artifact) throws IOException = versionInfoCache.row(group).computeIfAbsent(artifact, it -> {
        final Path relative = Path.of(STR."\{group.replace('.', '/')}/\{it}/maven-metadata.xml");
        return new VersionInfo.Visitor().let(visitor -> XML.read(tryUpdateCache(relative), visitor, relative | "/")).result();
    });
    
    @SneakyThrows
    public boolean latest(final String group, final String artifact, final String version) throws IOException = version.equals(resolveVersionInfo(group, artifact).latest);
    
    @SneakyThrows
    public boolean latest(final Project project) throws IOException = latest(project.group(), project.artifact(), project.version());
    
    @SneakyThrows
    public boolean valid(final String group, final String artifact, final String version) throws IOException = exists(Path.of(STR."\{group.replace('.', '/')}/\{artifact}/\{version}/maven-metadata.xml"));
    
    @SneakyThrows
    public boolean valid(final Project project) throws IOException = valid(project.group(), project.artifact(), project.version());
    
    @SneakyThrows
    protected Path tryUpdateCache(final Path relative) { try { return downloadDataFormRemote(relative); } catch (final IOException e) { return cache(relative); } }
    
    @Override
    public int versionIndex(final Project.Dependency dependency) throws IOException = ArrayHelper.indexOf(resolveVersionInfo(dependency.project().group(), dependency.project().artifact()).versions(), dependency.project().version());
    
    @Override
    @SneakyThrows
    public Tuple2<Project.Dependency, Collection<Project.Dependency>> resolveDependency(final Project.Dependency dependency) throws IOException = await(resolveCache.computeIfAbsent(dependency, it -> async(() -> {
        final Project project = resolveWildcards(dependency.project());
        try {
            final Path pom = relative(project, POM);
            return Tuple.tuple(new Project.Dependency(project, dependency.compile(), dependency.runtime(), this), new PomDependenciesVisitor().let(visitor -> XML.read(cache(pom), visitor, pom | "/")).result());
        } catch (final RepositoryFileNotFoundException notFoundEx) {
            final Path jar = relative(project, JAR);
            if (exists(jar))
                return Tuple.tuple(dependency, Set.of());
            throw new RepositoryFileNotFoundException(jar.toString(), this);
        }
    })));
    
    public Project resolveWildcards(final Project project) throws IOException = switch (project.version()) {
        case "+" -> new Project(project.group(), project.artifact(), resolveVersionInfo(project.group(), project.artifact()).latest(), project.classifiers());
        default  -> project;
    };
    
    @Override
    @SneakyThrows
    public Module.SingleDependency resolveModuleDependency(final Project.Dependency dependency, final JarRequirements requirements) throws IOException {
        final Project project = resolveWildcards(dependency.project());
        final CompletableFuture<Path>
                classesTask = requirements.classes() ? async(() -> cache(relative(project, JAR))) : completed(),
                sourcesTask = requirements.sources() ? async(() -> tryCache(() -> relative(project.concatClassifier(SOURCES), JAR))) : completed(),
                javadocTask = requirements.javadoc() ? async(() -> tryCache(() -> relative(project.concatClassifier(JAVADOC), JAR))) : completed();
        return { await(classesTask), await(sourcesTask), await(javadocTask), dependency.compile(), dependency.runtime() };
    }
    
    @Override
    public void dropResolveCache() {
        super.dropResolveCache();
        resolveCache.clear();
        snapshotVersionsCache.clear();
        versionInfoCache.clear();
    }
    
    @Getter
    private static final List<Function<HttpSetting, MavenRepository>> defaultRepositories = new CopyOnWriteArrayList<>(List.of(MavenRepository::releases, MavenRepository::snapshots));
    
    @Getter
    private static final Map<String, Function<HttpSetting, MavenRepository>> mirrors = new ConcurrentHashMap<>(Map.of("tencent", MavenRepository::tencent));
    
    static { defaultRepositories().addAll(0, Stream.of(Environment.local().lookup("MAHO_MAVEN_MIRRORS", "").split(";")).map(mirror -> mirror.toLowerCase(Locale.ENGLISH)).map(mirrors()::get).nonnull().toList()); }
    
    public static MavenRepository snapshots(final HttpSetting setting = HttpSetting.defaultInstance(), final Path cacheDir = Repository.defaultCachePath() / "snapshots")
            = { cacheDir, "https://oss.sonatype.org/content/repositories/snapshots/", setting, false, true };
    
    public static MavenRepository releases(final HttpSetting setting = HttpSetting.defaultInstance(), final Path cacheDir = Repository.defaultCachePath() / "releases")
            = { cacheDir, "https://repo1.maven.org/maven2/", setting, true, false };
    
    public static MavenRepository tencent(final HttpSetting setting = HttpSetting.defaultInstance(), final Path cacheDir = Repository.defaultCachePath() / "tencent")
            = { cacheDir, "https://mirrors.cloud.tencent.com/nexus/repository/maven-public/", setting, true, true };
    
}
