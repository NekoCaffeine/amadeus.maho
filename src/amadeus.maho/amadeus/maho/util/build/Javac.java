package amadeus.maho.util.build;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.security.ProtectionDomain;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.NoSuchElementException;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.tools.DiagnosticListener;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import jdk.internal.misc.VM;

import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.resources.LauncherProperties;
import com.sun.tools.javac.resources.LauncherProperties.Errors;
import com.sun.tools.javac.util.JCDiagnostic;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Delegate;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.function.FunctionHelper;

public interface Javac {
    
    String
            JAVA_SUFFIX  = ".java",
            CLASS_SUFFIX = ".class",
            MODULE_INFO  = "module-info",
            PACKAGE_INFO = "package-info";
    
    String CLASSES_DIR = "classes", SESSION = "javac.session";
    
    class Failure extends Exception {
        
        private static final ResourceBundle resourceBundle = ResourceBundle.getBundle("com.sun.tools.javac.resources.launcher");
        
        public Failure(final JCDiagnostic.Error error) = super(message(error));
        
        private static String message(final JCDiagnostic.Error error) {
            try {
                return resourceBundle.getString("launcher.error") + MessageFormat.format(resourceBundle.getString(error.key()), error.getArgs());
            } catch (final MissingResourceException e) { return "Cannot access resource; " + error.key() + Arrays.toString(error.getArgs()); }
        }
        
    }
    
    @Getter
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    class InMemoryContext {
        
        HashMap<String, byte[]> inMemoryClasses = { };
        
        public MemoryFileManager manager(final StandardJavaFileManager delegate) = { delegate, inMemoryClasses };
        
        public MemoryClassLoader loader(final ClassLoader parent) = { parent, inMemoryClasses };
        
    }
    
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    class MemoryFileManager extends ForwardingJavaFileManager<JavaFileManager> {
        
        Map<String, byte[]> map;
        
        @Override
        public JavaFileObject getJavaFileForOutput(final JavaFileManager.Location location, final String className, final JavaFileObject.Kind kind, final FileObject sibling) throws IOException
                = location == StandardLocation.CLASS_OUTPUT && kind == JavaFileObject.Kind.CLASS ? createInMemoryClassFile(className) : super.getJavaFileForOutput(location, className, kind, sibling);
        
        private JavaFileObject createInMemoryClassFile(final String className) = new SimpleJavaFileObject(URI.create("memory:///" + className.replace('.', '/') + ".class"), JavaFileObject.Kind.CLASS) {
            
            @Override
            public OutputStream openOutputStream() = new ByteArrayOutputStream() {
                
                @Override
                public void close() throws IOException {
                    super.close();
                    map.put(className, toByteArray());
                }
                
            };
            
        };
        
    }
    
    @Getter
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    class MemoryClassLoader extends ClassLoader {
        
        @RequiredArgsConstructor
        @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
        private static class MemoryURLStreamHandler extends URLStreamHandler {
            
            @RequiredArgsConstructor
            @FieldDefaults(level = AccessLevel.PRIVATE)
            private static class Connection extends URLConnection {
                
                final byte[] bytes;
                
                @Nullable InputStream input;
                
                @Override
                public void connect() throws IOException {
                    if (!connected) {
                        if (bytes == null)
                            throw new FileNotFoundException(getURL().getPath());
                        input = new ByteArrayInputStream(bytes);
                        connected = true;
                    }
                }
                
                @Override
                public InputStream getInputStream() throws IOException {
                    connect();
                    return input;
                }
                
                @Override
                public long getContentLengthLong() = bytes.length;
                
                @Override
                public String getContentType() = "application/octet-stream";
                
            }
            
            MemoryClassLoader loader;
            
            @Override
            public MemoryURLStreamHandler.Connection openConnection(final URL url) {
                if (!url.getProtocol().equalsIgnoreCase("memory"))
                    throw new IllegalArgumentException(url.toString());
                return { url, loader.sourceFileClasses.get(binaryName(url.getPath())) };
            }
            
        }
        
        MemoryURLStreamHandler handler = { this };
        
        Map<String, byte[]> sourceFileClasses;
        
        @Override
        protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    loadedClass = sourceFileClasses.containsKey(name) ? findClass(name) : getParent().loadClass(name);
                    if (resolve)
                        resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }
        
