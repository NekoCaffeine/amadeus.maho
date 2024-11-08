package amadeus.maho.lang.javac.multithreaded.dispatch;

import java.io.IOException;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.processing.Processor;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

import com.sun.source.util.TaskEvent;
import com.sun.tools.javac.code.ClassFinder;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Lint;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.Annotate;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.CompileStates;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.LambdaToMethod;
import com.sun.tools.javac.comp.Modules;
import com.sun.tools.javac.comp.Resolve;
import com.sun.tools.javac.comp.TransLiterals;
import com.sun.tools.javac.comp.TransPatterns;
import com.sun.tools.javac.comp.TransTypes;
import com.sun.tools.javac.comp.TypeEnter;
import com.sun.tools.javac.comp.TypeEnvs;
import com.sun.tools.javac.file.BaseFileManager;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.file.PathFileObject;
import com.sun.tools.javac.file.RelativePath;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.resources.CompilerProperties;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Pair;
import com.sun.tools.javac.util.Position;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.javac.JavacContext;
import amadeus.maho.lang.javac.handler.base.DelayedContext;
import amadeus.maho.lang.javac.handler.base.HandlerSupport;
import amadeus.maho.lang.javac.incremental.IncrementalContext;
import amadeus.maho.lang.javac.incremental.IncrementalScanner;
import amadeus.maho.lang.javac.multithreaded.CompileTaskProgress;
import amadeus.maho.lang.javac.multithreaded.MultiThreadedContext;
import amadeus.maho.lang.javac.multithreaded.concurrent.ConcurrentCompleter;
import amadeus.maho.lang.javac.multithreaded.concurrent.ConcurrentSymtab;
import amadeus.maho.lang.javac.multithreaded.concurrent.ConcurrentTransTypes;
import amadeus.maho.lang.javac.multithreaded.parallel.ParallelCompiler;
import amadeus.maho.lang.javac.multithreaded.parallel.ParallelContext;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.Redirect;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.Slice;
import amadeus.maho.transform.mark.base.TransformMetadata;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.concurrent.AsyncHelper;
import amadeus.maho.util.concurrent.ConcurrentWeakIdentityHashMap;
import amadeus.maho.util.control.Interrupt;
import amadeus.maho.util.function.Consumer3;
import amadeus.maho.util.function.Function4;
import amadeus.maho.util.logging.progress.ProgressBar;
import amadeus.maho.util.runtime.DebugHelper;
import amadeus.maho.util.runtime.MethodHandleHelper;

import static amadeus.maho.util.bytecode.Bytecodes.ASTORE;
import static amadeus.maho.util.concurrent.AsyncHelper.*;
import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Kinds.Kind.TYP;
import static com.sun.tools.javac.comp.CompileStates.CompileState.*;
import static com.sun.tools.javac.main.Option.XLINT_CUSTOM;
import static com.sun.tools.javac.tree.JCTree.Tag.*;

@TransformProvider
@FieldDefaults(level = AccessLevel.PUBLIC)
public class DispatchCompiler extends JavaCompiler implements AutoCloseable {
    
    @ToString
    @EqualsAndHashCode
    public record Barrier(@Nullable Consumer<? super ParallelCompiler> consumer, CountDownLatch latch, ConcurrentLinkedQueue<Throwable> throwables = { }) {
        
        public void cross(final ParallelCompiler compiler) {
            try {
                consumer?.accept(compiler);
            } catch (final Throwable throwable) {
                throwables += throwable;
            } finally { latch.countDown(); }
        }
        
    }
    
    public static final Context.Key<DispatchCompiler> dispatchCompilerKey = { };
    
    final DispatchContext context;
    
    final @Nullable IncrementalContext incrementalContext;
    
    final LinkedBlockingQueue<Consumer<ParallelCompiler>> queue = { };
    
    final HashSet<Symbol.ClassSymbol> symbols = { };
    
    protected <T> CompletableFuture<T> evaluate(final Function<ParallelCompiler, T> task) {
        final CompletableFuture<T> future = { };
        queue += context -> {
            try {
                final T result = task[context];
                step();
                future.complete(result);
            } catch (final Throwable throwable) { future.completeExceptionally(throwable); }
        };
        return future;
    }
    
    protected CompletableFuture<Void> dispatch(final Consumer<ParallelCompiler> task) {
        final CompletableFuture<Void> future = { };
        queue += context -> {
            try {
                task[context];
                step();
                future.complete(null);
            } catch (final Throwable throwable) { future.completeExceptionally(throwable); }
        };
        return future;
    }
    
    @Getter
    private volatile @Nullable Barrier barrier;
    
    @Getter
    private volatile boolean shutdown;
    
    volatile boolean enterDoing;
    
    volatile boolean hasError;
    
    LinkedList<ProgressBar<CompileTaskProgress>> progressBars = { };
    
    public DispatchCompiler(final DispatchContext context) {
        super(context);
        this.context = context;
        context.put(dispatchCompilerKey, this);
        inputFiles = ConcurrentHashMap.newKeySet();
        incrementalContext = context.get(IncrementalContext.incrementalContextKey);
    }
    
