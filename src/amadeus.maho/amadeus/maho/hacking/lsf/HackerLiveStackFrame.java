package amadeus.maho.hacking.lsf;

import java.io.PrintStream;
import java.lang.module.ModuleDescriptor;
import java.lang.reflect.Executable;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.FutureTask;
import java.util.function.Function;
import java.util.stream.Stream;

import jdk.internal.vm.annotation.Hidden;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import amadeus.maho.hacking.Hacker;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.inspection.APIStatus;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.Proxy;
import amadeus.maho.transform.mark.TransformTarget;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.Experimental;
import amadeus.maho.transform.mark.base.InvisibleType;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.annotation.mark.HiddenDanger;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.ComputeType;
import amadeus.maho.util.bytecode.context.TransformContext;
import amadeus.maho.util.concurrent.ConcurrentWeakIdentityHashMap;
import amadeus.maho.util.misc.Environment;

import static amadeus.maho.core.MahoExport.MAHO_HACKING_LSF;
import static org.objectweb.asm.Opcodes.*;

@Experimental
@HiddenDanger(HiddenDanger.JVM_NOT_CATCH) // java.lang.StackStreamFactory$AbstractStackWalker.fetchStackFrames(JJII[Ljava/lang/Object;)I
@TransformProvider
@APIStatus(design = APIStatus.Stage.β, implement = APIStatus.Stage.γ)
public class HackerLiveStackFrame implements Hacker {
    
    @Getter
    private static final HackerLiveStackFrame instance = { };
    
    private static volatile boolean hackFlag;
    
    @Setter
    @Getter
    private Function<Object, String> localToString = obj -> {
        try {
            return obj instanceof Object[] ? Arrays.deepToString((Object[]) obj) : obj == null ? "<null>" : obj.toString();
        } catch (Throwable t) { return "Throwable(%s): %s".formatted(t.getClass().getName(), t.getMessage()); }
    };
    
    @Setter
    @Getter
    private Function<Class<?>, ModuleDescriptor> descriptorMapper = clazz -> null;
    
    @Override
    public void irrupt() = hackFlag = true;
    
    @Override
    public void recovery() = hackFlag = false;
    
    @Override
    public boolean working() = hackFlag;
    
    public void addDescriptorMapper(final Function<Class<?>, ModuleDescriptor> descriptorMapper) {
        final Function<Class<?>, ModuleDescriptor> source = descriptorMapper();
        descriptorMapper(clazz -> descriptorMapper.apply(clazz) ?? source.apply(clazz));
    }
    
    static {
        if (Environment.local().lookup(MAHO_HACKING_LSF, true))
            instance().irrupt();
    }
    
    @TransformTarget(targetClass = Throwable.class, selector = ASMHelper._INIT_)
    private static void priorityAssignment(final TransformContext context, final ClassNode node, final MethodNode methodNode) {
        if (methodNode.desc.equals(ASMHelper.VOID_METHOD_DESC) || methodNode.desc.equals("(Ljava/lang/String;Ljava/lang/Throwable;ZZ)V"))
            return;
        final InsnList insnList = { };
        for (final AbstractInsnNode insn : methodNode.instructions)
            if (insn.getOpcode() == INVOKEVIRTUAL && ((MethodInsnNode) insn).name.equals("fillInStackTrace")) {
                final AbstractInsnNode previous = insn.getPrevious(), next = insn.getNext();
                Stream.of(previous, insn, next).forEach(methodNode.instructions::remove);
                insnList.add(previous);
                insnList.add(insn);
                insnList.add(next);
            }
        for (final AbstractInsnNode insn : methodNode.instructions)
            if (insn.getOpcode() == RETURN) {
                methodNode.instructions.insertBefore(insn, insnList);
                context.markModified();
                context.markCompute(methodNode, ComputeType.MAX, ComputeType.FRAME);
                return;
            }
    }
    
    @Proxy(INVOKEVIRTUAL)
    public static native StackTraceElement[] getOurStackTrace(Throwable $this);
    
    @Hook
    public static Hook.Result printStackTrace(final Throwable $this, final PrintStream stream) {
        printStackTraceToStream($this, stream);
        return Hook.Result.NULL;
    }
    
