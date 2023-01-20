package amadeus.maho.lang.javac;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.tools.JavaFileObject;

import jdk.jshell.ExpressionToTypeInfo;
import jdk.jshell.SourceCodeAnalysisImpl;

import com.sun.tools.javac.api.BasicJavacTask;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.code.Lint;
import com.sun.tools.javac.code.Preview;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.TypeMetadata;
import com.sun.tools.javac.comp.Check;
import com.sun.tools.javac.comp.DeferredAttr;
import com.sun.tools.javac.jvm.ClassWriter;
import com.sun.tools.javac.jvm.Gen;
import com.sun.tools.javac.jvm.PoolConstant;
import com.sun.tools.javac.jvm.PoolWriter;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.main.Option;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Options;

import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.Redirect;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.Slice;
import amadeus.maho.transform.mark.base.TransformMetadata;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.dynamic.CallerContext;
import amadeus.maho.vm.JDWP;

import static amadeus.maho.util.bytecode.Bytecodes.PUTFIELD;
import static com.sun.tools.javac.main.JavaCompiler.CompilePolicy.SIMPLE;

@TransformProvider
public class MahoJavac {
    
    public static final String KEY = "amadeus.maho.lang";
    
    private static final boolean debugFlag = System.getProperty("amadeus.maho.lang.debug") != null || JDWP.isJDWPEnable();
    
    @Hook(value = JavaCompiler.CompilePolicy.class, isStatic = true)
    private static Hook.Result decode(final String option) = { SIMPLE };
    
    @Hook
    private static void initPlugins(final BasicJavacTask $this, final Set<List<String>> pluginOpts) {
        if ($this.getContext().get(JavacContext.class) == null)
            new JavacContext($this.getContext());
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.TAIL)))
    private static void prepareCompiler(final JavacTaskImpl $this, final boolean forParse) = $this.getContext().get(JavacContext.class).mark();
    
    @Redirect(targetClass = TypeMetadata.Annotations.class, selector = "combine", slice = @Slice(@At(method = @At.MethodInsn(name = "check"))))
    private static void check(final boolean flag) { }
    
    @Hook
    private static Hook.Result makeBootstrapEntry(final PoolWriter $this, final PoolConstant.Dynamic dynamic) {
        final PoolConstant.Dynamic.BsmKey bsmKey = dynamic.bsmKey((Privilege) $this.types);
        final Map<PoolConstant.Dynamic.BsmKey, Integer> bootstrapMethods = (Privilege) $this.bootstrapMethods;
        @Nullable Integer index = bootstrapMethods[bsmKey];
        if (index == null) {
            index = bootstrapMethods.size();
            bootstrapMethods[bsmKey] = index;
            Stream.of(bsmKey.staticArgs).forEach(arg -> (Privilege) $this.putConstant(arg)); // Prevent recursive bsm from causing exception in com.sun.tools.javac.jvm.ClassWriter#writeBootstrapMethods
        }
        return { index };
    }
    
    @Hook(at = @At(field = @At.FieldInsn(opcode = PUTFIELD, name = "varDebugInfo")), capture = true, metadata = @TransformMetadata(disable = "disable.force.var.debug"))
    private static boolean _init_(final boolean capture, final Gen $this, final Context context) = true;
    
    // Emit parameter names for lambda expressions when javac has "-parameters" in its parameters, See details: https://bugs.openjdk.java.net/browse/JDK-8138729
    @Redirect(targetClass = ClassWriter.class, selector = "writeMethod", slice = @Slice(@At(method = @At.MethodInsn(name = "isLambdaMethod"))),
            metadata = @TransformMetadata(disable = { "JDK-8138729", "disable.emit.lambda.parameter.name" }))
    private static boolean forceEmitLambdaParameterName(final Symbol.MethodSymbol symbol) = false;
    
    // Force write minor version, eliminates the need to add "--enable-preview" at runtime.
    @Redirect(targetClass = ClassWriter.class, selector = "writeClassFile", slice = @Slice(@At(method = @At.MethodInsn(name = "isEnabled"))),
            metadata = @TransformMetadata(disable = "disable.force.minor.version"))
    private static boolean forceWriteMinorVersion(final Preview $this) = false;
    
    // Force enable preview
    @Hook(metadata = @TransformMetadata(disable = "disable.force.preview"))
    private static Hook.Result isEnabled(final Preview $this) = Hook.Result.TRUE;
    
    @Hook(metadata = @TransformMetadata(disable = "disable.force.preview"))
    private static Hook.Result usesPreview(final Preview $this, final JavaFileObject file) = Hook.Result.TRUE;
    
    // Disable preview warn
    @Hook(metadata = @TransformMetadata(disable = "disable.dont.warn.preview"))
    private static Hook.Result isSuppressed(final Lint $this, final Lint.LintCategory category) = Hook.Result.falseToVoid(category == Lint.LintCategory.PREVIEW);
    
    // Write parameter name by default
    @Hook(metadata = @TransformMetadata(disable = "disable.force.write.parameter.name"))
    private static Hook.Result isSet(final Options $this, final Option option) = Hook.Result.falseToVoid(option == Option.PARAMETERS);
    
    @Hook
    private static Hook.Result duplicateError(final Check $this, final JCDiagnostic.DiagnosticPosition pos, final Symbol sym)
            = Hook.Result.falseToVoid(sym instanceof Symbol.VarSymbol varSymbol && varSymbol.name.toString().equals("_"), null);
    
    // Disable some unnecessary warnings
    public static boolean skipLog(final String key) {
        if (debugFlag && !key.startsWith("compiler.note.") && !key.equals("compiler.err.cant.resolve.location.args") && CallerContext.Stack.walker().walk(stream -> stream.noneMatch(MahoJavac::skipFrame)))
            System.out.println("report: " + key); // breakpoint can be here
        return key.startsWith("compiler.note.");
    }
    
    public static final Set<String> disableErrorKeys = new HashSet<>(java.util.List.of(
            "compiler.err.initializer.not.allowed",
            "compiler.err.except.never.thrown.in.try",
            "compiler.err.record.component.and.old.array.syntax",
            "compiler.err.underscore.as.identifier.in.lambda",
            "compiler.err.incompatible.thrown.types.in.mref",
            "compiler.warn.requires.transitive.automatic",
            "compiler.warn.incubating.modules",
            "compiler.warn.unknown.enum.constant",
            "compiler.warn.unknown.enum.constant.reason",
            "compiler.warn.sun.proprietary",
            "compiler.warn.has.been.deprecated.for.removal",
            "compiler.err.switch.expression.no.result.expressions" // SwitchHandler
    ));
    
    public static final Set<Class<?>> skipClasses = new HashSet<>(java.util.List.of(DeferredAttr.class, SourceCodeAnalysisImpl.class, ExpressionToTypeInfo.class));
    
    private static boolean skipFrame(final StackWalker.StackFrame frame) = skipClasses.contains(frame.getDeclaringClass()) || frame.getDeclaringClass() == JavacContext.class && frame.getMethodName().equals("discardDiagnostic");
    
    @Hook
    private static Hook.Result report(final Log $this, final JCDiagnostic diagnostic) = Hook.Result.falseToVoid(disableErrorKeys.contains(diagnostic.getCode()) || skipLog(diagnostic.getCode()));
    
}
