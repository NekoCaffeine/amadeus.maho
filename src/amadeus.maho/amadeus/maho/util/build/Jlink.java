package amadeus.maho.util.build;

import java.lang.annotation.Annotation;
import java.lang.instrument.ClassFileTransformer;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.net.URL;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import jdk.internal.module.ModulePath;
import jdk.tools.jlink.builder.DefaultImageBuilder;
import jdk.tools.jlink.internal.Jlink.JlinkConfiguration;
import jdk.tools.jlink.internal.Jlink.PluginsConfiguration;
import jdk.tools.jlink.plugin.Plugin;
import jdk.tools.jlink.plugin.ResourcePool;
import jdk.tools.jlink.plugin.ResourcePoolBuilder;
import jdk.tools.jlink.plugin.ResourcePoolEntry;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.ModuleOpenNode;

import amadeus.maho.core.bootstrap.HookResultInjector;
import amadeus.maho.core.bootstrap.ReflectionInjector;
import amadeus.maho.core.bootstrap.UnsafeInjector;
import amadeus.maho.core.extension.AllClassesPublic;
import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Delegate;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.AOTTransformer;
import amadeus.maho.transform.TransformerManager;
import amadeus.maho.transform.handler.base.BaseTransformer;
import amadeus.maho.transform.handler.base.FieldTransformer;
import amadeus.maho.transform.handler.base.MethodTransformer;
import amadeus.maho.transform.mark.base.TransformMark;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.transform.mark.base.Transformer;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.ClassWriter;
import amadeus.maho.util.container.MapTable;
import amadeus.maho.util.function.FunctionHelper;
import amadeus.maho.util.misc.Environment;
import amadeus.maho.util.resource.ResourcePath;
import amadeus.maho.util.runtime.ArrayHelper;

import static amadeus.maho.transform.AOTTransformer.Level.CLOSED_WORLD;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;

public interface Jlink {
    
    String MAHO_LINK_CONTEXT = "amadeus.maho.link.context";
    
    @Getter
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    class MahoImageTransformPlugin implements Plugin {
        
        AOTTransformer.AOTClassLoader loader;
        
        ResourcePath path = ResourcePath.of(loader());
        
        HashMap<Object, Object> properties = { System.getProperties() };
        
        Environment environment = this::properties;
        
        { environment()[MAHO_LINK_CONTEXT] = true; }
        
        TransformerManager manager = { "jink-t", environment() };
        
        { manager().setup(loader, path, CLOSED_WORLD, "maho-image"); }
        
        List<ClassFileTransformer> transformers = new ArrayList<>(List.of(manager(), AllClassesPublic.instance(), ReflectionInjector.instance(), UnsafeInjector.instance(), HookResultInjector.instance()));
        
        MapTable<String, String, Set<String>> moduleOpensTable = MapTable.newHashMapTable();
        
        List<String> changedClasses = new ArrayList<>();
        
        public void open(final String moduleName, final String packageName, final String... targets)
                = moduleOpensTable().row(moduleName).computeIfAbsent(packageName.replace('.', '/'), FunctionHelper.abandon(HashSet::new)) *= List.of(targets);
        
        @Override
        @SneakyThrows
        public ResourcePool transform(final ResourcePool in, final ResourcePoolBuilder out) {
            final ArrayList<String> marks = { }, providers = { };
            in.transformAndCopy(resource -> {
                if (resource.type() == ResourcePoolEntry.Type.CLASS_OR_RESOURCE && resource.path().endsWith(Javac.CLASS_SUFFIX)) {
                    final String path = resource.path();
                    byte bytes[] = resource.contentBytes();
                    if (path.endsWith(Jar.MODULE_INFO)) {
                        final String moduleName = path.substring(1, path.indexOf('/', 1));
                        final @Nullable Map<String, Set<String>> opensMap = moduleOpensTable()[moduleName];
                        if (opensMap != null) {
                            final ClassNode node = ASMHelper.newClassNode(bytes);
                            opensMap.forEach((packageName, targets) -> {
                                final ModuleOpenNode openNode = { packageName, ACC_SYNTHETIC, targets.stream().toList() };
                                node.module.opens ?? (node.module.opens = new ArrayList<>()) += openNode;
                            });
                            bytes = ClassWriter.toBytecode(node::accept);
                        }
                    } else {
                        final int nameStartIndex = path.indexOf('/', 1) + 1;
                        final String className = path.substring(nameStartIndex, path.indexOf('.', nameStartIndex));
                        for (final ClassFileTransformer transformer : transformers)
                            bytes = transformer.transform(null, loader, className, null, null, bytes) ?? bytes;
                        if (!ArrayHelper.equals(bytes, resource.contentBytes()))
                            changedClasses += className;
                        {
                            final ClassNode node = ASMHelper.newClassNode(bytes);
                            if (isMark(node))
                                marks += className.replace('/', '.');
                            if (isProvider(node))
                                providers += className.replace('/', '.');
                        }
                    }
                    return resource.copyWithContent(bytes);
                }
                return resource;
            }, out);
            out.add(ResourcePoolEntry.create("/amadeus.maho/transform-marks", ResourcePoolEntry.Type.CLASS_OR_RESOURCE, String.join("\n", marks).getBytes(StandardCharsets.UTF_8)));
            out.add(ResourcePoolEntry.create("/amadeus.maho/transform-providers", ResourcePoolEntry.Type.CLASS_OR_RESOURCE, String.join("\n", providers).getBytes(StandardCharsets.UTF_8)));
            out.add(ResourcePoolEntry.create("/java.base/changed-classes", ResourcePoolEntry.Type.TOP, String.join("\n", changedClasses()).getBytes(StandardCharsets.UTF_8)));
            return out.build();
        }
        
        protected boolean isMark(final ClassNode node) = ASMHelper.hasAnnotation(node, TransformMark.class);
        
        protected boolean isProvider(final ClassNode node) {
            if (ASMHelper.hasAnnotation(node, Transformer.class))
                return true;
            final @Nullable Map<Class<? extends Annotation>, Class<? extends BaseTransformer>> base = manager.transformerTable()[BaseTransformer.class];
            if (base != null && !ASMHelper.hasAnnotation(node, TransformProvider.Exception.class) && base.keySet().stream().anyMatch(annotationType -> ASMHelper.hasAnnotation(node, annotationType)))
                return true;
            return base != null && !ASMHelper.hasAnnotation(node, TransformProvider.Exception.class) && base.keySet().stream().anyMatch(annotationType -> ASMHelper.hasAnnotation(node, annotationType)) ||
                    ASMHelper.hasAnnotation(node, TransformProvider.class) && (
                            manager.transformerTable()[FieldTransformer.class]?.keySet().stream().anyMatch(annotationType -> node.fields.stream().anyMatch(fieldNode -> ASMHelper.hasAnnotation(fieldNode, annotationType))) ?? false ||
                                    manager.transformerTable()[MethodTransformer.class]?.keySet().stream().anyMatch(annotationType -> node.methods.stream().anyMatch(methodNode -> ASMHelper.hasAnnotation(methodNode, annotationType))) ?? false);
        }
        
    }
    