    public static DispatchCompiler instance(final DispatchContext context = switch (JavacContext.instance().context) {
        case ParallelContext parallelContext -> parallelContext.context;
        case DispatchContext dispatchContext -> dispatchContext;
        default                              -> throw new IllegalStateException(STR."Unexpected value: \{JavacContext.instance().context}");
    }) = (DispatchCompiler) context.get(compilerKey) ?? new DispatchCompiler(context);
    
    public boolean hasError() {
        if (hasError)
            return true;
        if (log.nerrors > 0 || context.parallelContexts().stream().anyMatch(it -> it.get(Log.logKey).nerrors > 0))
            return hasError = true;
        return false;
    }
    
    public boolean shouldPushEvent() = !taskListener.isEmpty();
    
    @Override
    public boolean shouldStop(final CompileStates.CompileState cs) = cs.isAfter(hasError() ? shouldStopPolicyIfError : shouldStopPolicyIfNoError);
    
    @SneakyThrows
    public synchronized void barrier(final @Nullable Consumer<? super ParallelCompiler> consumer, final Runnable beforeAwait = () -> { }) {
        final java.util.List<ParallelContext> parallelContexts = context.parallelContexts();
        final CountDownLatch latch = { parallelContexts.size() };
        barrier = { consumer, latch };
        parallelContexts.forEach(ParallelContext::interrupt);
        beforeAwait.run();
        Interrupt.doUninterruptible(latch::await);
        final @Nullable ConcurrentLinkedQueue<Throwable> throwables = barrier()?.throwables() ?? null;
        if (throwables != null)
            try {
                if (throwables.peek() instanceof Throwable throwable)
                    throw DebugHelper.breakpointBeforeThrow(throwable);
            } finally { barrier = null; }
    }
    
    protected void step() = progressBars.getLast()?.update(CompileTaskProgress::step);
    
    @SneakyThrows
    protected synchronized <T> T dispatchTask(final String name, final int total, final Supplier<T> task) {
        final CompileTaskProgress progress = { name, total };
        try (final ProgressBar<CompileTaskProgress> progressBar = { CompileTaskProgress.renderer, progress }) {
            progressBars << progressBar;
            return DebugHelper.logTimeConsuming(name, task);
        } finally {
            progressBars--;
            VarHandle.fullFence();
        }
    }
    
    @SneakyThrows
    protected synchronized void dispatchTask(final String name, final int total, final Runnable task) {
        final CompileTaskProgress progress = { name, total };
        try (final ProgressBar<CompileTaskProgress> progressBar = { CompileTaskProgress.renderer, progress }) {
            progressBars << progressBar;
            DebugHelper.logTimeConsuming(name, task);
        } finally {
            progressBars--;
            VarHandle.fullFence();
        }
    }
    
    @SneakyThrows
    public <S> Stream<CompletableFuture<Void>> dispatchAsync(final Collection<S> queue, final BiConsumer<ParallelCompiler, S> processor)
        = queue.stream().map(source -> dispatch(compiler -> processor.accept(compiler, source)));
    
    @SneakyThrows
    public <S> void dispatch(final String name, final Collection<S> queue, final BiConsumer<ParallelCompiler, S> processor)
        = dispatchTask(name, queue.size(), () -> await(dispatchAsync(queue, processor)));
    
    @SneakyThrows
    public <S, V, R> R dispatch(final String name, final Collection<S> queue, final BiFunction<ParallelCompiler, S, V> processor, final Collector<? super V, ?, R> collector)
        = dispatchTask(name, queue.size(), () -> queue.stream()
                .map(source -> evaluate(compiler -> processor.apply(compiler, source)))
                .toList()
                .stream()
                .map(AsyncHelper::await)
                .collect(collector));
    
    public <S, V> Queue<V> dispatchQueue(final String name, final Collection<S> envs, final BiFunction<ParallelCompiler, S, V> processor)
        = dispatch(name, envs, processor, Collectors.toCollection(ListBuffer::new));
    
    @SneakyThrows
    public <S, V> Queue<V> dispatchToQueue(final String name, final Collection<S> envs, final Consumer3<ParallelCompiler, S, Queue<V>> processor)
        = dispatchTask(name, envs.size(), () -> {
            final ConcurrentLinkedQueue<V> queue = { };
            await(envs.stream().map(source -> dispatch(compiler -> processor.accept(compiler, source, queue))));
            return queue;
        });
    
    @SneakyThrows
    private static final VarHandle contentCacheHandle = MethodHandleHelper.lookup().findVarHandle(BaseFileManager.class, "contentCache", Map.class);
    