        @Override
        public URL getResource(final String name) = sourceFileClasses.containsKey(binaryName(name)) ? findResource(name) : getParent().getResource(name);
        
        @Override
        public Enumeration<URL> getResources(final String name) throws IOException {
            final URL url = findResource(name);
            final Enumeration<URL> enumeration = getParent().getResources(name);
            if (url == null)
                return enumeration;
            else {
                final List<URL> list = new ArrayList<>();
                list += url;
                while (enumeration.hasMoreElements())
                    list += enumeration.nextElement();
                return Collections.enumeration(list);
            }
        }
        
        @Override
        protected Class<?> findClass(final String name) throws ClassNotFoundException {
            final byte bytes[] = sourceFileClasses.get(name);
            if (bytes == null)
                throw new ClassNotFoundException(name);
            return defineClass(name, bytes, 0, bytes.length, new ProtectionDomain(null, null, this, null));
        }
        
        @Override
        public @Nullable URL findResource(final String name) {
            final @Nullable String binaryName = binaryName(name);
            if (binaryName == null || sourceFileClasses[binaryName] == null)
                return null;
            try { return { "memory", null, -1, name, handler }; } catch (final MalformedURLException e) { return null; }
        }
        
        @Override
        public Enumeration<URL> findResources(final String name) = new Enumeration<>() {
            
            private @Nullable URL next = findResource(name);
            
            @Override
            public boolean hasMoreElements() = next != null;
            
            @Override
            public URL nextElement() {
                if (next == null)
                    throw new NoSuchElementException();
                try { return next; } finally { next = null; }
            }
            
        };
        
