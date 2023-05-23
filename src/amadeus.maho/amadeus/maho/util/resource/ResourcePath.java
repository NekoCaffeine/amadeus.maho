package amadeus.maho.util.resource;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.nio.zipfs.ZipFileSystem;

import amadeus.maho.core.Maho;
import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.control.FunctionChain;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ResourcePath implements Closeable {
    
    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class ResourceTree implements Closeable {
        
        protected static final String JMOD_SUFFIX = ".jmod";
        
        final Path root;
        
        @Nullable FileSystem domain;
        
        final boolean jmod ;
        
        final UnaryOperator<Path> classesRedirect;
        
        protected ResourceTree(final Path root) {
            this.root = checkZipFile(root);
            jmod = domain() instanceof ZipFileSystem && root.getFileName().toString().endsWith(JMOD_SUFFIX);
            classesRedirect = jmod() ? it -> it / "classes" : UnaryOperator.identity();
        }
        
        @SneakyThrows
        public Stream<ResourceInfo> nodes(final UnaryOperator<Path> redirect = UnaryOperator.identity()) {
            final Path root = redirect.apply(root());
            return Files.walk(root).filter(path -> path.getFileName() != null).map(path -> ResourceInfo.of(root, path));
        }
        
        public Stream<ClassInfo> classes() = nodes(classesRedirect()).cast(ClassInfo.class);
        
        public @Nullable ResourceInfo findResource(final String name) {
            final Path path = root / name;
            return Files.isRegularFile(path) ? new ResourceInfo(root, path) : null;
        }
        
        public @Nullable ClassInfo findClassInfo(final String name) {
            final Path root = classesRedirect().apply(root()), path = root / (name.replace('.', '/') + ".class");
            return Files.isRegularFile(path) ? new ClassInfo(root, path) : null;
        }
        
        public @Nullable ClassInfo findModuleInfo() = findClassInfo("module-info");
        
        protected Path checkZipFile(final Path path) {
            if (Files.isReadable(path))
                try (final DataInputStream inputStream = { Files.newInputStream(path) }) {
                    if (inputStream.available() < Integer.BYTES)
                        return path;
                    final int signature = inputStream.readInt();
                    if (signature == 0x504B0304 || signature == 0x504B0506 || signature == 0x4A4D0100) // check zip or jmod file magic number
                        return (domain = path.zipFileSystem(Map.of("readonly", "true"))).getPath("");
                } catch (final IOException ignored) { }
            return path;
        }
        
        @Override
        public void close() throws IOException {
            if (domain != null)
                domain.close();
        }
        
        @Override
        public String toString() {
            final FileSystem domain = domain();
            return "%s | %s".formatted(domain == null ? "default" : domain.toString(), root());
        }
        
        @SneakyThrows
        public static @Nullable ResourceTree of(final URL url) {
            try { return { Path.of((url.openConnection() instanceof JarURLConnection jarURLConnection ? jarURLConnection.getJarFileURL() : url).toURI()) }; } catch (final URISyntaxException e) { return null; }
        }
        
        public static ResourceTree of(final Path path) = { path };
        
    }
    
    @Getter
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class ResourceInfo {
        
        Path root, path;
        
        public String fullyName() = path().getFileName().toString();
        
        public String name() = path().fileName();
        
        public @Nullable String extensionName() = path().extensionName();
        
        public byte[] readAll() throws IOException = Files.readAllBytes(path());
        
        public InputStream inputStream() throws IOException = Files.newInputStream(path());
        
        public static ResourceInfo of(final Path root, final Path path) {
            if (path.getFileName().toString().endsWith(".class"))
                return new ClassInfo(root, path);
            return { root, path };
        }
        
        @Override
        public String toString() {
            final FileSystem domain = path().getFileSystem();
            return "%s | %s".formatted(domain == null ? "default" : domain.toString(), root() / path());
        }
        
    }
    
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class ClassInfo extends ResourceInfo {
        
        public String className() {
            final String path = (root() % path()).toString().replace(path().getFileSystem().getSeparator(), ".");
            return path.substring(0, path.length() - ".class".length());
        }
        
        public String packageName() {
            final String path = className();
            final int slash = path.lastIndexOf('.');
            return slash == -1 ? "" : path.substring(0, slash);
        }
        
        public Class<?> load(final boolean initialize, final @Nullable ClassLoader loader) throws IOException {
            try { return Class.forName(className(), initialize, loader); } catch (final ClassNotFoundException e) { return Maho.shareClass(className(), readAll(), loader); }
        }
        
    }
    
    @Getter
    private static final FunctionChain<ClassLoader, URL[]> classLoaderMapperChain = new FunctionChain<ClassLoader, URL[]>()
            .add(target -> target
                    .cast(URLClassLoader.class)
                    .map(URLClassLoader::getURLs));
    
    @Getter
    private static final FunctionChain<Class<?>, URL> classMapperChain = new FunctionChain<Class<?>, URL>()
            .add(target -> target
                    .map(ResourcePath::lookupCodeSource)
                    .map(CodeSource::getLocation));
    
    @Getter
    @Default
    List<ResourceTree> trees = new ArrayList<>();
    
    public void addResourceTree(final @Nullable ResourceTree tree) = Optional.ofNullable(tree).ifPresent(trees()::add);
    
    public ResourcePath sub(final Predicate<ResourceTree> predicate) = { trees().stream().filter(predicate).collect(Collectors.toList()) };
    
    public Stream<ResourceInfo> resources() = trees().stream().flatMap(ResourceTree::nodes);
    
    public Stream<ClassInfo> classes() = trees().stream().flatMap(ResourceTree::classes);
    
    public Stream<ClassInfo> topLevelClasses() = classes().filter(info -> info.name().indexOf('$') == -1);
    
    public Stream<Path> traverse(final Path path) = trees().stream().map(ResourceTree::root).map(root -> root / path.toString()).filter(Files::isReadable);
    
    @Override
    @SneakyThrows
    public void close() = trees().forEach(AutoCloseable::close);
    
    public static @Nullable CodeSource lookupCodeSource(final Class<?> target) {
        final ProtectionDomain domain = target.getProtectionDomain();
        return domain == null ? null : domain.getCodeSource();
    }
    
    public static ResourcePath of(final @Nullable ClassLoader loader) {
        final ResourcePath result = { };
        classLoaderMapperChain().apply(loader)
                .map(Stream::of)
                .ifPresent(stream -> stream
                        .map(ResourceTree::of)
                        .forEach(result::addResourceTree));
        return result;
    }
    
    public static ResourcePath of(final @Nullable Class<?> target) {
        final ResourcePath result = { };
        classMapperChain().apply(target)
                .map(ResourceTree::of)
                .ifPresent(result::addResourceTree);
        return result;
    }
    
    public static ResourcePath of(final @Nullable Class<?>... target) {
        final ResourcePath result = { };
        Stream.of(target).forEach(it -> classMapperChain().apply(it)
                .map(ResourceTree::of)
                .ifPresent(result::addResourceTree));
        return result;
    }
    
    public static ResourcePath of(final @Nullable Path path) {
        final ResourcePath result = { };
        Optional.ofNullable(path)
                .map(ResourceTree::of)
                .ifPresent(result::addResourceTree);
        return result;
    }
    
}
