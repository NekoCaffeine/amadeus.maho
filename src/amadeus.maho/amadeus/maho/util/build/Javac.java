package amadeus.maho.util.build;

import java.io.File;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

import jdk.internal.misc.VM;

import com.sun.source.util.JavacTask;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.tools.javac.api.MultiTaskListener;
import com.sun.tools.javac.file.BaseFileManager;
import com.sun.tools.javac.file.CacheFSInfo;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.jvm.Target;
import com.sun.tools.javac.main.Arguments;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.main.Option;
import com.sun.tools.javac.resources.LauncherProperties;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.Log;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.javac.multithreaded.dispatch.DispatchCompiler;
import amadeus.maho.lang.javac.multithreaded.dispatch.DispatchContext;
import amadeus.maho.util.function.FunctionHelper;
import amadeus.maho.util.runtime.DebugHelper;

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
        
        public Failure(final JCDiagnostic.Error error, final Throwable throwable) = super(message(error), throwable);
        
        private static String message(final JCDiagnostic.Error error) {
            try {
                return resourceBundle.getString("launcher.error") + MessageFormat.format(resourceBundle.getString(error.key()), error.getArgs());
            } catch (final MissingResourceException e) { return STR."Cannot access resource; \{error.key()}\{Arrays.toString(error.getArgs())}"; }
        }
        
    }
    
    record Request(Collection<Path> paths, List<String> options, Locale locale = Locale.getDefault(), Charset charset = StandardCharsets.UTF_8,
                   @Nullable DiagnosticListener<? super JavaFileObject> listener = null, PrintWriter writer = { new OutputStreamWriter(System.out), true }) {
        
        public <C extends Context> C generateCompileContext(final C context, final boolean cacheFSInfo = true) {
            context.put(Locale.class, locale);
            if (listener != null)
                context.put(DiagnosticListener.class, listener);
            context.put(Log.errKey, writer);
            if (cacheFSInfo)
                CacheFSInfo.preRegister(context);
            final JavacFileManager fileManager = { context, true, charset };
            fileManager.autoClose = true;
            context.put(JavaFileManager.class, fileManager);
            final Arguments args = Arguments.instance(context);
            args.init("javac", options, List.of(), fileManager.getJavaFileObjectsFromPaths(paths));
            if (fileManager.isSupportedOption(Option.MULTIRELEASE.primaryName) == 1)
                fileManager.handleOption(Option.MULTIRELEASE.primaryName, List.of(Target.instance(context).multiReleaseValue()).iterator());
            return context;
        }
        
        public CompileTask.Parallel parallelCompileTask(final boolean cacheFSInfo = true) = { this, generateCompileContext(new DispatchContext()) };
        
        public CompileTask.Serial serialCompileTask(final boolean cacheFSInfo = true) = { this, generateCompileContext(new Context()) };
        
        public CompileTask compileTask(final boolean parallel = true, final boolean cacheFSInfo = true) = parallel ? parallelCompileTask(cacheFSInfo) : serialCompileTask(cacheFSInfo);
        
        @SneakyThrows
        public void compile(final boolean parallel = true, final boolean cacheFSInfo = true) throws Javac.Failure {
            try (final CompileTask<?> compileTask = compileTask(parallel, cacheFSInfo)) { DebugHelper.logTimeConsuming(parallel ? "parallel-compile" : "serial-compile", compileTask::compile); }
        }
        
    }
    
    sealed interface CompileTask<C extends JavaCompiler> extends AutoCloseable {
        
        record Parallel(Request request, DispatchContext context) implements CompileTask<DispatchCompiler> {
            
            @Override
            public DispatchCompiler compiler() = DispatchCompiler.instance(context());
            
        }
        
        record Serial(Request request, Context context) implements CompileTask<JavaCompiler> {
            
            @Override
            public JavaCompiler compiler() = JavaCompiler.instance(context());
            
        }
        
        private static com.sun.tools.javac.util.List<JavaFileObject> sources(final Context context, final Request request)
                = ((JavacFileManager) context.get(JavaFileManager.class)).getJavaFileObjectsFromPaths(request.paths).fromIterable().collect(com.sun.tools.javac.util.List.collector());
        
        Request request();
        
        Context context();
        
        C compiler();
        
        default void compile() throws Javac.Failure {
            final Context context = context();
            try {
                compiler().compile(sources(context, request()));
                if (compiler().errorCount() > 0)
                    throw new Javac.Failure(LauncherProperties.Errors.CompilationFailed);
            } catch (final Throwable throwable) { throw new Javac.Failure(LauncherProperties.Errors.CompilationFailed, throwable); }
        }
        
        @Override
        default void close() throws Exception {
            final Context context = context();
            JavaCompiler.instance(context)?.close();
            if (context.get(JavaFileManager.class) instanceof BaseFileManager baseFileManager && baseFileManager.autoClose)
                baseFileManager.close();
        }
        
    }
    
    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    class ClassesNameListener implements TaskListener {
        
        ConcurrentLinkedQueue<String> names = { };
        
        public ClassesNameListener(final JavacTask task) = task.addTaskListener(this);
        
        public ClassesNameListener(final Context context) = MultiTaskListener.instance(context).add(this);
        
        @Override
        public void started(final TaskEvent event) {
            if (event.getKind() == TaskEvent.Kind.ANALYZE) {
                final TypeElement element = event.getTypeElement();
                if (element.getNestingKind() == NestingKind.TOP_LEVEL)
                    names += element.getQualifiedName().toString();
            }
        }
        
    }
    
    AtomicReference<Boolean> parallelStrategy = { };
    
    {
        final @Nullable String strategy = System.getProperty("amadeus.maho.compile.parallel");
        if (strategy != null)
            parallelStrategy.set(Boolean.parseBoolean(strategy));
    }
    
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
    static void compile(final Collection<Path> paths, final List<String> options, final Charset charset = StandardCharsets.UTF_8, final Locale locale = Locale.getDefault(),
            final DiagnosticListener<? super JavaFileObject> listener = null, final PrintWriter writer = { new OutputStreamWriter(System.out), true }) throws Failure {
        final Request request = { paths, options, locale, charset, listener, writer };
        request.compile(parallelStrategy.get()?.booleanValue() ?? (paths.size() > 8));
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
            args += (~classesDir.toAbsolutePath()).toString();
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
        args += STR."\{module.name()}=ALL-UNNAMED";
    });
    
    static void addOpensAllUnnamed(final Collection<String> args, final Map<String, Set<String>> modulesWithPackages) = modulesWithPackages.forEach((module, packages) -> packages.forEach(pkg -> {
        args += "--add-reads";
        args += STR."\{module}/\{pkg}=ALL-UNNAMED";
    }));
    
}