        private static @Nullable String binaryName(final String name) = !name.endsWith(".class") ? null : name.substring(0, name.length() - 6).replace('/', '.');
        
    }
    
    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    class ClassesNameListener implements TaskListener {
        
        ConcurrentLinkedQueue<String> collection = { };
        
        public ClassesNameListener(final JavacTask task) = task.addTaskListener(this);
        
        @Override
        public void started(final TaskEvent event) {
            if (event.getKind() == TaskEvent.Kind.ANALYZE) {
                final TypeElement element = event.getTypeElement();
                if (element.getNestingKind() == NestingKind.TOP_LEVEL)
                    collection += element.getQualifiedName().toString();
            }
        }
        
    }
    
    @Getter(on = @Delegate)
    JavacTool instance = JavacTool.create();
    
    @Getter
    PathMatcher javaFileMatcher = FileSystems.getDefault().getPathMatcher("glob:**.java");
    
    @Getter
    List<String> unsupportedModuleWildcards = List.of("ALL-DEFAULT", "ALL-UNNAMED");
    
    static List<String> runtimeOptions(final boolean addAllModules = true, final String... runtimeArgs = VM.getRuntimeArguments()) throws Failure {
        final ArrayList<String> javacOpts = { }, needAddModules = { List.of("ALL-MODULE-PATH", "ALL-SYSTEM") };
        for (int i = 0; i < runtimeArgs.length; i++) {
            final String arg = runtimeArgs[i];
            String opt = arg, value = null;
            if (arg.startsWith("--")) {
                final int eq = arg.indexOf('=');
                if (eq > 0) {
                    opt = arg.substring(0, eq);
                    value = arg.substring(eq + 1);
                }
            }
            switch (opt) { // the following options all expect a value, either in the following position, or after '=', for options beginning "--"
                case "--add-exports",
                        "--add-modules",
                        "--limit-modules",
                        "--patch-module",
                        "--upgrade-module-path" -> {
                    if (value == null) {
                        if (i == runtimeArgs.length - 1) // should not happen when invoked from launcher
                            throw new Failure(LauncherProperties.Errors.NoValueForOption(opt));
                        value = runtimeArgs[++i];
                    }
                    if (opt.equals("--add-modules")) {
                        if (unsupportedModuleWildcards.contains(value)) // this option is only supported at runtime, it is not required or supported at compile time
                            break;
                        needAddModules -= value;
                    }
                    javacOpts += opt;
                    javacOpts += value;
                }
            }
        }
        final @Nullable String p = System.getProperty("jdk.module.path");
        if (!p.isEmptyOrNull()) {
            javacOpts += "-p";
            javacOpts += p;
        }
        final @Nullable String cp = System.getProperty("java.class.path");
        if (!cp.isEmptyOrNull()) {
            javacOpts += "-cp";
            javacOpts += cp;
        }
        if (addAllModules)
            needAddModules.forEach(module -> {
                javacOpts += "--add-modules";
                javacOpts += module;
            });
        return javacOpts;
    }
    
    @SneakyThrows
    static void compile(final Collection<Path> paths, final List<String> options, final Charset charset = StandardCharsets.UTF_8, final Locale locale = Locale.getDefault(), final Consumer<JavacTask> worker = FunctionHelper.abandon(),
            final Function<JavacFileManager, JavaFileManager> mapper = self -> self, final DiagnosticListener<? super JavaFileObject> listener = null, final PrintWriter writer = { new OutputStreamWriter(System.out), true }) throws Failure {
        final JavacFileManager manager = getStandardFileManager(listener, locale, charset);
        if (!getTask(writer, mapper.apply(manager), listener, options, null, manager.getJavaFileObjectsFromPaths(paths)).let(worker).call())
            throw new Failure(Errors.CompilationFailed);
    }
    
    Predicate<Path> hasModuleInfo = path -> path ^ root -> Files.exists(root / (MODULE_INFO + CLASS_SUFFIX));
    
    @SneakyThrows
    static Path compile(final Workspace workspace, final Module module, final Predicate<Path> useModulePath = hasModuleInfo, final Consumer<List<String>> argsTransformer = FunctionHelper.abandon(),
            final @Nullable Path moduleSourcePath = workspace.root() / module.path() / "src", final Predicate<String> shouldCompile = _ -> true,
            final Charset charset = StandardCharsets.UTF_8, final Locale locale = Locale.getDefault()) throws Failure {
        final Path classesDir = workspace.root() / workspace.output(CLASSES_DIR, module);
        final ArrayList<Path> p = { }, cp = { };
        module.dependencies().stream()
                .filter(Module.SingleDependency::compile)
                .map(Module.SingleDependency::classes)
                .forEach(path -> useModulePath.test(path) ? p : cp += path);
        module.dependencyModules().stream()
                .flatMap(dependencyModule -> dependencyModule.subModules().keySet().stream().map(name -> workspace.output(CLASSES_DIR, dependencyModule) / name))
                .forEach(path -> useModulePath.test(path) ? p : cp += path);
        final ArrayList<String> args = { };
        args += "-encoding";
        args += charset.displayName();
        if (p.nonEmpty()) {
            args += "-p";
            args += paths(p);
        }
        if (cp.nonEmpty()) {
            args += "-cp";
            args += paths(cp);
        }
        if (moduleSourcePath != null) {
            args += "--module-source-path";
            args += moduleSourcePath.toAbsolutePath().toString();
            args += "-d";
            args += classesDir.toAbsolutePath().toString();
            argsTransformer.accept(args);
            compile(module.subModules().entrySet().stream()
                    .filter(entry -> shouldCompile.test(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .map((workspace.root() / module.path())::resolve)
                    .flatMap(Files::walk)
                    .distinct()
                    .filter(javaFileMatcher()::matches)
                    .toList(), args, charset, locale);
        } else {
            argsTransformer.accept(args);
            module.subModules().entrySet().stream()
                    .filter(entry -> shouldCompile.test(entry.getKey()))
                    .forEach(entry -> compile(Files.walk(workspace.root() / module.path() / entry.getValue()).filter(javaFileMatcher()::matches).toList(), new ArrayList<String>(args.size() + 2).let(fork -> {
                        fork *= args;
                        fork += "-d";
                        fork += (classesDir / entry.getKey()).toAbsolutePath().toString();
                    }), charset, locale));
        }
        "" >> classesDir / SESSION;
        return classesDir;
    }
    
    static String paths(final Collection<Path> p) = p.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
    
    static void addReadsAllUnnamed(final Collection<String> args, final Module... modules) = Stream.of(modules).forEach(module -> {
        args += "--add-reads";
        args += module.name() + "=ALL-UNNAMED";
    });
    
}
