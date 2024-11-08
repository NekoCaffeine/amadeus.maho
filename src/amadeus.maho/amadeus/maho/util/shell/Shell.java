package amadeus.maho.util.shell;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.JavaFileManager;
import javax.tools.StandardJavaFileManager;

import jdk.internal.jshell.tool.JShellTool;
import jdk.jshell.Eval;
import jdk.jshell.EvalException;
import jdk.jshell.JShell;
import jdk.jshell.Snippet;
import jdk.jshell.execution.DirectExecutionControl;
import jdk.jshell.spi.ExecutionControl;
import jdk.jshell.spi.ExecutionControlProvider;
import jdk.jshell.tool.JavaShellToolBuilder;

import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.reference.Mutable;
import amadeus.maho.transform.AOTTransformer;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.Proxy;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.InvisibleType;
import amadeus.maho.transform.mark.base.TransformMetadata;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.annotation.mark.IndirectCaller;
import amadeus.maho.util.build.Javac;
import amadeus.maho.util.dynamic.CallerContext;
import amadeus.maho.vm.JDWP;

import static amadeus.maho.util.bytecode.Bytecodes.GETFIELD;

@TransformProvider
public interface Shell {
    
    @TransformProvider
    abstract class Context {
        
        private static final String TaskFactory = "jdk.jshell.TaskFactory", MemoryFileManager = "jdk.jshell.MemoryFileManager";
        
        private static final ThreadLocal<@InvisibleType(TaskFactory) Object> local = { };
        
        @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), exactMatch = false)
        private static void _init_(final @InvisibleType(TaskFactory) Object $this) = local.set($this);
        
        @Proxy(GETFIELD)
        private static native @InvisibleType(MemoryFileManager) JavaFileManager fileManager(@InvisibleType(TaskFactory) Object $this);
        
        @Proxy(GETFIELD)
        private static native StandardJavaFileManager stdFileManager(@InvisibleType(MemoryFileManager) Object $this);
        
        @Proxy(GETFIELD)
        private static native JShell state(@InvisibleType(TaskFactory) Object $this);
        
        public static void leave() = local.remove();
        
        public static JavaFileManager memoryFileManager() = fileManager(local.get());
        
        public static StandardJavaFileManager standardJavaFileManager() = stdFileManager(memoryFileManager());
        
        public static JShell shell() = state(local.get());
        
    }
    
    @SneakyThrows
    @IndirectCaller
    static JShell instance(final List<String> runtimeOptions = Javac.runtimeOptions(), final ExecutionControlProvider provider = new DirectExecutionControlProvider(), final @Nullable Map<String, String> parameters = null)
        = JShell.builder().executionEngine(provider, parameters).compilerOptions(runtimeOptions.toArray(String[]::new)).build();
    
    @SneakyThrows
    @IndirectCaller
    static int attachTool(final List<String> runtimeOptions = Javac.runtimeOptions(), final JavaShellToolBuilder builder = JavaShellToolBuilder.builder().interactiveTerminal(true)) {
        try { return builder.start(Stream.concat(Stream.of("-execution", "maho", "-n"), runtimeOptions.stream().map("-C"::concat)).toArray(String[]::new)); } finally { Context.leave(); }
    }
    
    static void importModulePackages(final Module module = CallerContext.caller().getModule(), final Predicate<String> filter = _ -> true)
        = imports() += module.getPackages().stream().filter(filter).map("import %s.*;"::formatted).collect(Collectors.joining("\n", "\n", "\n"));
    
    private static String importJavaAndAmadeusPackages() = Stream.of(Shell.class, Object.class)
            .map(Class::getModule)
            .map(Module::getPackages)
            .flatMap(Collection::stream)
            .filter(pkg -> pkg.startsWith("java.") || pkg.startsWith("amadeus."))
            .map("import %s.*;"::formatted)
            .collect(Collectors.joining("\n", "\n", "\n"));
    
    @Setter
    @Getter
    @Mutable
    String imports = STR."""
            \{importJavaAndAmadeusPackages()}
            import static java.lang.Math.*;
            import static amadeus.maho.util.shell.ShellHelper.*;
            """;
    
    static void extraModule(final Module module = CallerContext.caller().getModule()) = imports(imports() + List.of(module.getPackages().stream().map("import %s.*;"::formatted).collect(Collectors.joining())));
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static String packageAndImportsExcept(final String capture, final @InvisibleType("jdk.jshell.SnippetMaps") Object $this, final Set<Object> except, final Collection<Snippet> plus) = capture + imports();
    
    @Getter
    Set<String> extra = new LinkedHashSet<>() += "amadeus.maho.util.runtime.ModuleHelper.openAllBootModule();";
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static String getResourceString(final String capture, final JShellTool $this, final String key) = "startup.feedback".equals(key) ? capture + STR."""
            
            /set mode maho concise -quiet
            /set prompt maho "\{JDWP.isJDWPEnable() ? "maho-dbg" : "maho"}> " "   ...> "
            /set feedback maho
            
            \{String.join("\n", extra())}
            
            """ : capture;
    
    @Hook
    private static Hook.Result displayEvalException(final JShellTool $this, final EvalException ex, final StackTraceElement caused[]) {
        ex.printStackTrace();
        return Hook.Result.TRUE;
    }
    
    @Hook
    private static Hook.Result translateExceptionStack(final Eval $this, final Exception ex) = { Stream.of(ex.getStackTrace()).takeWhile(element -> !element.getMethodName().equals("do_it$")).toArray(StackTraceElement[]::new) };
    
    @Hook(at = @At(method = @At.MethodInsn(name = "getName")), before = false, capture = true, metadata = @TransformMetadata(aotLevel = AOTTransformer.Level.RUNTIME))
    private static String toString(final String name, final Throwable $this) = $this instanceof EvalException evalEx ? evalEx.getExceptionClassName() : name;
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static void asRunException(final ExecutionControl.RunException capture, final DirectExecutionControl $this, final Throwable exception) = Stream.of(exception.getSuppressed()).forEach(capture::addSuppressed);
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static void asEvalException(final EvalException capture, final Eval $this, final ExecutionControl.UserException exception) = Stream.of(exception.getSuppressed()).forEach(capture::addSuppressed);
    
}