    protected void checkComponentsThreadSafety() {
        if (fileManager instanceof JavacFileManager javacFileManager) {
            if ((Privilege) javacFileManager.pathsAndContainersByLocationAndRelativeDirectory instanceof
                    HashMap<JavaFileManager.Location, Map<RelativePath.RelativeDirectory, java.util.List<JavacFileManager.PathAndContainer>>> pathsAndContainersByLocationAndRelativeDirectory)
                (Privilege) (javacFileManager.pathsAndContainersByLocationAndRelativeDirectory = new ConcurrentHashMap<>(pathsAndContainersByLocationAndRelativeDirectory));
            if ((Privilege) javacFileManager.nonIndexingContainersByLocation instanceof
                    HashMap<JavaFileManager.Location, java.util.List<JavacFileManager.PathAndContainer>> nonIndexingContainersByLocation)
                (Privilege) (javacFileManager.nonIndexingContainersByLocation = new ConcurrentHashMap<>(nonIndexingContainersByLocation));
            if ((Privilege) javacFileManager.contentCache instanceof HashMap<JavaFileObject, BaseFileManager.ContentCacheEntry> contentCache)
                contentCacheHandle.set(javacFileManager, new ConcurrentHashMap<>(contentCache));
        }
        {
            if (lower.prunedTree instanceof WeakHashMap<Symbol.ClassSymbol, List<JCTree>> prunedTree)
                lower.prunedTree = new ConcurrentWeakIdentityHashMap<>(prunedTree);
        }
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static Map<RelativePath.RelativeDirectory, java.util.List<JavacFileManager.PathAndContainer>> indexPathsAndContainersByRelativeDirectory(
            final Map<RelativePath.RelativeDirectory, java.util.List<JavacFileManager.PathAndContainer>> capture, final JavacFileManager manager, final JavaFileManager.Location location)
        = JavacContext.instance().context instanceof MultiThreadedContext ? new ConcurrentHashMap<>(capture) : capture;
    
    public Queue<Env<AttrContext>> queue() = incrementalContext?.queue(context) ?? todo;
    
    @Override
    public void compile(final Collection<JavaFileObject> sourceFileObjects, final Collection<String> classNames, final Iterable<? extends Processor> processors, final Collection<String> addModules) {
        if (!classNames.isEmpty())
            throw new UnsupportedOperationException("Annotation processors are not currently supported");
        checkComponentsThreadSafety();
        if (shouldPushEvent())
            taskListener.started(new TaskEvent(TaskEvent.Kind.COMPILATION));
        options.put(STR."\{XLINT_CUSTOM.primaryName}-\{Lint.LintCategory.OPTIONS.option}", "true");
        options.remove(XLINT_CUSTOM.primaryName + Lint.LintCategory.OPTIONS.option);
        context.parallelContexts();
        try {
            enterTrees(stopIfError(ENTER, initModules(stopIfError(ENTER, parseFiles(sourceFileObjects)))));
            if (taskListener.isEmpty() && implicitSourcePolicy == ImplicitSourcePolicy.NONE)
                todo.retainFiles(inputFiles);
            if (!ATTR.isAfter(shouldStopPolicyIfNoError))
                generate(desugar(flow(attribute(queue()))));
        } finally {
            reportDeferredDiagnostics();
            if (!log.hasDiagnosticListener()) {
                printCount("error", errorCount());
                printCount("warn", warningCount());
            }
            if (shouldPushEvent())
                taskListener.finished(new TaskEvent(TaskEvent.Kind.COMPILATION));
        }
    }
    
    @Override
    public int warningCount() = log.nwarnings + context.parallelContexts().stream().mapToInt(it -> it.get(Log.logKey).nwarnings).sum();
    
    @Override
    public int errorCount() {
        final int error = log.nerrors + context.parallelContexts().stream().mapToInt(it -> it.get(Log.logKey).nerrors).sum();
        if (werror && error == 0 && warningCount() > 0)
            log.error(CompilerProperties.Errors.WarningsAndWerror);
        return error;
    }
    
    @Override
    public synchronized void close() {
        if (!shutdown) {
            barrier(null);
            shutdown = true;
            context.parallelContexts().forEach(ParallelContext::interrupt);
            super.close();
        }
    }
    
    @Override
    public @Nullable CharSequence readSource(final JavaFileObject filename) {
        try {
            inputFiles += filename;
            return filename.getCharContent(false);
        } catch (final IOException e) {
            JavacContext.instance().log.error(CompilerProperties.Errors.ErrorReadingFile(filename, JavacFileManager.getMessage(e)));
            return null;
        }
    }
    
    @Redirect(targetClass = BaseFileManager.class, selector = "decode", slice = @Slice(@At(field = @At.FieldInsn(name = "log"))))
    private static Log contextLog_$BaseFileManager$decode(final BaseFileManager $this) = JavacContext.instanceMayNull()?.log ?? $this.log;
    
    @Redirect(targetClass = PathFileObject.class, selector = "getCharContent", slice = @Slice(@At(field = @At.FieldInsn(name = "log"))))
    private static Log contextLog_$PathFileObject$getCharContent(final BaseFileManager $this) = JavacContext.instanceMayNull()?.log ?? $this.log;
    
    @Override
    public List<JCTree.JCCompilationUnit> parseFiles(final Iterable<JavaFileObject> fileObjects, final boolean force)
        = !force && shouldStop(PARSE) ? List.nil() : dispatch("parseFiles", fileObjects.fromIterable().distinct().toList(), ParallelCompiler::parse, List.collector());
    
    @Override
    public List<JCTree.JCCompilationUnit> enterTrees(final List<JCTree.JCCompilationUnit> roots) {
        try {
            if (shouldPushEvent())
                roots.stream().map(unit -> new TaskEvent(TaskEvent.Kind.PARSE, unit)).forEach(taskListener::started);
            enter(roots);
            enterDone();
            if (shouldPushEvent())
                roots.stream().map(unit -> new TaskEvent(TaskEvent.Kind.ENTER, unit)).forEach(taskListener::finished);
            if (sourceOutput)
                (Privilege) (rootClasses = roots.stream().flatMap(unit -> unit.defs.stream()).cast(JCTree.JCClassDecl.class).collect(List.collector()));
            roots.stream().map(unit -> unit.sourcefile).forEach(inputFiles::add);
            return roots;
        } finally {
            final ConcurrentLinkedQueue<Env<AttrContext>> buffer = { };
            barrier(compiler -> buffer *= compiler.todo);
            todo *= buffer;
        }
    }
    
    // The member phase may generate class symbols outside of the source code, and the completers of these symbols will cause thread crossing
    @Hook
    private static Hook.Result complete(final TypeEnter $this, final Symbol symbol) {
        final @Nullable JavacContext instance = JavacContext.instanceMayNull(); // null when <init>
        if (instance != null && instance.typeEnter != $this) {
            instance.typeEnter.complete(symbol);
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    // ArrayList is not thread safe
    @Hook(at = @At(field = @At.FieldInsn(name = "permitted")), capture = true)
    private static CopyOnWriteArrayList<?> _init_(final java.util.List<?> capture, final Symbol.ClassSymbol $this, final long flags, final Name name, final Type type, final Symbol owner) = { capture };
    
    // permitted thread safe
    @Hook(forceReturn = true)
    private static void fillPermits(final TypeEnter.HeaderPhase $this, final JCTree.JCClassDecl tree, final Env<AttrContext> baseEnv) {
        final JavacContext instance = JavacContext.instance();
        final Symbol.ClassSymbol classSymbol = tree.sym;
        // fill in implicit permits in supertypes:
        if (!classSymbol.isAnonymous() || classSymbol.isEnum()) {
            for (final Type superType : instance.types.directSupertypes(classSymbol.type)) {
                if (superType.tsym.kind == TYP) {
                    final Symbol.ClassSymbol superClassSymbol = (Symbol.ClassSymbol) superType.tsym;
                    final Env<AttrContext> superEnv = instance.enter.getEnv(superClassSymbol);
                    if (superClassSymbol.isSealed() && !superClassSymbol.isPermittedExplicit && superEnv != null && superEnv.toplevel == baseEnv.toplevel)
                        superClassSymbol.addPermittedSubclass(classSymbol, tree.pos);
                }
            }
        }
        // attribute (explicit) permits of the current class:
        if (classSymbol.isPermittedExplicit) {
            final ListBuffer<Symbol> permittedSubtypeSymbols = { };
            final List<JCTree.JCExpression> permittedTrees = tree.permitting;
            for (final JCTree.JCExpression permitted : permittedTrees) {
                final Type pt = (Privilege) instance.attr.attribBase(permitted, baseEnv, false, false, false);
                permittedSubtypeSymbols.append(pt.tsym);
            }
            classSymbol.setPermittedSubclasses(permittedSubtypeSymbols.toList());
        }
    }
    
    public <P extends TypeEnter.Phase> P lookupPhase(final TypeEnter typeEnter, final Class<P> phaseType) {
        @Nullable TypeEnter.Phase phase = (Privilege) typeEnter.completeClass;
        // noinspection DataFlowIssue
        do if (phaseType.isInstance(phase))
            return (P) phase;
        while ((phase = (Privilege) phase.next) != null);
        throw new AssertionError(STR."Phase instance not found: \{phaseType.getName()}");
    }
    
    public void runPhase(final Collection<Symbol.ClassSymbol> symbols, final Class<? extends TypeEnter.Phase> phaseType, final ConcurrentLinkedQueue<CompletableFuture<Void>> futures,
            final Function<Symbol.ClassSymbol, Collection<Symbol.ClassSymbol>> derivedFunction = _ -> null) = dispatchAsync(symbols, (compiler, symbol) -> {
        final TypeEnter typeEnter = TypeEnter.instance(compiler.context);
        final TypeEnter.Phase phase = lookupPhase(typeEnter, phaseType), prevTopLevelPhase = (Privilege) typeEnter.topLevelPhase;
        final Env<AttrContext> env = (Privilege) TypeEnvs.instance(context).get(symbol);
        final JCTree tree = env.tree;
        final JavaFileObject prev = ((Privilege) typeEnter.log).useSource(env.toplevel.sourcefile);
        final JCDiagnostic.DiagnosticPosition prevLintPos = ((Privilege) typeEnter.deferredLintHandler).setPos(tree.pos());
        try {
            (Privilege) phase.runPhase(env);
            final @Nullable Collection<Symbol.ClassSymbol> derived = derivedFunction[symbol];
            if (derived != null)
                runPhase(derived, phaseType, futures, derivedFunction);
        } finally {
            (Privilege) (typeEnter.topLevelPhase = prevTopLevelPhase);
            ((Privilege) typeEnter.deferredLintHandler).setPos(prevLintPos);
            ((Privilege) typeEnter.log).useSource(prev);
        }
    }).forEach(futures::add);
    
    public void runPhaseWithDependencies(final Set<Symbol.ClassSymbol> symbols, final Function<Symbol.ClassSymbol, Symbol> prev, final TypeEnter.Phase phase) {
        final ArrayList<Symbol.ClassSymbol> roots = { };
        final HashMap<Symbol.ClassSymbol, ArrayList<Symbol.ClassSymbol>> dependencies = { };
        symbols.forEach(classSymbol -> prev[classSymbol] instanceof Symbol.ClassSymbol superClassSymbol && symbols[superClassSymbol] ?
                                               dependencies.computeIfAbsent(superClassSymbol, _ -> new ArrayList<>()) : roots += classSymbol);
        awaitRecursion(futures -> runPhase(roots, phase.getClass(), futures, dependencies::get));
    }
    
    public void enter(final List<JCTree.JCCompilationUnit> trees) {
        annotate.blockAnnotations();
        try {
            final CopyOnWriteArrayList<Symbol.ClassSymbol> allUncompleted = { };
            dispatch("classEnter", trees, (compiler, tree) -> {
                final Enter nextEnter = (Privilege) compiler.enter;
                final ListBuffer<Symbol.ClassSymbol> uncompletedBuffer = { };
                (Privilege) (nextEnter.uncompleted = uncompletedBuffer);
                (Privilege) nextEnter.classEnter(tree, null);
                (Privilege) (nextEnter.uncompleted = null);
                uncompletedBuffer.forEach(classSymbol -> {
                    classSymbol.completer = Symbol.Completer.NULL_COMPLETER;
                    classSymbol.flags_field |= UNATTRIBUTED | SUPER_OWNER_ATTRIBUTED;
                });
                allUncompleted.addAll(uncompletedBuffer);
            });
            final TypeEnter typeEnter = (Privilege) enter.typeEnter;
            TypeEnter.Phase phase = (Privilege) typeEnter.completeClass;
            {
                final java.util.List<Symbol.ClassSymbol> uncompleted = allUncompleted.stream().filter(symbol -> symbol.owner.kind == Kinds.Kind.PCK).toList();
                final TypeEnter.Phase current = phase; // Import
                dispatchTask(STR."enter-\{current.getClass().getSimpleName()}", uncompleted.size(), () -> awaitRecursion(futures ->
                        runPhase(uncompleted, current.getClass(), futures)));
            }
            phase = (Privilege) phase.next;
            symbols.clear();
            symbols *= allUncompleted;
            {
                final TypeEnter.Phase current = phase; // Hierarchy
                dispatchTask(STR."enter-\{current.getClass().getSimpleName()}", symbols.size(), () -> runPhaseWithDependencies(symbols, symbol -> symbol.owner, current));
            }
            phase = (Privilege) phase.next;
            do {
                final TypeEnter.Phase current = phase; // Header | Record
                dispatchTask(STR."enter-\{current.getClass().getSimpleName()}", symbols.size(), () -> awaitRecursion(futures -> runPhase(symbols, current.getClass(), futures)));
                phase = (Privilege) phase.next;
            } while (!(phase instanceof TypeEnter.MembersPhase));
            {
                final TypeEnter.Phase current = phase; // Members
                dispatchTask(STR."enter-\{current.getClass().getSimpleName()}", symbols.size(), () -> runPhaseWithDependencies(symbols, symbol -> types.supertype(symbol.type).tsym, current));
            }
            dispatch("finishImports", trees, (compiler, tree) -> (Privilege) TypeEnter.instance(compiler.context).finishImports(tree, !tree.starImportScope.isFilled() ?
                    () -> (Privilege) ((Privilege) TypeEnter.instance(compiler.context).completeClass).resolveImports(tree, Enter.instance(compiler.context).getTopLevelEnv(tree)) : () -> { }));
        } finally { annotate.unblockAnnotations(); }
    }
    
    @Hook
    private static Hook.Result attributeAnnotationType(final Annotate $this, final Env<AttrContext> env) {
        final @Nullable JavacContext instance = JavacContext.instanceMayNull();
        if (instance != null && instance.annotate != $this) {
            (Privilege) instance.annotate.attributeAnnotationType(env);
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    private static final Map<String, VarHandle> allQ = List.of("q", "typesQ", "afterTypesQ", "validateQ").stream().collect(Collectors.toMap(Function.identity(), DispatchCompiler::lookupQ));
    
    @SneakyThrows
    private static VarHandle lookupQ(final String name) = MethodHandleHelper.lookup().findVarHandle(Annotate.class, name, ListBuffer.class);
    
    @Hook(at = @At(field = @At.FieldInsn(name = "q")))
    private static Hook.Result flush(final Annotate $this) {
        if (JavacContext.instance().context instanceof DispatchContext context) {
            final DispatchCompiler instance = instance(context);
            if (instance.enterDoing) {
                allQ.forEach((name, q) -> instance.barrier(compiler -> run((Privilege) compiler.annotate, q), () -> run($this, q)));
                return Hook.Result.NULL;
            }
        }
        return Hook.Result.VOID;
    }
    
    private static void run(final Annotate annotate, final VarHandle handle) {
        final ListBuffer<Runnable> buffer = (ListBuffer<Runnable>) handle.get(annotate);
        (Privilege) annotate.startFlushing();
        try {
            while (buffer.nonEmpty())
                buffer.next().run();
        } finally { (Privilege) annotate.doneFlushing(); }
    }
    
    @Override
    public void enterDone() {
        dispatch("loadDynamicAnnotationProvider", modules.allModules(), (compiler, module) -> JavacContext.instance(compiler.context, HandlerSupport.class).loadDynamicAnnotationProvider(module));
        enterDoing = true;
        try {
            barrier(ParallelCompiler::unblockAnnotationsNoFlush);
            (Privilege) (enterDone = true);
            annotate.enterDone();
        } finally { enterDoing = false; }
        JavacContext.instance(DelayedContext.class).beforeEnterDone(this);
    }
    
    @Hook(metadata = @TransformMetadata(order = -1 /* DefaultValueHandler */)) // unexpected thread crossing
    private static Hook.Result setLazyConstValue(final Symbol.VarSymbol $this, final Env<AttrContext> env, final Attr attr, final JCTree.JCVariableDecl variable) {
        if (JavacContext.instance().context instanceof MultiThreadedContext) {
            $this.setData((Callable<Object>) () -> JavacContext.instance().attr.attribLazyConstantValue(env, variable, $this.type));
            return Hook.Result.NULL;
        }
        return Hook.Result.VOID;
    }
    
    // ImportPhase too slow
    @Hook(forceReturn = true)
    private static long flags(final Symbol.PackageSymbol $this) = $this.flags_field;
    
    @Hook
    private static Hook.Result complete(final ClassFinder $this, final Symbol symbol) {
        if (JavacContext.instance().context instanceof ParallelContext parallelContext) {
            final ClassFinder instance = ClassFinder.instance(parallelContext);
            if ($this != instance) {
                instance.getCompleter().complete(symbol);
                return Hook.Result.NULL;
            }
        }
        return Hook.Result.VOID;
    }
    
    @Hook
    private static Hook.Result loadClass(final ClassFinder $this, final Symbol.ModuleSymbol moduleSymbol, final Name flatname) throws Symbol.CompletionFailure {
        if ((Privilege) $this.syms instanceof ConcurrentSymtab symtab)
            return { symtab.tryLoadClass(moduleSymbol, flatname) };
        return Hook.Result.VOID;
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static Symbol.Completer getCompleter(final Symbol.Completer capture, final Modules $this) = (Privilege) $this.syms instanceof ConcurrentSymtab ? new ConcurrentCompleter(capture) : capture;
    
    @Hook(at = @At(var = @At.VarInsn(opcode = ASTORE, var = 8)), capture = true) // Assert.check(env1.info.preferredTreeForDiagnostics == null);
    private static Env<AttrContext> findFun(final Env<AttrContext> capture, final Resolve $this, final Env<AttrContext> env, final Name name,
            final List<Type> argTypes, final List<Type> typeArgTypes, final boolean allowBoxing, final boolean useVarargs) = capture.dup(capture.tree, (Privilege) capture.info.dup());
    
    @Hook
    private static Hook.Result visitClassDef(final Attr $this, final JCTree.JCClassDecl tree)
        = Hook.Result.falseToVoid(tree.sym != null && JavacContext.instance().context instanceof ParallelContext context && instance(context.context).symbols[tree.sym], null);
    
    @Override
    public Queue<Env<AttrContext>> attribute(final Queue<Env<AttrContext>> envs) {
        final Queue<Env<AttrContext>> queue;
        if (envs == todo) {
            dispatch("attribute", symbols, (compiler, symbol) -> {
                final Env<AttrContext> env = enter.getEnv(symbol);
                if (compileStates.isDone(env, ATTR))
                    return;
                if (shouldPushEvent())
                    taskListener.started((Privilege) ((JavaCompiler) this).newAnalyzeTaskEvent(env));
                ((Privilege) compiler.attr).attribClass(env.tree.pos(), symbol);
                if (symbol.outermostClass() == symbol)
                    compileStates[env] = ATTR;
            });
            envs.forEach(env -> {
                switch (env.tree.getTag()) {
                    case MODULEDEF,
                         PACKAGEDEF -> attr.attrib(env);
                }
            });
            return stopIfError(ATTR, envs);
        } else
            queue = stopIfError(ATTR, dispatchQueue("attribute", envs, ParallelCompiler::attribute));
        if (queue.nonEmpty()) {
            JavacContext.instance(DelayedContext.class).beforeAttributeDone(this);
            if (errorCount() > 0 && !shouldStop(ATTR))
                dispatch("postAttr", envs, (compiler, env) -> attr.postAttr(env.tree));
        }
        return queue;
    }
    
    @Override
    public Queue<Env<AttrContext>> flow(final Queue<Env<AttrContext>> envs) = dispatchToQueue("flow", envs, ParallelCompiler::flow);
    
    ConcurrentHashMap<Env<AttrContext>, Queue<Pair<Env<AttrContext>, JCTree.JCClassDecl>>> desugaredEnvs = { };
    
    public void translateInfoDef(final Env<AttrContext> env, final Queue<Pair<Env<AttrContext>, JCTree.JCClassDecl>> results) {
        if (sourceOutput)
            return;
        final JavaFileObject prev = log.useSource(env.enclClass.sym.sourcefile != null ? env.enclClass.sym.sourcefile : env.toplevel.sourcefile);
        final TreeMaker localMake = make.at(Position.FIRSTPOS).forToplevel(env.toplevel);
        try {
            final List<JCTree> def = lower.translateTopLevelClass(env, env.tree, localMake);
            if (def.head != null) {
                Assert.check(def.tail.isEmpty());
                results += new Pair<>(env, (JCTree.JCClassDecl) def.head);
            }
            compileStates[env] = LOWER;
        } finally { log.useSource(prev); }
    }
    
    public void translateTypes(final Collection<Symbol.ClassSymbol> symbols, final ConcurrentLinkedQueue<CompletableFuture<Void>> futures, final Function<Symbol.ClassSymbol, Collection<Symbol.ClassSymbol>> derivedFunction)
        = dispatchAsync(symbols, (compiler, symbol) -> {
            final Env<AttrContext> env = enter.getEnv(symbol);
            final JavaFileObject prev = compiler.log.useSource(symbol.sourcefile != null ? symbol.sourcefile : env.toplevel.sourcefile);
            final TreeMaker localMake = ((Privilege) compiler.make).at(Position.FIRSTPOS).forToplevel(env.toplevel);
            final ConcurrentTransTypes transTypes = (ConcurrentTransTypes) TransTypes.instance(compiler.context);
            try {
                (Privilege) (transTypes.make = localMake);
                (Privilege) (transTypes.pt = null);
                transTypes.translateClass(symbol, env);
                compileStates[env] = TRANSTYPES;
            } finally { compiler.log.useSource(prev); }
            final @Nullable Collection<Symbol.ClassSymbol> derived = derivedFunction[symbol];
            if (derived != null)
                translateTypes(derived, futures, derivedFunction);
        }).forEach(futures::add);
    
    public <T> void translate(final String name, final Queue<Env<AttrContext>> envs, final Predicate<Env<AttrContext>> predicate = _ -> true, final Function<Context, T> translator,
            final Function4<T, Env<AttrContext>, JCTree, TreeMaker, JCTree> translate, final CompileStates.CompileState state)
        = dispatch(name, envs, (compiler, env) -> {
            if (predicate.test(env)) {
                final JavaFileObject prev = compiler.log.useSource(env.enclClass.sym.sourcefile != null ? env.enclClass.sym.sourcefile : env.toplevel.sourcefile);
                final TreeMaker localMake = ((Privilege) compiler.make).at(Position.FIRSTPOS).forToplevel(env.toplevel);
                try {
                    env.tree = translate.apply(translator[compiler.context], env, env.tree, localMake);
                    compileStates[env] = state;
                } finally { compiler.log.useSource(prev); }
            }
        });
    
    public void translateLower(final Queue<Env<AttrContext>> envs, final Queue<Pair<Env<AttrContext>, JCTree.JCClassDecl>> results)
        = dispatch("lower", envs, (compiler, env) -> {
            final JavaFileObject prev = compiler.log.useSource(env.enclClass.sym.sourcefile != null ? env.enclClass.sym.sourcefile : env.toplevel.sourcefile);
            final TreeMaker localMake = ((Privilege) compiler.make).at(Position.FIRSTPOS).forToplevel(env.toplevel);
            try {
                ((Privilege) compiler.lower).translateTopLevelClass(env, env.tree, localMake).stream()
                        .map(classDecl -> new Pair<>(env, (JCTree.JCClassDecl) classDecl))
                        .forEach(results::add);
                compileStates[env] = LOWER;
            } finally { compiler.log.useSource(prev); }
        });
    
    @Override
    public Queue<Pair<Env<AttrContext>, JCTree.JCClassDecl>> desugar(final Queue<Env<AttrContext>> envs) {
        final ConcurrentLinkedQueue<Pair<Env<AttrContext>, JCTree.JCClassDecl>> result = { };
        if (envs.isEmpty())
            return result;
        Stream<Env<AttrContext>> stream = envs.stream();
        if (implicitSourcePolicy == ImplicitSourcePolicy.NONE)
            stream = stream.filter(env -> inputFiles.contains(env.toplevel.sourcefile));
        if (!modules.multiModuleMode)
            stream = stream.filter(env -> env.toplevel.modle == modules.getDefaultModule());
        stream = stream.filter(env -> {
            if (compileStates.isDone(env, LOWER)) {
                result *= desugaredEnvs[env]!;
                return false;
            }
            if (env.tree.hasTag(PACKAGEDEF) || env.tree.hasTag(MODULEDEF)) {
                translateInfoDef(env, result);
                return false;
            }
            return true;
        });
        final Queue<Env<AttrContext>> queue = stream.collect(Collectors.toCollection(LinkedList::new));
        if (shouldStop(TRANSTYPES))
            return new ConcurrentLinkedQueue<>();
        final Map<Env<AttrContext>, NestedScanner> scanners = dispatch("desugar-scan", queue,
                (compiler, env) -> new NestedScanner(compiler.context, env).let(scanner -> scanner.scan(env.tree)), Collectors.toMap(NestedScanner::env, Function.identity()));
        {
            final Queue<Env<AttrContext>> dependencies = scanners.values().stream()
                    .map(NestedScanner::dependencies)
                    .flatMap(Collection::stream)
                    .distinct()
                    .filter(env -> !compileStates.isDone(env, FLOW))
                    .collect(Collectors.toCollection(ConcurrentLinkedQueue::new));
            // The normal compilation process should not enter this branch, this is just a cover for unexpected calls
            if (!dependencies.isEmpty()) {
                flow(attribute(dependencies));
                dependencies.stream().filterNot(new HashSet<>(envs)::contains).forEach(queue::add);
            }
        }
        if (shouldStop(TRANSTYPES))
            return new ConcurrentLinkedQueue<>();
        {
            final Set<Symbol.ClassSymbol> symbols = scanners.values().stream().map(NestedScanner::symbols).flatMap(Collection::stream).collect(Collectors.toSet());
            final ArrayList<Symbol.ClassSymbol> roots = { };
            final HashMap<Symbol.ClassSymbol, ArrayList<Symbol.ClassSymbol>> dependencies = { };
            symbols.forEach(classSymbol -> types.supertype(classSymbol.type).tsym instanceof Symbol.ClassSymbol superClassSymbol && symbols[superClassSymbol] ?
                                                   dependencies.computeIfAbsent(superClassSymbol, _ -> new ArrayList<>()) : roots += classSymbol);
            dispatchTask("desugar-translateTypes", symbols.size(), () -> awaitRecursion(futures -> translateTypes(roots, futures, dependencies::get)));
        }
        if (shouldStop(TRANSLITERALS))
            return new ConcurrentLinkedQueue<>();
        translate("desugar-translateLiterals", queue, TransLiterals::instance, TransLiterals::translateTopLevelClass, TRANSLITERALS);
        if (shouldStop(TRANSPATTERNS))
            return new ConcurrentLinkedQueue<>();
        translate("desugar-translatePatterns", queue, env -> scanners[env]!.hasPatterns(), TransPatterns::instance, TransPatterns::translateTopLevelClass, TRANSPATTERNS);
        if (shouldStop(UNLAMBDA))
            return new ConcurrentLinkedQueue<>();
        translate("desugar-unlambda", queue, env -> scanners[env]!.hasLambdas(), LambdaToMethod::instance, LambdaToMethod::translateTopLevelClass, UNLAMBDA);
        if (shouldStop(LOWER))
            return new ConcurrentLinkedQueue<>();
        if (sourceOutput)
            scanners.values().stream()
                    .filter(scanner -> scanner.untranslated() instanceof JCTree.JCClassDecl classDecl && ((Privilege) rootClasses).contains(classDecl))
                    .map(scanner -> new Pair<>(scanner.env(), (JCTree.JCClassDecl) scanner.env().tree))
                    .forEach(result::add);
        else
            translateLower(queue, result);
        return result;
        
    }
    
    @Override
    public void generate(final Queue<Pair<Env<AttrContext>, JCTree.JCClassDecl>> queue, final @Nullable Queue<JavaFileObject> results) {
        if (incrementalContext != null)
            dispatch("incremental-collect", queue.stream().map(pair -> pair.snd).toList(), (compiler, classDecl) -> classDecl.accept(IncrementalScanner.instance(compiler.context)));
        final @Nullable Queue<JavaFileObject> delegateQueue = results == null ? null : new ConcurrentLinkedQueue<>();
        dispatch("generate", queue, (compiler, pair) -> {
            final LinkedList<Pair<Env<AttrContext>, JCTree.JCClassDecl>> wrapper = { };
            wrapper << pair;
            compiler.generate(wrapper, delegateQueue);
        });
        // noinspection DataFlowIssue
        results?.addAll(delegateQueue);
    }
    
}