    @Getter(on = @Delegate)
    jdk.tools.jlink.internal.Jlink instance = { };
    
    @SneakyThrows
    static Path createMahoImage(final Workspace workspace, final Module module, final Path output = workspace.output("maho-image")) {
        final LinkedList<Path> paths = { };
        final Path jmods = Path.of(System.getProperty("java.home"), "jmods");
        paths *= Files.list(jmods).toList();
        paths *= Files.list(workspace.output(Jar.MODULES_DIR)).toList();
        paths *= module.dependencies().stream().map(Module.SingleDependency::classes).toList();
        final ModuleFinder finder = ModulePath.of(Runtime.version(), true, paths.toArray(new Path[0]));
        final Set<String> modules = finder.findAll().stream().map(ModuleReference::descriptor).map(ModuleDescriptor::name).collect(Collectors.toUnmodifiableSet());
        final JlinkConfiguration jlinkConfiguration = { output, modules, ByteOrder.nativeOrder(), finder };
        try (final AOTTransformer.AOTClassLoader loader = { paths.stream().map(Path::toUri).map(URI::toURL).toArray(URL[]::new) }) {
            final MahoImageTransformPlugin mahoImageTransformPlugin = { loader };
            mahoImageTransformPlugin.open("java.instrument", "sun.instrument", "amadeus.maho");
            final List<Plugin> plugins = List.of(mahoImageTransformPlugin);
            final Map<String, String> launchers = Map.of("maho", "amadeus.maho");
            final DefaultImageBuilder builder = { output, launchers };
            final PluginsConfiguration pluginsConfiguration = { plugins, builder, null };
            build(jlinkConfiguration, pluginsConfiguration);
            launchers.keySet().forEach(prototype -> derive(output, prototype, name -> STR."\{name}dbg", List.of(
                    "-XX:+UnlockDiagnosticVMOptions",
                    "-XX:+ShowHiddenFrames",
                    "-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=localhost:36768"
            )));
            final Path outputMods = output / "jmods";
            jmods >> outputMods;
            workspace.output(Jar.MODULES_DIR) >> outputMods;
            module.dependencies().stream().map(Module.SingleDependency::classes).forEach(it -> it >> outputMods);
        }
        return output;
    }
    
    static void derive(final Path root, final String prototype, final UnaryOperator<String> nameAdder, final List<String> args) {
        final Path bin = root / DefaultImageBuilder.BIN_DIRNAME;
        final String derivedName = nameAdder.apply(prototype);
        derive(bin / prototype, bin / derivedName, args);
        derive(bin / (STR."\{prototype}.bat"), bin / (STR."\{derivedName}.bat"), args);
    }
    
    String JLINK_VM_OPTIONS_PATTERN = "JLINK_VM_OPTIONS=";
    
    @SneakyThrows
    static void derive(final Path prototype, final Path derived, final List<String> args) {
        if (Files.isRegularFile(prototype))
            (Files.readString(prototype).replace(JLINK_VM_OPTIONS_PATTERN, JLINK_VM_OPTIONS_PATTERN + String.join(" ", args)) >> derived).markExecutable();
    }
    
}
