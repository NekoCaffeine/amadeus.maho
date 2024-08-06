package amadeus.maho.transform;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import amadeus.maho.core.Maho;
import amadeus.maho.core.MahoProfile;
import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.handler.base.BaseTransformer;
import amadeus.maho.transform.handler.base.DerivedTransformer;
import amadeus.maho.transform.handler.base.FieldTransformer;
import amadeus.maho.transform.handler.base.MethodTransformer;
import amadeus.maho.transform.handler.base.marker.Marker;
import amadeus.maho.transform.mark.base.TransformMark;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.transform.mark.base.Transformed;
import amadeus.maho.transform.mark.base.Transformer;
import amadeus.maho.util.annotation.AnnotationHandler;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.ClassWriter;
import amadeus.maho.util.bytecode.context.TransformContext;
import amadeus.maho.util.bytecode.remap.RemapHandler;
import amadeus.maho.util.bytecode.remap.StreamRemapHandler;
import amadeus.maho.util.concurrent.AsyncHelper;
import amadeus.maho.util.concurrent.ConcurrentWeakIdentityHashSet;
import amadeus.maho.util.container.MapTable;
import amadeus.maho.util.misc.Environment;
import amadeus.maho.util.profile.Sampler;
import amadeus.maho.util.resource.ClassLoadable;
import amadeus.maho.util.resource.ResourcePath;
import amadeus.maho.util.runtime.ArrayHelper;
import amadeus.maho.util.runtime.DebugHelper;
import amadeus.maho.util.runtime.MethodHandleHelper;
import amadeus.maho.util.runtime.ObjectHelper;
import amadeus.maho.util.runtime.ReflectionHelper;
import amadeus.maho.util.throwable.ExtraInformationThrowable;

