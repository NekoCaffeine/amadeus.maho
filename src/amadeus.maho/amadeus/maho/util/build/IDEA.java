package amadeus.maho.util.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.depend.JarRequirements;
import amadeus.maho.util.depend.Project;
import amadeus.maho.util.depend.Repository;
import amadeus.maho.util.depend.maven.MavenRepository;
import amadeus.maho.util.link.http.HttpSetting;
import amadeus.maho.util.tuple.Tuple2;

import static amadeus.maho.util.concurrent.AsyncHelper.*;
import static amadeus.maho.util.data.XML.*;

public interface IDEA {
    
    interface DevKit {
        
        String
                JetBrains           = "https://www.jetbrains.com/",
                INTELLIJ_REPOSITORY = JetBrains + "intellij-repository/",
                SNAPSHOTS           = INTELLIJ_REPOSITORY + "snapshots/",
                RELEASES            = INTELLIJ_REPOSITORY + "releases/";
        
        static Repository.Combined repository(final Path cacheDir = Repository.defaultCachePath() / "intellij", final HttpSetting setting = HttpSetting.defaultInstance())
                = { new Repository[] { new MavenRepository(cacheDir, SNAPSHOTS, setting, true, true), new MavenRepository(cacheDir, RELEASES, setting, true, false) } };
        
        @SneakyThrows
        static LinkedHashSet<Module.Dependency> attachLocalInstance(final Path instanceHome, final Set<String> plugins = Set.of(), final Predicate<Path> shouldInCompile = path -> true,
                final Tuple2<String, String> metadata = inferInstanceMetadata(instanceHome), final Path sources = resolveSources(repository(), metadata)) = Stream.concat(
                        Stream.of(new Module.DependencySet("IntelliJ IDEA %s-%s".formatted(metadata.v1, metadata.v2), Files.list(instanceHome / "lib")
                                .filter(Files::isRegularFile)
                                .filter(path -> path.getFileName().toString().endsWith(Jar.SUFFIX))
                                .map(path -> new Module.SingleDependency(path, sources, null, shouldInCompile.test(path)))
                                .collect(Collectors.toCollection(LinkedHashSet::new)))),
                        plugins.stream()
                                .map(Pattern::compile)
                                .map(Pattern::asMatchPredicate)
                                .reduce(Predicate::or)
                                .stream()
                                .flatMap(predicate -> Files.list(instanceHome / "plugins")
                                        .filter(Files::isDirectory)
                                        .filter(path -> predicate.test(path.getFileName().toString()))
                                        .map(pluginDir -> new Module.DependencySet("IntelliJ IDEA Built-in Plugin [%s]".formatted(pluginDir.getFileName()), Files.list(pluginDir / "lib")
                                                .filter(Files::isRegularFile)
                                                .filter(path -> path.getFileName().toString().endsWith(Jar.SUFFIX))
                                                .map(path -> new Module.SingleDependency(path, sources, null, shouldInCompile.test(path)))
                                                .collect(Collectors.toCollection(LinkedHashSet::new))))))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        
        @SneakyThrows
        static Tuple2<String, String> inferInstanceMetadata(final Path instanceHome) {
            final Path product_info = instanceHome / "product-info.json";
            if (Files.exists(product_info))
                try {
                    final String info = Files.readString(product_info);
                    return { info.find("\"productCode\"\\s?:\\s?\\\"(.+?)\\\"").isEmptyOr("IC"), info.find("\"buildNumber\"\\s?:\\s?\\\"(.+?)\\\"").requireNonEmpty() };
                } catch (final Exception ignored) { }
            return { "IC", instanceHome.getFileName().toString() };
        }
        
        @SneakyThrows
        static Path resolveSources(final Repository repository, final Tuple2<String, String> metadata, final LinkedHashSet<String> record = { }, final String... suffix = { "-EAP-CANDIDATE-SNAPSHOT", "-EAP-SNAPSHOT", "" })
                = (record.add(metadata.v2) ? Stream.of(suffix).map(it -> tryResolveSources(repository, metadata, it)).nonnull().findFirst()
                .or(() -> Optional.ofNullable(resolveSources(repository, lowAdaptive(metadata), record, suffix))) : Optional.<Path>empty())
                .orElseThrow(() -> new IOException("Cannot be resolved from the following archives." + record.stream().map(
                        it -> "com.jetbrains.intellij.idea:idea%s:%s:sources".formatted(metadata.v1, it)).collect(Collectors.joining("\n    ", "\n    ", ""))));
        
