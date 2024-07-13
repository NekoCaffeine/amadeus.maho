package amadeus.maho.util.build;

import java.io.ByteArrayInputStream;
import java.lang.module.ModuleDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import jdk.internal.module.ModuleInfoExtender;
import jdk.internal.module.ModuleResolution;

import amadeus.maho.core.MahoExport;
import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.Extension;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.inspection.Nullable;

import static amadeus.maho.util.concurrent.AsyncHelper.*;
import static java.util.jar.Attributes.Name.*;

public interface Jar {
    
    @ToString
    @EqualsAndHashCode
    record Agent(String agentClass, boolean canRetransformClasses = true, boolean canRedefineClasses = true, boolean canSetNativeMethodPrefix = false, boolean launcherAgent = true) { }
    
    @ToString
    @EqualsAndHashCode
    record ClassPath(String relatively = "libs/", Collection<Module.SingleDependency> dependencies) {
        
        @Extension.Operator(">>")
        public void copyTo(final Path path) {
            final Path dir = ~(path / relatively());
            dependencies.forEach(dependency -> dependency.classes() >> dir);
        }
        
    }
    
    @ToString
    @EqualsAndHashCode
    record Result(Path modules, @Nullable Path sources = null) { }
    
    String SUFFIX = ".jar", META_INF = "META-INF", MANIFEST_NAME = "MANIFEST.MF", MODULE_INFO = "module-info.class";
    
    String MODULES_DIR = "modules", SOURCES_DIR = "sources";
    
    String DEFAULT_FORMATTER = "${name}-${version}${type}.jar", WITHOUT_VERSION_FORMATTER = "${name}${type}.jar";
    
    Attributes.Name
            PREMAIN_CLASS                = { "Premain-Class" },
            AGENT_CLASS                  = { "Agent-Class" },
            CAN_RETRANSFORM_CLASSES      = { "Can-Retransform-Classes" },
            CAN_REDEFINE_CLASSES         = { "Can-Redefine-Classes" },
            CAN_SET_NATIVE_METHOD_PREFIX = { "Can-Set-Native-Method-Prefix" },
            MAHO_VERSION                 = { "Maho-Version" },
            CREATED_TIME                 = { "Created-Time" },
            LAUNCHER_AGENT_CLASS         = { "Launcher-Agent-Class" },
            TARGET_PLATFORM              = { "Target-Platform" };
    
    BiConsumer<Path, Path> defaultCopier = (a, b) -> a >> b;
    
    static Manifest manifest(final @Nullable String mainClass = null, final @Nullable Agent agent = null, final @Nullable ClassPath classPath = null, final @Nullable String targetPlatform = null) {
        final Manifest manifest = { };
        final Attributes attributes = manifest.getMainAttributes();
        attributes[MANIFEST_VERSION] = "1.0";
        attributes[MAHO_VERSION] = MahoExport.VERSION;
        attributes[CREATED_TIME] = ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
        if (mainClass != null)
            attributes[MAIN_CLASS] = mainClass;
        if (agent != null) {
            attributes[PREMAIN_CLASS] = agent.agentClass();
            attributes[AGENT_CLASS] = agent.agentClass();
            attributes[CAN_RETRANSFORM_CLASSES] = String.valueOf(agent.canRetransformClasses());
            attributes[CAN_REDEFINE_CLASSES] = String.valueOf(agent.canRedefineClasses());
            attributes[CAN_SET_NATIVE_METHOD_PREFIX] = String.valueOf(agent.canSetNativeMethodPrefix());
            if (agent.launcherAgent())
                attributes[LAUNCHER_AGENT_CLASS] = agent.agentClass();
        }
        if (classPath != null)
            attributes[CLASS_PATH] = classPath.dependencies().stream().map(Module.SingleDependency::classes).map(Path::getFileName).map(Path::toString).map(classPath.relatively()::concat).collect(Collectors.joining(" "));
        if (targetPlatform != null)
            attributes[TARGET_PLATFORM] = targetPlatform;
        return manifest;
    }
    
    @SneakyThrows
    static Map<String, Result> pack(final Workspace workspace, final Module module, final Manifest manifest = manifest(), final BiConsumer<Path, Path> copier = defaultCopier, final String format = "${name}-${version}${type}.jar",
            final boolean packSources = true, final ModuleResolution resolution = ModuleResolution.empty()) {
        final Module.Metadata metadata = workspace.config().load(new Module.Metadata(), module.name());
        final Path modulesDir = ~(workspace.root() / workspace.output(MODULES_DIR, module)), classesDir = workspace.root() / workspace.output("classes", module), modulePath = workspace.root() / module.path();
        final ConcurrentHashMap<String, Result> result = { };
        await(module.subModules().entrySet().stream().map(entry -> async(() -> {
            final Path moduleClassesDir = classesDir / entry.getKey(), moduleInfo = moduleClassesDir / MODULE_INFO, moduleSrcDir = modulePath / entry.getValue();
            if (Files.isRegularFile(moduleInfo))
                try (final ByteArrayInputStream input = { Files.readAllBytes(moduleInfo) }) {
                    final ModuleInfoExtender extender = ModuleInfoExtender.newExtender(input);
                    extender.version(ModuleDescriptor.Version.parse(metadata.version));
                    extender.moduleResolution(resolution);
                    final Attributes attributes = manifest.getMainAttributes();
                    Optional.ofNullable(attributes.get(MAIN_CLASS)).map(Object::toString).ifPresent(extender::mainClass);
                    Optional.ofNullable(attributes.get(TARGET_PLATFORM)).map(Object::toString).ifPresent(extender::targetPlatform);
                    try (final var output = Files.newOutputStream(moduleInfo)) { extender.write(output); }
                }
            final String name = format.replace("${name}", entry.getKey()).replace("${version}", metadata.version);
            final Path binary = modulesDir / name.replace("${type}", "");
            modulesDir / name.replace("${type}", "") | root -> {
                Files.walk(moduleSrcDir).filter(Files::isRegularFile).filter(path -> !Javac.javaFileMatcher().matches(path)).forEach(path -> copier.accept(path, root / (moduleSrcDir % path).toString()));
                moduleClassesDir >> root;
                try (final var output = Files.newOutputStream(~(root / META_INF) / MANIFEST_NAME)) { manifest.write(output); }
            };
            final @Nullable Path sources = packSources ? ~(workspace.root() / workspace.output(SOURCES_DIR, module)) / name.replace("${type}", "-sources") : null;
            if (packSources)
                sources | root -> moduleSrcDir >> root;
            final Result pack = { binary, sources };
            result[entry.getKey()] = pack;
        })));
        return result;
    }
    
}