    private static final String CAUSE_CAPTION = "Caused by: ", SUPPRESSED_CAPTION = "Suppressed: ", ARROW = "↑";
    
    public static void printStackTraceToStream(final Throwable $this, final PrintStream stream) {
        final Set<Throwable> mark = Collections.newSetFromMap(new IdentityHashMap<>());
        mark.add($this);
        synchronized (stream) {
            stream.println($this);
            final StackTraceElement[] trace = getOurStackTrace($this);
            for (final StackTraceElement traceElement : trace)
                stream.println("\t" + (traceElement.getFileName().startsWith(ARROW) ? traceElement.getFileName() : "at " + traceElement));
            for (final var suppressed : $this.getSuppressed())
                printEnclosedStackTrace(suppressed, stream, trace, SUPPRESSED_CAPTION, "\t", mark);
            @Nullable final Throwable ourCause = $this.getCause();
            if (ourCause != null)
                printEnclosedStackTrace(ourCause, stream, trace, CAUSE_CAPTION, "", mark);
        }
    }
    
    private static void printEnclosedStackTrace(final Throwable $this, final PrintStream stream, final StackTraceElement enclosingTrace[], final String caption, final String prefix, final Set<Throwable> mark) {
        if (mark.contains($this))
            stream.println(prefix + caption + "[CIRCULAR REFERENCE: " + $this + "]");
        else {
            mark.add($this);
            final StackTraceElement trace[] = getOurStackTrace($this);
            int m = trace.length - 1;
            int n = enclosingTrace.length - 1;
            while (m >= 0 && n >=0 && trace[m].equals(enclosingTrace[n])) {
                m--;
                n--;
            }
            final int framesInCommon = trace.length - 1 - m;
            stream.println(prefix + caption + $this);
            for (int i = 0; i <= m; i++)
                stream.println(prefix + "\t" + (trace[i].getFileName().startsWith(ARROW) ? trace[i].getFileName() : "at " + trace[i]));
            if (framesInCommon != 0)
                stream.println(prefix + "\t... " + framesInCommon + " more");
            for (final var suppressed : $this.getSuppressed())
                printEnclosedStackTrace(suppressed, stream, trace, SUPPRESSED_CAPTION, prefix +"\t", mark);
            @Nullable final Throwable ourCause = $this.getCause();
            if (ourCause != null)
                printEnclosedStackTrace(ourCause, stream, trace, CAUSE_CAPTION, prefix, mark);
        }
    }
    
    private static final String
            ExtendedOption = "java.lang.StackWalker$ExtendedOption",
            LiveStackFrame = "java.lang.LiveStackFrame",
            PrimitiveSlot = "java.lang.LiveStackFrame$PrimitiveSlot",
            CompletableFuture$AsyncSupply = "java.util.concurrent.CompletableFuture$AsyncSupply",
            CompletableFuture$AsyncRun = "java.util.concurrent.CompletableFuture$AsyncRun",
            CompletableFuture$UniAccept = "java.util.concurrent.CompletableFuture$UniAccept",
            CompletableFuture$UniRun = "java.util.concurrent.CompletableFuture$UniRun";
    
    private static final ConcurrentWeakIdentityHashMap<StackTraceElement, LiveStackFrame> stackTraceLiveMap = { };
    
    private static final ConcurrentWeakIdentityHashMap<Object, StackTraceElement[]> stackTraceExtender = { };
    
    private static final ThreadLocal<LinkedList<Object>> contextExtender = ThreadLocal.withInitial(LinkedList::new);
    
    private static final ThreadLocal<LinkedList<Throwable>> context = ThreadLocal.withInitial(LinkedList::new);
    