        static Tuple2<String, String> lowAdaptive(final Tuple2<String, String> metadata) {
            final String args[] = metadata.v2.split("\\.");
            if (args.length > 2)
                return { metadata.v1, metadata.v2.substring(0, args[0].length() + args[1].length() + 1) };
            return metadata;
        }
        
        static @Nullable Path tryResolveSources(final Repository repository, final Tuple2<String, String> metadata, final String suffix) {
            try {
                final Project.Dependency dependency = { Project.of("com.jetbrains.intellij.idea:idea%s:%s%s:sources".formatted(metadata.v1, metadata.v2, suffix)) };
                final Module.SingleDependency result = repository.resolveModuleDependency(dependency, JarRequirements.ONLY_CLASSES);
                return result.classes();
            } catch (final IOException e) { return null; }
        }
        
        @SneakyThrows
        private static LinkedHashSet<Module.Dependency> baseDependencySet() = new LinkedHashSet<Module.Dependency>() += Module.SingleDependency.maho().stream()
                .filter(dependency -> dependency.classes().getFileName().toString().startsWith("amadeus.maho-"))
                .findFirst()
                .map(dependency -> new Module.SingleDependency(dependency.classes(), dependency.sources(), dependency.javadoc(), true, false))
                .orElseThrow();
        
        @SneakyThrows
        static Module run(final Path instanceHome) = {
                Path.of("intellij.run"), "intellij.run", baseDependencySet() += new Module.DependencySet("IntelliJ IDEA Run",
                Files.list(instanceHome / "lib")
                        .filter(Files::isRegularFile)
                        .map(Module.SingleDependency::new)
                        .collect(Collectors.toCollection(LinkedHashSet::new)))
        };
        
    }
    
    record ModuleMetadata(String jdkVersion, boolean preview = true, String languageLevel = "JDK_" + jdkVersion, String jdkType = SDK_JAVA) { }
    
    String
            IDEA               = ".idea",
            LIBRARIES          = "libraries",
            IML                = ".iml",
            MODULES_XML        = "modules" + SUFFIX,
            MISC_XML           = "misc" + SUFFIX,
            NAME_URL           = "url",
            NAME_TYPE          = "type",
            NAME_NAME          = "name",
            NAME_VERSION       = "version",
            NAME_LIBRARY       = "library",
            FILE               = "file://",
            MODULE_DIR         = "$MODULE_DIR$/",
            PROJECT_DIR        = "$PROJECT_DIR$/",
            TAG_PROJECT        = "project",
            TAG_MODULE         = "module",
            TAG_MODULES        = "modules",
            TAG_COMPONENT      = "component",
            TAG_OUTPUT         = "output",
            TAG_EXCLUDE_OUTPUT = "exclude-output",
            TAG_CONTENT        = "content",
            TAG_SOURCE_FOLDER  = "sourceFolder",
            TAG_EXCLUDE_FOLDER = "excludeFolder",
            TAG_ORDER_ENTRY    = "orderEntry",
            TAG_LIBRARY        = "library",
            TAG_ROOT           = "root",
            TAG_CLASSES        = "CLASSES",
            TAG_JAVADOC        = "JAVADOC",
            TAG_SOURCES        = "SOURCES",
            SDK_JAVA           = "JavaSDK";
    
    Map<String, String>
            ATTR_PROJECT                 = Map.of(NAME_VERSION, "4"),
            ATTR_MODULE                  = Map.of(NAME_TYPE, "JAVA_MODULE", NAME_VERSION, "4"),
            ATTR_NEW_MODULE_ROOT_MANAGER = Map.of(NAME_NAME, "NewModuleRootManager", "inherit-compiler-output", "true"),
            ATTR_PROJECT_MODULE_MANAGER  = Map.of(NAME_NAME, "ProjectModuleManager"),
            ATTR_CONTENT                 = Map.of(NAME_URL, "file://$MODULE_DIR$"),
            ATTR_INHERITED_JDK           = Map.of(NAME_TYPE, "inheritedJdk"),
            ATTR_SOURCE_FOLDER           = Map.of(NAME_TYPE, "sourceFolder", "forTests", "false"),
            ATTR_MODULE_LIBRARY          = Map.of(NAME_TYPE, "module-library", "exported", ""),
            ATTR_LIBRARY_TABLE           = Map.of(NAME_NAME, "libraryTable"),
            ATTR_LIBRARY                 = Map.of(NAME_NAME, "library");
    
