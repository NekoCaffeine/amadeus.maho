package amadeus.maho.transform;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import amadeus.maho.core.Maho;
import amadeus.maho.core.MahoProfile;
import amadeus.maho.lang.AccessLevel;
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
import amadeus.maho.util.container.MapTable;
import amadeus.maho.util.control.FunctionChain;
import amadeus.maho.util.dynamic.CallerContext;
import amadeus.maho.util.function.FunctionHelper;
import amadeus.maho.util.misc.Environment;
import amadeus.maho.util.profile.Sampler;
import amadeus.maho.util.resource.ResourcePath;
import amadeus.maho.util.runtime.ArrayHelper;
import amadeus.maho.util.runtime.MethodHandleHelper;
import amadeus.maho.util.runtime.ReflectionHelper;
import amadeus.maho.util.throwable.ExtraInformationThrowable;
import amadeus.maho.vm.transform.mark.HotSpotJIT;
import amadeus.maho.vm.transform.mark.HotSpotMethodFlags;

import static amadeus.maho.core.MahoExport.*;
import static amadeus.maho.util.concurrent.AsyncHelper.*;
import static amadeus.maho.vm.reflection.hotspot.KlassMethod.Flags._force_inline;
import static java.nio.file.StandardOpenOption.*;
import static org.objectweb.asm.Opcodes.*;