    private static final StackWalker walker = newInstance(Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE), valueOfExtendedOption("LOCALS_AND_OPERANDS"));
    
    @Proxy(value = INVOKESTATIC, target = ExtendedOption, selector = "valueOf")
    private static native @InvisibleType(ExtendedOption) Object valueOfExtendedOption(String name);
    
    @Proxy(value = INVOKESTATIC, targetClass = StackWalker.class)
    private static native StackWalker newInstance(Set<StackWalker.Option> options, @InvisibleType(ExtendedOption) Object extendedOption);
    
    @Proxy(INVOKEINTERFACE)
    private static native Object[] getStack(@InvisibleType(LiveStackFrame) Object $this);
    
    @Proxy(INVOKEINTERFACE)
    private static native Object[] getLocals(@InvisibleType(LiveStackFrame) Object $this);
    
    @Proxy(INVOKEVIRTUAL)
    public static native int size(@InvisibleType(PrimitiveSlot) Object $this);
    
    @Proxy(INVOKEVIRTUAL)
    public static native int intValue(@InvisibleType(PrimitiveSlot) Object $this);
    
    @Proxy(INVOKEVIRTUAL)
    public static native long longValue(@InvisibleType(PrimitiveSlot) Object $this);
    
    @Proxy(PUTFIELD)
    private static native void stackTrace(Throwable $this, StackTraceElement elements[]);
    
    @Proxy(PUTFIELD)
    private static native void detailMessage(Throwable $this, String message);
    
    @Proxy(PUTFIELD)
    private static native void cause(Throwable $this, Throwable cause);
    
    @Proxy(PUTFIELD)
    private static native void suppressedExceptions(Throwable $this, List<Throwable> suppressedExceptions);
    
    @Proxy(value = INSTANCEOF, target = "jdk.internal.reflect.DelegatingClassLoader")
    private static native boolean instanceofDelegatingClassLoader(Object $this);
    
    private static String loaderName(final ClassLoader loader) = loader == null ? "boot" : instanceofDelegatingClassLoader(loader) ? "delegating" : loader.getName() == null ? loader.getClass().getName() : loader.getName();
    
    private static StackTraceElement of(final StackWalker.StackFrame frame) {
        final Class<?> declaringClass = frame.getDeclaringClass();
        final ClassLoader loader = declaringClass.getClassLoader();
        final Module module = declaringClass.getModule();
        final ModuleDescriptor descriptor = !module.isNamed() ? instance().descriptorMapper().apply(declaringClass) : module.getDescriptor();
        final String name = descriptor == null ? "<unnamed>" : descriptor.name(),
                version = descriptor == null ? null : descriptor.version().map(ModuleDescriptor.Version::toString).orElse("?");
        return { loaderName(loader), name, version, declaringClass.getName(), frame.getMethodName(), frame.getFileName(), frame.getLineNumber() };
    }
    
    private static final class ReservedInternalError extends InternalError { }
    
    private static final ReservedInternalError reserved = { };
    
    @Proxy(value = INVOKESTATIC, target = "amadeus.maho.core.extension.DynamicLinkingContext", reverse = true)
    private static native LinkedList<?> contextStack();
    
    @Proxy(GETFIELD)
    private static native int classRedefinedCount(Class<?> $this);
    
    private static volatile int classRedefinedCount;
    
    private static synchronized void printStackTrace(final String message, final Throwable nested) {
        final int count = classRedefinedCount(HackerLiveStackFrame.class);
        if (classRedefinedCount != count) {
            classRedefinedCount = count;
            return;
        }
        detailMessage(reserved, message);
        cause(reserved, nested);
        final @Nullable LinkedList<Throwable> throwables = context.get();
        suppressedExceptions(reserved, throwables == null ? List.of() : new ArrayList<>(throwables));
        reserved.fillInStackTrace();
        reserved.printStackTrace();
    }
    
    private static boolean inDefineClass() = walker.walk(stream -> stream.anyMatch(frame -> frame.getDeclaringClass() == ClassLoader.class && switch (frame.getMethodName()) {
        case "defineClass1", "defineClass2" -> true;
        default                             -> false;
    }));
    
    @Hook(avoidRecursion = true, at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)))
    public static void fillInStackTrace(final Throwable $this) {
        try {
            if ($this == reserved || !hackFlag || !contextStack().isEmpty() || inDefineClass())
                return;
            final LinkedList<Throwable> throwables = context.get();
            if (throwables.contains($this)) {
                printStackTrace("Nested exceptions, the JVM may be about to crash. If you want to avoid this problem, please disable amadeus.maho.hacking.lsf.HackerLiveStackFrame.", $this);
                return;
            }
            if ($this instanceof LinkageError) // avoid jvm crash
                return;
            throwables.push($this);
            try {
                final StackTraceElement elements[] = $this.getStackTrace();
                if (elements.length > 0) {
                    final StackTraceElement first = elements[0];
                    final boolean sync[] = { false };
                    final List<StackTraceElement> realElements = new LinkedList<>();
                    walker.forEach(frame -> {
                        if (sync[0] || frame.getClassName().equals(first.getClassName()) && frame.getMethodName().equals(first.getMethodName()) && (sync[0] = true)) {
                            final StackTraceElement element = of(frame);
                            realElements += element;
                            stackTraceLiveMap.put(element, new LiveStackFrame(frame.getDeclaringClass(), frame.getMethodName(), frame.getMethodType(), getLocals(frame), getStack(frame)));
                        }
                    });
                    final LinkedList<Object> contextStack = contextExtender.get();
                    if (!contextStack.isEmpty())
                        for (final ListIterator<Object> iterator = contextStack.listIterator(contextStack.size()); iterator.hasPrevious(); ) {
                            final Object context = iterator.previous();
                            final @Nullable StackTraceElement elementsExtender[] = stackTraceExtender.get(context);
                            if (elementsExtender != null && elementsExtender.length != 0)
                                realElements *= List.of(elementsExtender);
                        }
                    stackTrace($this, realElements.toArray(StackTraceElement[]::new));
                }
            } finally { context.get()?.pop(); }
        } catch (final Throwable throwable) { fuseProtection(throwable); }
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    public static StackTraceElement[] getOurStackTrace(final StackTraceElement result[], final Throwable $this) = result.length == 0 ? result : Stream.of(result)
            .filter(element -> {
                final LiveStackFrame frame = stackTraceLiveMap.get(element);
                return frame == null || frame.method() == null || frame.method().getAnnotation(Hidden.class) == null;
            }).toArray(StackTraceElement[]::new);
    
    private static boolean is64Bit(final int size) = size == 8;
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    public static String toString(final String result, final StackTraceElement $this) throws Throwable {
        final String fileName = $this.getFileName();
        if (fileName.startsWith("#"))
            return fileName;
        try {
            if (!hackFlag)
                return result;
            final LiveStackFrame frame = stackTraceLiveMap.get($this);
            if (frame == null)
                return result;
            final Map<Parameter, Object> localList = new LinkedHashMap<>();
            final Object locals[] = frame.locals;
            final Executable method = frame.method();
            final Parameter parameters[] = method == null ? new Parameter[0] : method.getParameters();
            for (int i = 0, offset = method == null ? 0 : Modifier.isStatic(method.getModifiers()) ? 0 : -1; offset < parameters.length; i++, offset++) {
                final Class<?> type = offset == -1 ? method.getDeclaringClass() : parameters[offset].getType();
                if (i >= locals.length)
                    localList.put(offset == -1 ? null : parameters[offset], "<invalid>");
                else if (type == long.class)
                    if (is64Bit(size(locals[i]))) {
                        i++;
                        localList.put(parameters[offset], longValue(locals[i]));
                    } else {
                        final long value = (long) intValue(locals[i++]) << 32 | (long) intValue(locals[i]);
                        localList.put(parameters[offset], value);
                    }
                else if (type == double.class)
                    if (is64Bit(size(locals[i]))) {
                        i++;
                        localList.put(parameters[offset], Double.longBitsToDouble(longValue(locals[i])));
                    } else {
                        final long value = (long) intValue(locals[i++]) << 32 | (long) intValue(locals[i]);
                        localList.put(parameters[offset], Double.longBitsToDouble(value));
                    }
                else if (type == float.class)
                    if (is64Bit(size(locals[i])))
                        localList.put(parameters[offset], Float.intBitsToFloat((int) longValue(locals[i])));
                    else
                        localList.put(parameters[offset], Float.intBitsToFloat(intValue(locals[i])));
                else if (type == boolean.class)
                    if (is64Bit(size(locals[i])))
                        localList.put(parameters[offset], (int) longValue(locals[i]) > 0);
                    else
                        localList.put(parameters[offset], intValue(locals[i]) > 0);
                else if (type.isPrimitive())
                    if (is64Bit(size(locals[i])))
                        localList.put(parameters[offset], (int) longValue(locals[i]));
                    else
                        localList.put(parameters[offset], intValue(locals[i]));
                else
                    localList.put(offset == -1 ? null : parameters[offset], locals[i] == null ? "<null>" :
                            type.isAssignableFrom(locals[i].getClass()) ? locals[i] : "<invalid>");
            }
            return String.format("%s locals: %s", result,
                    localList.entrySet().stream()
                            .map(entry -> "%s: %s".formatted(entry.getKey() == null ? "this" :
                                    "%s %s".formatted(entry.getKey().getType().getSimpleName(), entry.getKey().getName()), instance().localToString().apply(entry.getValue()).replace("\r", "").replace("\n", "\\n")))
                            .toList());
        } catch (final Throwable throwable) {
            fuseProtection(throwable);
            return result;
        }
    }
    
    private static synchronized void fuseProtection(final Throwable throwable) {
        if (hackFlag) {
            hackFlag = false;
            context.set(null);
            printStackTrace("Fuse protection, the JVM may be about to crash. If you want to avoid this problem, please disable amadeus.maho.hacking.lsf.HackerLiveStackFrame.", throwable);
        }
    }
    
    public static void fork(final Object target) = stackTraceExtender.computeIfAbsent(target, key -> walker.walk(stream ->
            Stream.concat(Stream.of(new StackTraceElement("amadeus.maho.hacking.lsf.HackerLiveStackFrame", "fork", "↑ " + Thread.currentThread(), -1)), stream
                    .dropWhile(frame -> !frame.getDeclaringClass().isInstance(target))
                    .dropWhile(frame -> frame.getDeclaringClass().isInstance(target))
                    .map(HackerLiveStackFrame::of))
                    .toArray(StackTraceElement[]::new)));
    
    public static void push(final Object context) = contextExtender.get().addLast(context);
    
    public static void pop() = contextExtender.get().peekLast();
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), exactMatch = false)
    public static void _init__$Thread(final Thread $this) = fork($this);
    
    @Hook
    public static void run_$Enter(final Thread $this) = push($this);
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.FINALLY)))
    public static void run_$Exit(final Thread $this) = pop();
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), exactMatch = false)
    public static void _init__$FutureTask(final FutureTask<?> $this) = fork($this);
    
    @Hook
    public static void run_$Enter(final FutureTask<?> $this) = push($this);
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.FINALLY)))
    public static void run_$Exit(final FutureTask<?> $this) = pop();
    
    @Hook
    public static void runAndReset_$Enter(final FutureTask<?> $this) = push($this);
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.FINALLY)))
    public static void runAndReset_$Exit(final FutureTask<?> $this) = pop();
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), exactMatch = false)
    public static void _init__$AsyncSupply(final @InvisibleType(CompletableFuture$AsyncSupply) Object $this) = fork($this);
    
    @Hook
    public static void run_$Enter$AsyncSupply(final @InvisibleType(CompletableFuture$AsyncSupply) Object $this) = push($this);
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.FINALLY)))
    public static void run_$Exit$AsyncSupply(final @InvisibleType(CompletableFuture$AsyncSupply) Object $this) = pop();
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), exactMatch = false)
    public static void _init__$AsyncRun(final @InvisibleType(CompletableFuture$AsyncRun) Object $this) = fork($this);
    
    @Hook
    public static void run_$Enter$AsyncRun(final @InvisibleType(CompletableFuture$AsyncRun) Object $this) = push($this);
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.FINALLY)))
    public static void run_$Exit$AsyncRun(final @InvisibleType(CompletableFuture$AsyncRun) Object $this) = pop();
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), exactMatch = false)
    public static void _init__$UniAccept(final @InvisibleType(CompletableFuture$UniAccept) Object $this) = fork($this);
    
    @Hook
    public static void tryFire_$Enter$UniAccept(final @InvisibleType(CompletableFuture$UniAccept) Object $this) = push($this);
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.FINALLY)))
    public static void tryFire_$Exit$UniAccept(final @InvisibleType(CompletableFuture$UniAccept) Object $this) = pop();
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), exactMatch = false)
    public static void _init__$UniRun(final @InvisibleType(CompletableFuture$UniRun) Object $this) = fork($this);
    
    @Hook
    public static void tryFire_$Enter$UniRun(final @InvisibleType(CompletableFuture$UniRun) Object $this) = push($this);
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.FINALLY)))
    public static void tryFire_$Exit$UniRun(final @InvisibleType(CompletableFuture$UniRun) Object $this) = pop();
    
}