    static void generateIml(final Workspace workspace, final Module module, final @Nullable ModuleMetadata metadata) = module.subModules().forEach((name, location) -> {
        ~(workspace.root() / module.path() / location);
        final Writer writer = { };
        writer.visitDeclaration();
        writer.visitTag(TAG_MODULE, () -> writer.visitTag(TAG_COMPONENT, () -> {
            writer.visitEmptyTag(TAG_EXCLUDE_OUTPUT);
            writer.visitTag(TAG_CONTENT, () -> {
                writer.visitEmptyTag(TAG_SOURCE_FOLDER, Map.of(NAME_URL, FILE + MODULE_DIR + (location | "/"), "isTestSource", "false"));
                if (module.path().toString().isEmpty()) {
                    writer.visitEmptyTag(TAG_EXCLUDE_FOLDER, Map.of(NAME_URL, FILE + MODULE_DIR + IDEA));
                    writer.visitEmptyTag(TAG_EXCLUDE_FOLDER, Map.of(NAME_URL, FILE + MODULE_DIR + IDEA + "/" + (workspace.outputDir() | "/")));
                }
            }, ATTR_CONTENT);
            writer.visitEmptyTag(TAG_ORDER_ENTRY, metadata == null ? ATTR_INHERITED_JDK : Map.of(NAME_TYPE, "inheritedJdk", "jdkName", metadata.jdkVersion, "jdkType", metadata.jdkType));
            writer.visitEmptyTag(TAG_ORDER_ENTRY, ATTR_SOURCE_FOLDER);
            module.rawDependencies().forEach(dependency -> {
                if (dependency instanceof Module.SingleDependency singleDependency)
                    writer.visitTag(TAG_ORDER_ENTRY, () -> writer.visitTag(TAG_LIBRARY, () -> writeLibrary(writer, singleDependency)), libraryAttr(singleDependency));
                else if (dependency instanceof Module.DependencySet dependencySet)
                    writer.visitEmptyTag(TAG_ORDER_ENTRY, libraryAttr(Map.of(NAME_TYPE, NAME_LIBRARY, NAME_NAME, dependencySet.name(), "level", "project"), dependencySet.compile(), dependencySet.runtime()));
            });
            module.dependencyModules().forEach(dependencyModule -> writer.visitEmptyTag(TAG_ORDER_ENTRY, Map.of(NAME_TYPE, TAG_MODULE, "module-name", dependencyModule.name(), "exported", "")));
        }, metadata == null ? ATTR_NEW_MODULE_ROOT_MANAGER : Map.of(NAME_NAME, "NewModuleRootManager", "LANGUAGE_LEVEL", metadata.preview ?
                metadata.languageLevel + "_PREVIEW" : metadata.languageLevel, "inherit-compiler-output", "true")), ATTR_MODULE);
        writer >> ~(workspace.root() / module.path()) / (name + IML);
    });
    
    private static Map<String, String> libraryAttr(final Module.SingleDependency dependency)
            = libraryAttr(ATTR_MODULE_LIBRARY, dependency.compile(), dependency.runtime());
    
    private static Map<String, String> libraryAttr(final Map<String, String> map, final boolean compile, final boolean runtime)
            = new HashMap<>(map).let(it -> it.put("scope", compile ? runtime ? "COMPILE" : "PROVIDED" : runtime ? "RUNTIME" : "TEST"));
    
    private static void writeLibrary(final Writer writer, final Module.SingleDependency dependency) {
        writeLibrary(writer, TAG_CLASSES, dependency.classes());
        Optional.ofNullable(dependency.javadoc()).ifPresent(javadoc -> writeLibrary(writer, TAG_JAVADOC, javadoc));
        Optional.ofNullable(dependency.sources()).ifPresent(sources -> writeLibrary(writer, TAG_SOURCES, sources));
    }
    
    private static void writeLibrary(final Writer writer, final String tag, final Path path) = writer.visitTag(tag, () -> writeRoot(writer, path));
    
    private static void writeRoot(final Writer writer, final Path path, final @Nullable String next = null) = writer.visitEmptyTag(TAG_ROOT, Map.of(NAME_URL, "jar://%s!/%s".formatted(path | "/", next != null ? next + "/" : "")));
    