@HotSpotJIT
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TransformerManager implements ClassFileTransformer, StreamRemapHandler {
    
    @Getter
    private static final TransformerManager runtime = { "runtime" };
    
    @Getter
    @NoArgsConstructor(AccessLevel.PRIVATE)
    public static final class Patcher {
        
        private static final FunctionChain<Class<?>, Boolean> needRetransformFilter = { };
        
        public static void patch(final TransformRange transformer) = patch(List.of(transformer));
        
        @SneakyThrows
        public static synchronized void patch(final Collection<? extends TransformRange> transformers) {
            final Instrumentation instrumentation = Maho.instrumentation();
            final Class classes[] = Stream.of(instrumentation.getAllLoadedClasses())
                    .parallel()
                    .filter(instrumentation::isModifiableClass)
                    .filter(clazz -> transformers.stream().anyMatch(transformer -> transformer.isTarget(clazz)))
                    .filter(clazz -> needRetransformFilter().apply(clazz).orElse(Boolean.TRUE))
                    .toArray(Class[]::new);
            instrumentation.retransformClasses(classes);
        }
        
    }
    
    private static final String TRANSFORMER_MANAGER = ASMHelper.className(TransformerManager.class);
    
    private static final ConcurrentHashMap<String, Map<String, Runnable>> callbackMapping = { };
    
    private static final String CallbackFlag = ASMHelper.className(TransformerManager.class.getName() + "$CallbackFlag");
    
    private static ClassNode makeCallbackFlag() {
        final ClassNode result = { };
        result.name = CallbackFlag;
        result.access = ACC_PUBLIC | ACC_SYNTHETIC | ACC_INTERFACE;
        result.version = bytecodeVersion();
        return result;
    }
    
    static { Maho.shareClass(makeCallbackFlag()); }
    
    public static void markClinitCallback(final ClassNode node, final Runnable runnable, final String debugInfo) {
        callbackMapping.computeIfAbsent(node.name, FunctionHelper.abandon(HashMap::new)).put(debugInfo, runnable);
        for (final String itf : node.interfaces)
            if (itf.equals(CallbackFlag))
                return;
        node.interfaces += CallbackFlag;
        MethodNode clinit = null;
        for (final MethodNode methodNode : node.methods)
            if (methodNode.name.equals(ASMHelper._CLINIT_)) {
                clinit = methodNode;
                break;
            }
        final boolean flag = clinit == null;
        if (flag)
            node.methods += clinit = { 0, ASMHelper._CLINIT_, ASMHelper.VOID_METHOD_DESC, null, null };
        final InsnList list = { };
        list.add(new LdcInsnNode(node.name));
        list.add(new MethodInsnNode(INVOKESTATIC, TRANSFORMER_MANAGER, "callback", Type.getMethodDescriptor(Type.VOID_TYPE, ASMHelper.TYPE_STRING), false));
        if (flag)
            list.add(new InsnNode(RETURN));
        clinit.instructions.insert(list);
    }
    
    @SneakyThrows
    public static void callback(final String name) {
        final Class<?> caller = CallerContext.caller();
        if (!ASMHelper.className(caller).equals(name))
            throw new IllegalAccessException("Only the target class can call its own initialization callback, caller: " + caller);
        final @Nullable Map<String, Runnable> callback = callbackMapping[name];
        if (callback == null)
            return;
        transform("clinit", "callback: %s - %s".formatted(name, String.join(", ", callback.keySet())));
        callback.forEach((debugInfo, runnable) -> {
            try {
                runnable.run();
            } catch (final Throwable throwable) {
                throwable.addSuppressed(new ExtraInformationThrowable("Throwable in callback " + name + " : " + debugInfo));
                throw throwable;
            }
        });
    }
    
    @Getter
    final String name;
    
    @Getter
    final Sampler<String> sampler = MahoProfile.sampler(getClass().getCanonicalName() + "#" + name());
    
    final ConcurrentLinkedQueue<RemapHandler> remapHandlers = { };
    
    public boolean addRemapHandler(final RemapHandler remapHandler) = remapHandlers.add(remapHandler);
    
    public boolean removeRemapHandler(final RemapHandler remapHandler) = remapHandlers.remove(remapHandler);
    
    @Override
    public Stream<RemapHandler> remapHandlers() = remapHandlers.stream();
    
    @Override
    public boolean hasRemapHandlers() = !remapHandlers.isEmpty();
    
    final MapTable<
            Class<? extends BaseTransformer>,
            Class<? extends Annotation>,
            Class<? extends BaseTransformer>> transformerTable = MapTable.newConcurrentHashMapTable();
    
    final ConcurrentLinkedQueue<ClassTransformer> transformers = { };
    
    final ConcurrentHashMap<String, Collection<ClassTransformer.Limited>> limitedTransformers = { };
    
    final ConcurrentLinkedQueue<Runnable> setupCallbacks = { };
    
    public void addSetupCallback(final Runnable callback) = setupCallbacks += callback;
    
    public Comparator<ClassTransformer> transformerComparator() = ClassTransformer::compareTo;
    
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
        throw new IllegalArgumentException(type + " is not assignable from " + BaseTransformer.class.getName());
    }
    
    public Class<? extends BaseTransformer> lookupTransformerType(final Member member) {
        if (member instanceof Executable)
            return MethodTransformer.class;
        if (member instanceof Field)
            return FieldTransformer.class;
        throw new IllegalArgumentException("Unknown type: " + member.getClass());
    }
    
    public Stream<ClassTransformer> lookupTransformer(final @Nullable Class<?> clazz, final @Nullable ClassLoader loader, final String name) {
        final @Nullable Collection<ClassTransformer.Limited> collection = limitedTransformers[name];
        return Stream.concat(collection == null ? Stream.empty() : collection.stream(), transformers.stream().filter(filter(clazz, loader, name)));
    }
    
    public static Predicate<ClassTransformer> filter(final @Nullable Class<?> clazz, final @Nullable ClassLoader loader, final @Nullable String name)
            = clazz != null ? transformer -> transformer.isTarget(clazz) : transformer -> transformer.isTarget(loader, name);
    
    public void addTransformer(final ClassTransformer transformer, final @Nullable Collection<ClassTransformer> patchTransformers) {
        logAddTransformer(transformer);
        if (patchTransformers != null)
            patchTransformers += transformer;
        if (transformer instanceof ClassTransformer.Limited limited && limited.limited())
            limited.targets().forEach(target -> limitedTransformers.computeIfAbsent(target, FunctionHelper.abandon(ConcurrentLinkedQueue::new)) += limited);
        else
            transformers += transformer;
        if (transformer instanceof DerivedTransformer derived)
            derived.derivedTransformers().forEach(derivedTransformer -> addTransformer(derivedTransformer, patchTransformers));
        if (context() == null) // exponential disaster
            sortTransformers();
    }
    
    protected void logAddTransformer(final ClassTransformer transformer) {
        final String info;
        if (transformer instanceof Enum e)
            info = e.getClass().getCanonicalName() + "#" + e.name();
        else
            info = transformer.toString();
        Maho.debug("AddTransform -> " + info);
    }
    
    public <T extends BaseTransformer<?>> void addTransformerBase(final T transformer, final ClassLoader loader, final @Nullable Collection<ClassTransformer> patchTransformers) {
        transformer.contextClassLoader(loader);
        if (transformer.valid())
            addTransformer(transformer, patchTransformers);
        else if (patchTransformers != null)
            patchTransformers += transformer;
    }
    
    public Stream<Collection<ClassTransformer>> transformersStream() = Stream.concat(Stream.of(transformers), limitedTransformers.values().stream().map(FunctionHelper.cast()));
    
    public void sortTransformers() = transformersStream().forEach(queue -> queue.sort(transformerComparator()));
    
    volatile boolean ready;
    
    public boolean isReady() = ready;
    
    public synchronized void addToInstrumentation() {
        if (isReady())
            return;
        Maho.instrumentation().addTransformer(this, true);
        ready = true;
    }
    
    public synchronized void removeFromInstrumentation() {
        while (Maho.instrumentation().removeTransformer(this)) ;
        ready = false;
    }
    
    public record Context(boolean aot, ResourcePath resourcePath, @Nullable ClassLoader loader, Queue<Future<?>> asyncTasks) { }
    
    @Getter
    @Setter
    volatile @Nullable Context context;
    
    public synchronized void setup(final @Nullable ClassLoader loader, final ResourcePath path, final Predicate<ResourcePath.ClassInfo> filter = _ -> true, final String debugInfo,
            final AOTTransformer.Level aotLevel = AOTTransformer.Level.RUNTIME, final boolean scanAnnotation = true, final boolean scanProvider = true) {
        final boolean aot = this != runtime();
        if (!aot)
            addToInstrumentation();
        Maho.debug("Setup start: " + debugInfo);
        final Context srcContext = context(), newContext = { aot, path, loader, new ConcurrentLinkedQueue<>() };
        try {
            context(newContext);
            if (scanAnnotation)
                scanAnnotation(path, filter);
            if (scanProvider) {
                final var transformers = scanProvider(path, filter);
                if (!aot)
                    runMarker(transformers, true);
                newContext.asyncTasks().forEach(AsyncHelper::await);
                sortTransformers();
                if (!aot) {
                    Patcher.patch(transformers);
                    runMarker(transformers, false);
                } else {
                    transformers.removeIf(transformer -> !transformer.canAOT());
                    final Set<String> classesNames = path.classes().map(ResourcePath.ClassInfo::className).collect(Collectors.toSet());
                    limitedTransformers.values().forEach(collection -> collection.removeIf(transformer -> !transformer.canAOT() || transformer.aotLevel() > aotLevel || !classesNames.containsAll(transformer.targets())));
                    limitedTransformers.values().removeIf(Collection::isEmpty);
                    new HashMap<>(limitedTransformers).forEach((name, collection) -> {
                        final AOTTransformer aotTransformer = { name };
                        collection.stream().cast(BaseTransformer.class).forEach(baseTransformer -> baseTransformer.onAOT(aotTransformer));
                        limitedTransformers[name] += aotTransformer;
                    });
                }
            }
            if (!aot)
                setupCallbacks.forEach(Runnable::run);
        } catch (final Throwable t) {
            t.printStackTrace();
            throw t;
        } finally {
            setupCallbacks.clear();
            context(srcContext);
            Maho.debug("Setup end: " + debugInfo);
        }
    }
    
    public void runMarker(final Collection<ClassTransformer> patchTransformers, final boolean advance) = await(patchTransformers.stream()
            .cast(Marker.class)
            .filter(marker -> marker.advance() == advance)
            .map(marker -> async(() -> {
                if (!(marker instanceof BaseTransformer) || ((BaseTransformer<?>) marker).nodeFilter().test(null))
                    marker.onMark();
            }, Setup.executor())));
    
    @SneakyThrows
    protected void scanAnnotation(final ResourcePath path, final Predicate<ResourcePath.ClassInfo> filter)
            = await(path.classes().filter(filter).map(info -> async(() -> scanAnnotation(info, ASMHelper.newClassNode(info.readAll(), ClassReader.SKIP_CODE)), Setup.executor())));
    
    protected void scanAnnotation(final ResourcePath.ClassInfo info, final ClassNode node) throws IOException {
        final @Nullable ClassLoader loader = context().loader();
        final TransformMark mark = ASMHelper.findAnnotation(node, TransformMark.class, loader);
        if (mark != null) {
            final Class<? extends Annotation> annotationType = (Class<? extends Annotation>) info.load(false, loader);
            for (final Class<? extends BaseTransformer<?>> handlerType : mark.value()) {
                if (!BaseTransformer.class.isAssignableFrom(handlerType))
                    throw new IncompatibleClassChangeError(handlerType + " is not an instance of " + BaseTransformer.class.getName());
                // Avoid the default values of these annotations from being loaded while the transformer is working and thus failing.
                // Recursively triggering the transform when the transformer is working will result in not getting the target class bytecode(pass in a null pointer).
                AnnotationHandler.initDefaultValue(annotationType);
                transformerTable.put(lookupTransformerType(handlerType), annotationType, handlerType);
            }
        }
    }
    
    @SneakyThrows
    protected Collection<ClassTransformer> scanProvider(final ResourcePath path, final Predicate<ResourcePath.ClassInfo> filter)
            = new ConcurrentLinkedQueue<ClassTransformer>().let(it -> await(path.classes().filter(filter).map(info -> async(() -> scanProvider(it, info, ASMHelper.newClassNode(info.readAll())), Setup.executor()))));
    
    protected <T> T debugCall(final Supplier<T> supplier, final Function<Throwable, T> handler) { try { return supplier.get(); } catch (final Throwable throwable) { return handler.apply(throwable); } }
    
    @SneakyThrows
    protected <T> T debugCall(final Supplier<T> supplier, final String information) = debugCall(supplier, throwable -> {
        throwable.addSuppressed(new ExtraInformationThrowable(information));
        throw throwable;
    });
    
    @SneakyThrows
    protected <A extends Annotation, T extends BaseTransformer<A>> T newInstance(final Class<T> handlerType, final A annotation, final ClassNode node, final @Nullable Object subNode = null) = debugCall(() -> {
        final MethodHandle constructor = MethodHandleHelper.lookup().findConstructor(handlerType, subNode != null ?
                MethodType.methodType(void.class, TransformerManager.class, annotation.annotationType(), ClassNode.class, subNode.getClass()) :
                MethodType.methodType(void.class, TransformerManager.class, annotation.annotationType(), ClassNode.class));
        return subNode != null ? (T) constructor.invoke(this, annotation, node, subNode) : (T) constructor.invoke(this, annotation, node);
    }, subNode != null ? "%s#%s".formatted(node.name, subNode instanceof MethodNode methodNode ? methodNode.name + methodNode.desc : ((FieldNode) subNode).name) : node.name);
    
    protected void scanProvider(final Collection<ClassTransformer> patchTransformers, final ResourcePath.ClassInfo info, final ClassNode node) throws IOException {
        final @Nullable ClassLoader loader = context().loader();
        if (ASMHelper.hasAnnotation(node, Transformer.class))
            if (info.load(false, loader).defaultInstance() instanceof ClassTransformer transformer)
                addTransformer(transformer, patchTransformers);
            else
                throw new IllegalArgumentException("The target marked by @Transformer can only be an implementation of ClassTransformer");
        final @Nullable Map<Class<? extends Annotation>, Class<? extends BaseTransformer>> base = transformerTable.get(BaseTransformer.class);
        if (base != null && !ASMHelper.hasAnnotation(node, TransformProvider.Exception.class))
            base.forEach((annotationType, handlerType) -> {
                final @Nullable Annotation annotation = ASMHelper.findAnnotation(node, annotationType, loader);
                if (annotation != null)
                    addTransformerBase(newInstance(handlerType, annotation, node), loader, patchTransformers);
            });
        if (ASMHelper.hasAnnotation(node, TransformProvider.class)) {
            transformerTable.get(FieldTransformer.class)?.forEach((annotationType, handlerType) -> {
                for (final FieldNode fieldNode : node.fields)
                    if (!ASMHelper.hasAnnotation(fieldNode, TransformProvider.Exception.class)) {
                        final @Nullable Annotation annotation = ASMHelper.findAnnotation(fieldNode, annotationType, loader);
                        if (annotation != null)
                            addTransformerBase(newInstance(handlerType, annotation, node, fieldNode), loader, patchTransformers);
                    }
            });
            transformerTable.get(MethodTransformer.class)?.forEach((annotationType, handlerType) -> {
                for (final MethodNode methodNode : node.methods)
                    if (!ASMHelper.hasAnnotation(methodNode, TransformProvider.Exception.class)) {
                        final @Nullable Annotation annotation = ASMHelper.findAnnotation(methodNode, annotationType, loader);
                        if (annotation != null)
                            addTransformerBase(newInstance(handlerType, annotation, node, methodNode), loader, patchTransformers);
                    }
            });
        }
    }
    
    public <T extends BaseTransformer<A>, A extends Annotation> T makeTransformer(final Class<A> annotationType, final ClassNode node, final ClassLoader contextClassLoader) {
        final @Nullable Class<T> handlerType = (Class<T>) transformerTable.get(MethodTransformer.class, annotationType);
        if (handlerType == null)
            throw new IllegalArgumentException("handlerType == null");
        final @Nullable A annotation = ASMHelper.findAnnotation(node, annotationType, contextClassLoader);
        if (annotation == null)
            throw new IllegalArgumentException("annotation == null");
        return newInstance(handlerType, annotation, node).let(it -> it.contextClassLoader(contextClassLoader));
    }
    
    public <T extends FieldTransformer<A>, A extends Annotation> T makeTransformer(final Class<A> annotationType, final ClassNode node, final Field member) {
        final @Nullable Class<T> handlerType = (Class<T>) transformerTable.get(MethodTransformer.class, annotationType);
        if (handlerType == null)
            throw new IllegalArgumentException("handlerType == null");
        final String name = member.getName(), desc = Type.getDescriptor(member.getType());
        for (final FieldNode fieldNode : node.fields)
            if (fieldNode.name.equals(name) && fieldNode.desc.equals(desc)) {
                final @Nullable A annotation = ASMHelper.findAnnotation(fieldNode, annotationType, member.getDeclaringClass().getClassLoader());
                if (annotation == null)
                    throw new IllegalArgumentException("annotation == null");
                return newInstance(handlerType, annotation, node, fieldNode).let(it -> it.contextClassLoader(member.getDeclaringClass().getClassLoader()));
            }
        throw new IllegalArgumentException("Unable to match to target Field");
    }
    
    public <T extends MethodTransformer<A>, A extends Annotation> T makeTransformer(final Class<A> annotationType, final ClassNode node, final Executable member) {
        final @Nullable Class<T> handlerType = (Class<T>) transformerTable.get(MethodTransformer.class, annotationType);
        if (handlerType == null)
            throw new IllegalArgumentException("handlerType == null");
        final String name = member.getName(), desc = member instanceof Method ?
                Type.getMethodDescriptor((Method) member) : Type.getConstructorDescriptor((Constructor<?>) member);
        for (final MethodNode methodNode : node.methods)
            if (methodNode.name.equals(name) && methodNode.desc.equals(desc)) {
                final @Nullable A annotation = ASMHelper.findAnnotation(methodNode, annotationType, member.getDeclaringClass().getClassLoader());
                if (annotation == null)
                    throw new IllegalArgumentException("annotation == null");
                return newInstance(handlerType, annotation, node, methodNode).let(it -> it.contextClassLoader(member.getDeclaringClass().getClassLoader()));
            }
        throw new IllegalArgumentException("Unable to match to target Executable");
    }
    
    @Override
    public @Nullable byte[] transform(final @Nullable ClassLoader loader, final @Nullable String name, final @Nullable Class<?> clazz, final @Nullable ProtectionDomain domain, final @Nullable byte bytecode[]) {
        String srcName = name;
        try {
            final @Nullable ClassReader reader = ASMHelper.newClassReader(bytecode);
            final @Nullable String internalName = reader != null ? reader.getClassName() : clazz != null ? ASMHelper.className(clazz) : name != null ? ASMHelper.className(name) : null;
            if (internalName == null)
                return null;
            srcName = ASMHelper.sourceName(internalName);
            final List<ClassTransformer> transformers = lookupTransformer(clazz, loader, srcName).toList();
            if (transformers.isEmpty())
                return null;
            final ClassNode p_node[] = { ASMHelper.newClassNode(reader, 0) };
            final ClassWriter writer = { loader };
            final TransformContext.WithSource context = writer.mark(p_node[0]).context(bytecode);
            transformers.forEach(transformer -> p_node[0] = transform(context, p_node[0], transformer, loader, clazz, domain));
            final @Nullable byte result[] = writeBytecodeAndMark(p_node[0], context, loader);
            if (result != null) {
                DebugDumper.dumpBytecode(internalName, bytecode, DebugDumper.dump_transform_source);
                DebugDumper.dumpBytecode(internalName, result, DebugDumper.dump_transform_result);
            }
            return result;
        } catch (final Throwable throwable) {
            transformers.stream()
                    .map(transformer -> transformer instanceof Enum<?> e ? e.getClass().getCanonicalName() + "#" + e.name() : transformer.toString())
                    .map(ExtraInformationThrowable::new)
                    .forEach(throwable::addSuppressed);
            throwable.addSuppressed(new ExtraInformationThrowable("loader: %s, name: %s".formatted(loader, srcName)));
            throwable.printStackTrace();
            throw TransformException.of(throwable).let(Maho::fatal);
        }
    }
    
    @SneakyThrows
    public void aot(final Path source, final Path target, final ClassLoader loader, final Predicate<Path> checker = path -> path.toString().endsWith(".class")) {
        ~-target;
        if (checker.test(source)) {
            final byte bytecode[] = Files.readAllBytes(source);
            final ClassReader reader = { bytecode };
            final String name = ASMHelper.sourceName(reader.getClassName());
            final List<ClassTransformer> transformers = lookupTransformer(null, loader, name).filter(ClassTransformer::canAOT).toList();
            if (!transformers.isEmpty()) {
                final ClassNode p_node[] = { ASMHelper.newClassNode(reader, 0) };
                final ClassWriter writer = { loader };
                final TransformContext.WithSource context = writer.mark(p_node[0]).context(true, bytecode);
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
    
    public static @Nullable ClassNode transform(final TransformContext context, final @Nullable ClassNode node,
            final ClassTransformer transformer, final @Nullable ClassLoader loader, final @Nullable Class<?> clazz = null, final @Nullable ProtectionDomain domain = null) {
        try {
            final @Nullable ClassNode result = transformer.transform(context, node, loader, clazz, domain);
            return result == null ? node : result;
        } catch (final Throwable throwable) {
            Maho.error("Throwable in transform " + ASMHelper.sourceName(node.name) + " : " + transformer);
            throw TransformException.of(throwable);
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
        try (final var handle = sampler().handle("MahoCompute")) { return context.compute().writer().toBytecode(node); }
    }
    
    @HotSpotMethodFlags(_force_inline)
    public static void transform(final String name, final String text) = Maho.debug("Transform: <" + name + "> " + text);
    
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
            state(Environment.local().lookup(MAHO_DEBUG_DUMP_BYTECODE, debug()));
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
                    Files.write(dumpTargetPath / (stdName + ".class"), bytecode, options);
                    final ByteArrayOutputStream output = { 1024 * 16 };
                    ASMHelper.dumpBytecode(new ClassReader(bytecode), output);
                    Files.write(dumpTargetPath / (stdName + ".bytecode"), output.toByteArray(), options);
                } catch (final IOException e) { e.printStackTrace(); }
            }
        }
        
    }
    
}