import static amadeus.maho.core.MahoExport.*;
import static amadeus.maho.util.concurrent.AsyncHelper.*;
import static java.nio.file.StandardOpenOption.*;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TransformerManager implements ClassFileTransformer, StreamRemapHandler {
    
    @Getter
    private static final TransformerManager runtime = { "runtime" };
    
    @Getter
    @NoArgsConstructor(AccessLevel.PRIVATE)
    public static final class Patcher {
        
        private static final ConcurrentWeakIdentityHashSet<Class<?>> preLoadedClasses = { };
        
        public static void patch(final TransformRange transformer) = patch(List.of(transformer));
        
        @SneakyThrows
        public static synchronized void patch(final Collection<? extends TransformRange> transformers) {
            VarHandle.fullFence();
            final Lock lock = runtime().read;
            lock.lock();
            try {
                final Instrumentation instrumentation = Maho.instrumentation();
                final Stream<Class> stream = Stream.of(instrumentation.getAllLoadedClasses())
                        .parallel()
                        .filter(instrumentation::isModifiableClass);
                final Class classes[] = (preLoadedClasses.isEmpty() ? stream : stream.filter(clazz -> !preLoadedClasses.contains(clazz)))
                        .filter(clazz -> transformers.stream().anyMatch(transformer -> transformer.isTarget(clazz)))
                        .toArray(Class[]::new);
                synchronized (VerifyErrorInfoTransformer.verifyErrorsRef) {
                    final ArrayList<VerifyError> verifyErrors = { };
                    VerifyErrorInfoTransformer.verifyErrorsRef.set(verifyErrors);
                    try {
                        instrumentation.retransformClasses(classes);
                    } catch (final LinkageError | InternalError error) {
                        verifyErrors.forEach(error::addSuppressed);
                        throw DebugHelper.breakpointBeforeThrow(error);
                    } finally { VerifyErrorInfoTransformer.verifyErrorsRef.set(null); }
                }
            } finally {
                preLoadedClasses.clear();
                lock.unlock();
            }
        }
        
    }
    
    @Getter
    final String name;
    
    @Getter
    @Default
    Environment environment = Environment.local();
    
    @Getter
    final Sampler<String> sampler = MahoProfile.sampler(STR."\{getClass().getCanonicalName()}#\{name()}");
    
    final ConcurrentLinkedQueue<RemapHandler> remapHandlers = { };
    
    public boolean addRemapHandler(final RemapHandler remapHandler) = remapHandlers.add(remapHandler);
    
    public boolean removeRemapHandler(final RemapHandler remapHandler) = remapHandlers.remove(remapHandler);
    
    @Override
    public Stream<RemapHandler> remapHandlers() = remapHandlers.stream();
    
    @Override
    public boolean hasRemapHandlers() = !remapHandlers.isEmpty();
    
    @Getter
    final MapTable<
            Class<? extends BaseTransformer>,
            Class<? extends Annotation>,
            Class<? extends BaseTransformer>> transformerTable = MapTable.ofConcurrentHashMapTable();
    
    final ReentrantReadWriteLock lock = { };
    
    final ReentrantReadWriteLock.ReadLock read = lock.readLock();
    
    final ReentrantReadWriteLock.WriteLock write = lock.writeLock();
    
    public static final String ANY = "*";
    
    final ConcurrentHashMap<String, CopyOnWriteArrayList<ClassTransformer>> transformerMap = { };
    
    public Class<? extends BaseTransformer> lookupTransformerType(final Class<? extends BaseTransformer<?>> type) {
        if (ReflectionHelper.anyMatch(type, ReflectionHelper.ABSTRACT) || type.isEnum())
            throw new IllegalArgumentException("Transformer can only be a class that can be instantiated");
        if (BaseTransformer.class.isAssignableFrom(type)) {
            if (MethodTransformer.class.isAssignableFrom(type))
                return MethodTransformer.class;
            if (FieldTransformer.class.isAssignableFrom(type))
                return FieldTransformer.class;
            return BaseTransformer.class;
        }
        throw new IllegalArgumentException(STR."\{type} is not assignable from \{BaseTransformer.class.getName()}");
    }
    
    public Class<? extends BaseTransformer> lookupTransformerType(final Member member) {
        if (member instanceof Executable)
            return MethodTransformer.class;
        if (member instanceof Field)
            return FieldTransformer.class;
        throw new IllegalArgumentException(STR."Unknown type: \{member.getClass()}");
    }
    
    public Stream<ClassTransformer> lookupTransformer(final @Nullable Class<?> clazz, final @Nullable ClassLoader loader, final String name) {
        read.lock();
        try {
            return Stream.concat(transformerMap[name]?.stream() ?? Stream.<ClassTransformer>empty(), transformerMap[ANY]?.stream()?.filter(filter(clazz, loader, name)) ?? Stream.<ClassTransformer>empty());
        } finally { read.unlock(); }
    }
    
    public static Predicate<ClassTransformer> filter(final @Nullable Class<?> clazz, final @Nullable ClassLoader loader, final String name)
            = clazz != null ? transformer -> transformer.isTarget(clazz) : transformer -> transformer.isTarget(loader, name);
    
    volatile boolean ready;
    
    public synchronized void addToInstrumentation() {
        if (ready)
            return;
        write.lock();
        try {
            Maho.instrumentation().addTransformer(this, true);
        } finally { write.unlock(); }
        ready = true;
    }
    
    public synchronized void removeFromInstrumentation() {
        write.lock();
        try {
            final Instrumentation instrumentation = Maho.instrumentation();
            instrumentation.removeTransformer(this);
            if (instrumentation.removeTransformer(this))
                DebugHelper.breakpoint();
        } finally { write.unlock(); }
        ready = false;
    }
    
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
    public static class Context {
        
        private static final Function<String, ConcurrentLinkedQueue<ClassTransformer>> queueMaker = _ -> new ConcurrentLinkedQueue<>();
        
        TransformerManager manager;
        
        @Nullable
        ClassLoader loader;
        
        ConcurrentHashMap<String, ConcurrentLinkedQueue<ClassTransformer>> transformerMap = { };
        
        ConcurrentLinkedQueue<Marker> markers = { };
        
        ConcurrentLinkedQueue<Future<?>> asyncTasks = { };
        
        ConcurrentLinkedQueue<Runnable> setupCallbacks = { };
        
        public void addSetupCallback(final Runnable callback) = setupCallbacks += callback;
        
        public void addTransformer(final ClassTransformer transformer) {
            logAddTransformer(transformer);
            if (transformer instanceof ClassTransformer.Limited limited && limited.limited())
                limited.targets().forEach(target -> transformerMap.computeIfAbsent(target, queueMaker) += limited);
            else
                transformerMap.computeIfAbsent(ANY, queueMaker) += transformer;
            if (transformer instanceof DerivedTransformer derived)
                derived.derivedTransformers().forEach(this::addTransformer);
        }
        
        private void logAddTransformer(final ClassTransformer transformer) {
            final String info;
            if (transformer instanceof Enum e)
                info = STR."\{e.getClass().getCanonicalName()}#\{e.name()}";
            else
                info = transformer.toString();
            Maho.trace(STR."AddTransform -> \{info}");
        }
        
        public <T extends BaseTransformer<?>> void addTransformerBase(final T transformer, final @Nullable ClassLoader loader) {
            transformer.contextClassLoader(loader);
            if (transformer.valid())
                addTransformer(transformer);
            if (transformer instanceof Marker marker)
                markers += marker;
        }
        
        @SneakyThrows
        public Stream<ResourcePath.ClassInfo> scanMayCachedList(final ResourcePath path, final String cachedPath) {
            final ArrayList<ResourcePath.ClassInfo> classInfoFromCachedList = { };
            final ResourcePath sub = path.sub(resourceTree -> {
                final @Nullable ResourcePath.ResourceInfo cacheInfo = resourceTree.findResource(cachedPath);
                if (cacheInfo != null) {
                    Files.readAllLines(cacheInfo.path(), StandardCharsets.UTF_8).stream()
                            .map(resourceTree::findClassInfo)
                            .nonnull()
                            .forEach(classInfoFromCachedList::add);
                    return false;
                }
                return true;
            });
            return Stream.concat(classInfoFromCachedList.stream(), sub.classes());
        }
        
        public void scan(final ResourcePath path, final Predicate<ResourcePath.ClassInfo> filter) {
            scanMarks(path, filter);
            scanProviders(path, filter);
        }
        
        @SneakyThrows
        public void scanProviders(final ResourcePath path, final Predicate<ResourcePath.ClassInfo> filter)
                = await(scanMayCachedList(path, "transform-providers").filter(filter).map(info -> async(() -> scanProvider(info, ASMHelper.newClassNode(info.readAll())), Setup.executor())));
        
        public void scanProvider(final ClassLoadable classLoadable, final ClassNode node, final boolean redefine = false) {
            if (!redefine)
                if (ASMHelper.hasAnnotation(node, Transformer.class))
                    if (classLoadable.load(false, loader).defaultInstance() instanceof ClassTransformer transformer)
                        addTransformer(transformer);
                    else
                        throw new IllegalArgumentException("The target marked by @Transformer can only be an implementation of ClassTransformer");
            final @Nullable ClassLoader contextLoader = classLoadable instanceof ClassLoadable.Loaded loaded ? loaded.loadedClass().getClassLoader() : loader;
            final @Nullable Map<Class<? extends Annotation>, Class<? extends BaseTransformer>> base = manager.transformerTable[BaseTransformer.class];
            if (base != null && !ASMHelper.hasAnnotation(node, TransformProvider.Exception.class))
                base.forEach((annotationType, handlerType) -> {
                    final @Nullable Annotation annotation = ASMHelper.findAnnotation(node, annotationType, contextLoader);
                    if (annotation != null)
                        addTransformerBase(newInstance(handlerType, annotation, node), contextLoader);
                });
            if (ASMHelper.hasAnnotation(node, TransformProvider.class)) {
                manager.transformerTable[FieldTransformer.class]?.forEach((annotationType, handlerType) -> {
                    for (final FieldNode fieldNode : node.fields)
                        if (!ASMHelper.hasAnnotation(fieldNode, TransformProvider.Exception.class)) {
                            final @Nullable Annotation annotation = ASMHelper.findAnnotation(fieldNode, annotationType, contextLoader);
                            if (annotation != null)
                                addTransformerBase(newInstance(handlerType, annotation, node, fieldNode), contextLoader);
                        }
                });
                manager.transformerTable[MethodTransformer.class]?.forEach((annotationType, handlerType) -> {
                    for (final MethodNode methodNode : node.methods)
                        if (!ASMHelper.hasAnnotation(methodNode, TransformProvider.Exception.class)) {
                            final @Nullable Annotation annotation = ASMHelper.findAnnotation(methodNode, annotationType, contextLoader);
                            if (annotation != null)
                                addTransformerBase(newInstance(handlerType, annotation, node, methodNode), contextLoader);
                        }
                });
            }
        }
        
        @SneakyThrows
        public <A extends Annotation, T extends BaseTransformer<A>> T newInstance(final Class<T> handlerType, final A annotation, final ClassNode node, final @Nullable Object subNode = null) = debugCall(() -> {
            final MethodHandle constructor = MethodHandleHelper.lookup().findConstructor(handlerType, subNode != null ?
                    MethodType.methodType(void.class, TransformerManager.class, annotation.annotationType(), ClassNode.class, subNode.getClass()) :
                    MethodType.methodType(void.class, TransformerManager.class, annotation.annotationType(), ClassNode.class));
            return subNode != null ? (T) constructor.invoke(manager, annotation, node, subNode) : (T) constructor.invoke(manager, annotation, node);
        }, subNode != null ? STR."\{node.name}#\{subNode instanceof MethodNode methodNode ? methodNode.name + methodNode.desc : ((FieldNode) subNode).name}" : node.name);
        
        private static <T> T debugCall(final Supplier<T> supplier, final Function<Throwable, T> handler) { try { return supplier.get(); } catch (final Throwable throwable) { return handler.apply(throwable); } }
        
        @SneakyThrows
        private static <T> T debugCall(final Supplier<T> supplier, final String information) = debugCall(supplier, throwable -> {
            throwable.addSuppressed(new ExtraInformationThrowable(information));
            throw throwable;
        });
        
        @SneakyThrows
        protected void scanMarks(final ResourcePath path, final Predicate<ResourcePath.ClassInfo> filter)
                = await(scanMayCachedList(path, "transform-marks").filter(filter).map(info -> async(() -> scanMarks(info, ASMHelper.newClassNode(info.readAll(), ClassReader.SKIP_CODE)), Setup.executor())));
        
        protected void scanMarks(final ResourcePath.ClassInfo info, final ClassNode node) throws IOException {
            final @Nullable TransformMark mark = ASMHelper.findAnnotation(node, TransformMark.class, loader);
            if (mark != null) {
                final Class<? extends Annotation> annotationType = (Class<? extends Annotation>) info.load(false, loader);
                for (final Class<? extends BaseTransformer<?>> handlerType : mark.value()) {
                    if (!BaseTransformer.class.isAssignableFrom(handlerType))
                        throw new IncompatibleClassChangeError(STR."\{handlerType} is not an instance of \{BaseTransformer.class.getName()}");
                    // Avoid the default values of these annotations from being loaded while the transformer is working and thus failing.
                    // Recursively triggering the transform when the transformer is working will result in not getting the target class bytecode(pass in a null pointer).
                    AnnotationHandler.initDefaultValue(annotationType);
                    manager.transformerTable.put(manager.lookupTransformerType(handlerType), annotationType, handlerType);
                }
            }
        }
        
        public void runMarker(final boolean advance) = await(markers.stream()
                .cast(Marker.class)
                .filter(marker -> marker.advance() == advance)
                .map(marker -> async(() -> {
                    if (!(marker instanceof BaseTransformer baseTransformer) || baseTransformer.nodeFilter().test(null))
                        marker.onMark(this);
                }, Setup.executor())));
        
        public void mergeTransformers() {
            if (transformerMap.isEmpty())
                return;
            final ReentrantReadWriteLock.WriteLock lock = manager.write;
            lock.lock();
            try {
                transformerMap.forEach((target, queue) -> {
                    final @Nullable CopyOnWriteArrayList<ClassTransformer> prev = manager.transformerMap[target];
                    final ClassTransformer array[];
                    if (prev == null)
                        array = queue.toArray(new ClassTransformer[0]);
                    else {
                        final LinkedList<ClassTransformer> copy = { prev };
                        queue.stream()
                                .filter(transformer -> copy.stream().noneMatch(identity -> identity == transformer))
                                .forEach(copy::add);
                        if (prev.size() == copy.size())
                            return;
                        array = copy.toArray(new ClassTransformer[0]);
                    }
                    ArrayHelper.sort(array, ClassTransformer::compareTo);
                    manager.transformerMap[target] = { array };
                });
            } finally { lock.unlock(); }
        }
        
        public void separateTransformers() {
            if (transformerMap.isEmpty())
                return;
            final ReentrantReadWriteLock.WriteLock lock = manager.write;
            lock.lock();
            try {
                transformerMap.forEach((target, queue) -> {
                    final @Nullable CopyOnWriteArrayList<ClassTransformer> prev = manager.transformerMap[target];
                    if (prev != null) {
                        final LinkedList<ClassTransformer> copy = { prev };
                        queue.stream()
                                .filter(transformer -> copy.stream().noneMatch(identity -> identity == transformer))
                                .forEach(copy::add);
                        if (copy.isEmpty())
                            manager.transformerMap -= target;
                        else {
                            final ClassTransformer array[] = copy.toArray(new ClassTransformer[0]);
                            ArrayHelper.sort(array, ClassTransformer::compareTo);
                            manager.transformerMap[target] = { array };
                        }
                    }
                });
            } finally { lock.unlock(); }
            patch();
        }
        
        public void patch(final Collection<? extends ClassTransformer> rollbackTransformers = List.of()) = Patcher.patch(Stream.concat(transformerMap.values().stream().flatMap(Collection::stream), rollbackTransformers.stream()).toList());
        
        public void setup(final @Nullable ResourcePath path, final AOTTransformer.Level level, final boolean aot, final Collection<? extends ClassTransformer> rollbackTransformers = List.of()) {
            mergeTransformers();
            if (!aot)
                runMarker(true);
            asyncTasks.forEach(AsyncHelper::await);
            if (!aot) {
                patch(rollbackTransformers);
                runMarker(false);
            } else {
                final ReentrantReadWriteLock.WriteLock lock = manager.write;
                lock.lock();
                try {
                    final ConcurrentHashMap<String, CopyOnWriteArrayList<ClassTransformer>> transformerMap = manager.transformerMap;
                    if (path != null) {
                        final Set<String> classesNames = path.classes().map(ResourcePath.ClassInfo::className).collect(Collectors.toSet());
                        transformerMap.values().forEach(collection -> collection.removeIf(transformer -> !transformer.canAOT() || transformer.aotLevel() > level ||
                                                                                                         transformer instanceof ClassTransformer.Limited limited && limited.targets().stream().noneMatch(classesNames::contains)));
                        transformerMap.values().removeIf(Collection::isEmpty);
                    }
                    final HashMap<String, AOTTransformer> aotTransformerMap = { };
                    List.copyOf(transformerMap.values()).stream()
                            .flatMap(Collection::stream)
                            .cast(BaseTransformer.class)
                            .forEach(transformer -> transformer.onAOT(aotTransformerMap.computeIfAbsent(ASMHelper.sourceName(transformer.sourceClass.name), AOTTransformer::new)));
                    aotTransformerMap.forEach((target, aotTransformer) -> transformerMap.computeIfAbsent(target, _ -> new CopyOnWriteArrayList<>()) += aotTransformer);
                } finally { lock.unlock(); }
            }
            if (!aot) {
                setupCallbacks.forEach(Runnable::run);
                setupCallbacks.clear();
            }
        }
        
    }
    
    @SneakyThrows
    public synchronized void setup(final @Nullable ClassLoader loader, final ResourcePath path, final AOTTransformer.Level level, final String debugInfo, final Predicate<ResourcePath.ClassInfo> filter = _ -> true) {
        final boolean aot = this != runtime();
        if (!aot)
            addToInstrumentation();
        Maho.debug(STR."Setup start: \{debugInfo}");
        final Context context = { this, loader };
        try {
            final ResourcePath scanPath = path.sub(resourceTree -> {
                final @Nullable ResourcePath.ClassInfo info = resourceTree.findModuleInfo();
                if (info != null) {
                    final ClassNode node = ASMHelper.newClassNode(info.readAll());
                    return ASMHelper.hasAnnotation(node, TransformProvider.class);
                }
                return false;
            });
            context.scan(scanPath, filter);
            context.setup(path, level, aot);
        } catch (final Throwable t) {
            Maho.debug(STR."Setup failed: \{debugInfo}");
            t.printStackTrace();
            throw DebugHelper.breakpointBeforeThrow(t);
        } finally { Maho.debug(STR."Setup end: \{debugInfo}"); }
    }
    
    public synchronized void redefine(final ClassDefinition... definitions) {
        if (this != runtime())
            throw DebugHelper.breakpointBeforeThrow(new UnsupportedOperationException("redefine non-runtime TransformerManger"));
        final List<ClassDefinition> providers = Stream.of(definitions).filter(definition -> definition.getDefinitionClass().isAnnotationPresent(TransformProvider.class)).toList();
        final Set<String> names = providers.stream().map(definition -> definition.getDefinitionClass().getName()).map(ASMHelper::className).collect(Collectors.toSet());
        final ReentrantReadWriteLock.WriteLock lock = write;
        lock.lock();
        try {
            final ArrayList<BaseTransformer<?>> rollbackTransformers = { };
            transformerMap.values().forEach(list -> list.removeIf(transformer -> {
                if (transformer instanceof BaseTransformer<?> baseTransformer && names[baseTransformer.sourceClass.name]) {
                    rollbackTransformers += baseTransformer;
                    return true;
                }
                return false;
            }));
            final Context context = { this, TransformerManager.class.getClassLoader() };
            providers.forEach(definition -> {
                final ClassLoadable.Loaded loaded = { definition.getDefinitionClass() };
                final ClassNode node = ASMHelper.newClassNode(definition.getDefinitionClassFile());
                context.scanProvider(loaded, node, true);
            });
            context.setup(null, AOTTransformer.Level.RUNTIME, false, rollbackTransformers);
        } finally { lock.unlock(); }
    }
    
    public synchronized Context setupRuntimeClass(final Class<?> clazz, final ClassNode node = Maho.getClassNodeFromClassNonNull(clazz)) {
        final Context context = { this, clazz.getClassLoader() };
        final ClassLoadable.Loaded loaded = { clazz };
        context.scanProvider(loaded, node);
        context.setup(null, AOTTransformer.Level.RUNTIME, this != runtime());
        return context;
    }
    
    public List<ClassTransformer> debugClassTransformer(final int count = 0) {
        final Map<String, Class> classes = Stream.of(Maho.instrumentation().getAllLoadedClasses()).collect(Collectors.toMap(Class::getName, Function.identity(), (a, _) -> a));
        final ArrayList<ClassTransformer> result = { };
        final ClassNode fakeNode = { };
        write.lock();
        try {
            transformerMap.forEach((target, transformers) -> {
                if (classes.containsKey(target))
                    transformers.stream()
                            .cast(BaseTransformer.class)
                            .filter(transformer -> transformer.debugTransformCount() == count)
                            .filter(transformer -> transformer.nodeFilter().test(fakeNode))
                            .forEach(result::add);
            });
        } finally { write.unlock(); }
        return result;
    }
    
    @Override
    public @Nullable byte[] transform(final @Nullable ClassLoader loader, final @Nullable String name, final @Nullable Class<?> clazz, final @Nullable ProtectionDomain domain, final @Nullable byte bytecode[]) {
        @Nullable String srcName = name;
        List<ClassTransformer> transformers = List.of();
        try {
            final @Nullable ClassReader reader = bytecode == null ? null : new ClassReader(bytecode);
            final @Nullable String internalName = reader != null ? reader.getClassName() : clazz != null ? ASMHelper.className(clazz) : name != null ? ASMHelper.className(name) : null;
            if (internalName == null) {
                if (srcName != null)
                    DebugHelper.breakpoint();
                return null;
            }
            srcName = ASMHelper.sourceName(internalName);
            transformers = lookupTransformer(clazz, loader, srcName).toList();
            if (transformers.isEmpty())
                return null;
            final ClassNode p_node[] = { reader == null ? null : ASMHelper.newClassNode(reader) };
            final ClassWriter writer = { loader };
            writer.mark(p_node[0]);
            final TransformContext context = bytecode == null ? writer.context() : writer.context(bytecode);
            transformers.forEach(transformer -> p_node[0] = transform(context, p_node[0], transformer, loader, clazz, domain));
            final @Nullable byte result[] = writeBytecodeAndMark(p_node[0], context, loader);
            if (result != null && runtime() == this && DebugDumper.state()) {
                DebugDumper.dumpBytecode(internalName, bytecode, DebugDumper.dump_transform_source);
                DebugDumper.dumpBytecode(internalName, result, DebugDumper.dump_transform_result);
            }
            return result;
        } catch (final Throwable throwable) {
            transformers.stream()
                    .map(transformer -> transformer instanceof Enum<?> e ? STR."\{e.getClass().getCanonicalName()}#\{e.name()}" : ObjectHelper.toString(transformer))
                    .map(ExtraInformationThrowable::new)
                    .forEach(throwable::addSuppressed);
            throwable.addSuppressed(new ExtraInformationThrowable(STR."loader: \{loader}, name: \{srcName}"));
            throwable.printStackTrace();
            throw TransformException.of(throwable).let(Maho::fatal);
        }
    }
    
    @SneakyThrows
    public void aot(final Path source, final Path target, final ClassLoader loader, final ToIntFunction<ClassLoader> loaderIndexed, final Predicate<Path> checker = path -> path.toString().endsWith(".class")) {
        ~-target;
        if (checker.test(source)) {
            final byte bytecode[] = Files.readAllBytes(source);
            final ClassReader reader = { bytecode };
            final String name = ASMHelper.sourceName(reader.getClassName());
            final List<ClassTransformer> transformers = lookupTransformer(null, loader, name).filter(ClassTransformer::canAOT).toList();
            if (!transformers.isEmpty()) {
                final ClassNode p_node[] = { ASMHelper.newClassNode(reader) };
                final ClassWriter writer = { loader };
                final TransformContext.WithSource context = writer.mark(p_node[0]).context(loaderIndexed, bytecode);
                transformers.forEach(transformer -> p_node[0] = transform(context, p_node[0], transformer, loader));
                final @Nullable byte result[] = writeBytecodeAndMark(p_node[0], context, loader);
                if (result != null) {
                    result >> target;
                    return;
                }
            }
        }
        source >> target;
    }
    
    public static @Nullable ClassNode transform(final TransformContext context, final @Nullable ClassNode node, final ClassTransformer transformer, final @Nullable ClassLoader loader,
            final @Nullable Class<?> clazz = null, final @Nullable ProtectionDomain domain = null) {
        try {
            final @Nullable ClassNode result = transformer.transform(context, node, loader, clazz, domain);
            return result == null ? node : result;
        } catch (final Throwable throwable) {
            final String name = node == null ? "?" :ASMHelper.sourceName(node.name);
            Maho.error(STR."Throwable in transform \{name} : \{transformer}");
            throwable.addSuppressed(new ExtraInformationThrowable(STR."Class: \{name}"));
            throwable.addSuppressed(new ExtraInformationThrowable(STR."Transformer: \{transformer.toString()}"));
            throw DebugHelper.breakpointBeforeThrow(TransformException.of(throwable));
        }
    }
    
    public @Nullable byte[] writeBytecodeAndMark(final @Nullable ClassNode node, final TransformContext context, final @Nullable ClassLoader loader) {
        if (node == null | !context.modified())
            return null;
        context.writer().mark(node);
        if (!context.aot() && !ASMHelper.hasAnnotation(node, Transformed.class)) {
            final AnnotationNode annotationNode = { Type.getDescriptor(Transformed.class) };
            node.visibleAnnotations == null ? node.visibleAnnotations = new LinkedList<>() : node.visibleAnnotations += annotationNode;
        }
        try (final var _ = sampler()["MahoCompute"]) { return context.compute().writer().toBytecodeNoCompute(node); }
    }
    
    public static void transform(final String name, final String text) = Maho.debug(STR."Transform: <\{name}> \{text}");
    
    public static final class DebugDumper {
        
        public static final Path
                dump_transform_result   = Path.of("dump/transform/result"),
                dump_transform_source   = Path.of("dump/transform/source"),
                dump_transform_generate = Path.of("dump/transform/generate"),
                dump_transform_share    = Path.of("dump/transform/share"),
                dump_shell              = Path.of("dump/shell");
        
        @Getter
        @Setter
        private static OpenOption options[] = { CREATE, WRITE, TRUNCATE_EXISTING };
        
        @Getter
        @Setter
        private static boolean state;
        
        public static void addOptions(final OpenOption... value) = options = ArrayHelper.addAll(options(), value);
        
        static {
            if (state())
                init();
        }
        
        @SneakyThrows
        public static void init() = Stream.of(DebugDumper.class.getDeclaredFields())
                .filter(field -> ReflectionHelper.anyMatch(field, ReflectionHelper.STATIC) && Path.class.isAssignableFrom(field.getType()))
                .forEach(field -> init((Path) field.get(null)));
        
        @SneakyThrows
        public static void init(final Path path) { try { ~--path; } catch (final Throwable e) { e.printStackTrace(); } }
        
        public static void dumpBytecode(final String name, final @Nullable byte bytecode[], final Path dir) {
            if (state() && bytecode != null) {
                final int index = name.lastIndexOf('/');
                final Path dumpTargetPath = workDirectory() / (index == -1 ? dir : dir / name.substring(0, index));
                try {
                    ~dumpTargetPath;
                    final String stdName = (index == -1 ? name : name.substring(index + 1)).replace('/', '.');
                    Files.write(dumpTargetPath / STR."\{stdName}.class", bytecode, options);
                    final ByteArrayOutputStream output = { 1024 * 16 };
                    try {
                        ASMHelper.printBytecode(new ClassReader(bytecode), output);
                    } catch (final Throwable throwable) { DebugHelper.breakpoint(); }
                    Files.write(dumpTargetPath / STR."\{stdName}.bytecode", output.toByteArray(), options);
                } catch (final IOException e) { e.printStackTrace(); }
            }
        }
        
    }
    
    static { DebugDumper.state(Environment.local().lookup(MAHO_DEBUG_DUMP_BYTECODE, debug())); }
    
}
