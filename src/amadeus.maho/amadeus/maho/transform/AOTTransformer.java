package amadeus.maho.transform;

import java.lang.annotation.Annotation;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.nio.zipfs.ZipFileSystem;

import org.objectweb.asm.tree.ClassNode;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.context.TransformContext;
import amadeus.maho.util.function.FunctionHelper;
import amadeus.maho.util.resource.ResourcePath;
import amadeus.maho.util.tuple.Tuple;
import amadeus.maho.util.tuple.Tuple2;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AOTTransformer implements ClassTransformer.Limited {
    
    public enum Level {
        OPEN_WORLD,   // open-world assumptions, usually used to transform jar files
        CLOSED_WORLD, // closed-world assumptions, usually used to make image (jlink tool)
        RUNTIME,      // only in runtime, can't aot
    }
    
    @Getter
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class AOTClassLoader extends URLClassLoader {
        
        protected static final String jmod_suffix = ".jmod";
        
        List<URL> jmods = Stream.of(((Privilege) ucp).getURLs()).filter(url -> url.getFile().endsWith(jmod_suffix)).toList();
        
        public static Predicate<ResourcePath.ResourceTree> excludeJmodResourceTree()
                = resourceTree -> !(resourceTree.domain() instanceof ZipFileSystem zipFileSystem && ((Privilege) zipFileSystem.getZipFile()).getFileName().toString().endsWith(jmod_suffix));
        
    }
    
    @SneakyThrows
    public static void transform(final Path sourceDir, final Path targetDir)
            = transform(Files.walk(sourceDir).filter(path -> path.toString().endsWith(".jar")).collect(Collectors.toMap(Function.identity(), path -> targetDir / (sourceDir % path).toString())));
    
    @SneakyThrows
    public static void transform(final Map<Path, Path> files) {
        final TransformerManager manager = { "aot-t" };
        try (final AOTClassLoader loader = { files.keySet().stream().map(Path::toUri).map(URI::toURL).toArray(URL[]::new) }) {
            manager.setup(loader, ResourcePath.of(loader), Level.OPEN_WORLD, files.keySet().stream().map(Path::toString).collect(Collectors.joining(",\n    ", "AOT [\n    ", "\n]")));
            files.forEach((a, b) -> a | pa -> b | pb -> pa.projection(pb, (source, target) -> manager.aot(source, target, loader)));
        }
    }
    
    String target;
    
    Set<Class<? extends Annotation>> annotationTypes = new HashSet<>();
    
    Map<Tuple2<String, String>, Set<Class<? extends Annotation>>> fields = new ConcurrentHashMap<>(), methods = new ConcurrentHashMap<>();
    
    public void addClassAnnotation(final Class<? extends Annotation> annotationType) = annotationTypes() += annotationType;
    
    public void addFieldAnnotation(final String name, final String desc, final Class<? extends Annotation> annotationType) = fields().computeIfAbsent(Tuple.tuple(name, desc), FunctionHelper.abandon(HashSet::new)) += annotationType;
    
    public void addMethodAnnotation(final String name, final String desc, final Class<? extends Annotation> annotationType) = methods().computeIfAbsent(Tuple.tuple(name, desc), FunctionHelper.abandon(HashSet::new)) += annotationType;
    
    @Override
    public Set<String> targets() = Set.of(target);
    
    @Override
    public ClassNode transform(final TransformContext context, final ClassNode node, final @Nullable ClassLoader loader, final @Nullable Class<?> clazz, final @Nullable ProtectionDomain domain) {
        context.markModified();
        ASMHelper.delAllAnnotation(node, annotationTypes);
        fields().forEach((desc, annotationTypes) -> ASMHelper.lookupFieldNode(node, desc.v1, desc.v2).ifPresent(fieldNode -> ASMHelper.delAnnotation(fieldNode, annotationTypes)));
        methods().forEach((desc, annotationTypes) -> ASMHelper.lookupMethodNode(node, desc.v1, desc.v2).ifPresent(methodNode -> ASMHelper.delAnnotation(methodNode, annotationTypes)));
        return node;
    }
    
}