    private static void writeLibraries(final Writer writer, final String tag, final Stream<Path> paths, final @Nullable String next = null) = writer.visitTag(tag, () -> paths.forEach(path -> writeRoot(writer, path, next)));
    
    static void generateModules(final Workspace workspace, final Collection<Module> modules) {
        final Writer writer = { };
        writer.visitDeclaration();
        writer.visitTag(TAG_PROJECT, () -> writer.visitTag(TAG_COMPONENT, () -> writer.visitTag(TAG_MODULES, () -> modules.forEach(module -> module.subModules().forEach((name, location) -> {
            final String path = module.path() / (name + IML) | "/";
            writer.visitEmptyTag(TAG_MODULE, Map.of("fileurl", FILE + PROJECT_DIR + path, "filepath", PROJECT_DIR + path));
        }))), ATTR_PROJECT_MODULE_MANAGER), ATTR_PROJECT);
        writer >> ~(workspace.root() / IDEA) / MODULES_XML;
    }
    
    static void generateMisc(final Workspace workspace, final String jdkVersion, final boolean preview = false, final String languageLevel = "JDK_" + jdkVersion, final String jdkType = SDK_JAVA) {
        final Writer writer = { };
        writer.visitDeclaration();
        writer.visitTag(TAG_PROJECT, () -> writer.visitTag(TAG_COMPONENT, () -> writer.visitEmptyTag(TAG_OUTPUT, Map.of(NAME_URL, FILE + PROJECT_DIR + (workspace.output("classes") | "/"))),
                Map.of(
                        NAME_NAME, "ProjectRootManager",
                        NAME_VERSION, "2",
                        "languageLevel", preview ? languageLevel + "_PREVIEW" : languageLevel,
                        "default", "false",
                        "project-jdk-name", jdkVersion,
                        "project-jdk-type", jdkType
                )), ATTR_PROJECT);
        writer >> ~(workspace.root() / IDEA) / MISC_XML;
    }
    
    static void generateLibraries(final Workspace workspace, final Collection<Module> modules)
            = await(modules.stream().map(Module::rawDependencies).flatMap(Collection::stream).cast(Module.DependencySet.class).map(dependencySet -> async(() -> generateLibrary(workspace, dependencySet))));
    
    static void generateLibrary(final Workspace workspace, final Module.DependencySet dependencySet) {
        final Writer writer = { };
        writer.visitDeclaration();
        writer.visitTag(TAG_COMPONENT, () -> writer.visitTag(TAG_LIBRARY, () -> {
            writeLibraries(writer, TAG_CLASSES, dependencySet.flat().map(Module.SingleDependency::classes).distinct());
            if (dependencySet.sameSources())
                dependencySet.flat().map(Module.SingleDependency::classes).distinct().map(Path::fileName)
                        .forEach(next -> writeLibraries(writer, TAG_SOURCES, dependencySet.flat().map(Module.SingleDependency::sources).nonnull().distinct(), next));
            else
                writeLibraries(writer, TAG_SOURCES, dependencySet.flat().map(Module.SingleDependency::sources).nonnull().distinct());
            writeLibraries(writer, TAG_JAVADOC, dependencySet.flat().map(Module.SingleDependency::javadoc).nonnull().distinct());
        }, Map.of(NAME_NAME, dependencySet.name())), ATTR_LIBRARY_TABLE);
        writer >> ~(workspace.root() / IDEA / LIBRARIES) / (dependencySet.name() + SUFFIX);
    }
    
    static void generateAll(final Workspace workspace, final String jdkVersion, final boolean preview = false, final Collection<Module> modules, final Map<Module, ModuleMetadata> metadataMap = Map.of(),
            final String languageLevel = "JDK_" + jdkVersion, final String jdkType = SDK_JAVA)
            = await(Stream.concat(Stream.of(
                    async(() -> generateModules(workspace, modules)),
                    async(() -> generateMisc(workspace, jdkVersion, preview, languageLevel, jdkType)),
                    async(() -> generateLibraries(workspace, modules))),
            modules.stream().map(module -> async(() -> generateIml(workspace, module, metadataMap[module])))));
    
    static void deleteLibraries(final Workspace workspace) = --(workspace.root() / IDEA / LIBRARIES);
    
}
