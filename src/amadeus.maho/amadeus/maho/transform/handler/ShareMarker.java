package amadeus.maho.transform.handler;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Future;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import amadeus.maho.core.Maho;
import amadeus.maho.core.MahoExport;
import amadeus.maho.core.extension.MagicAccessor;
import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.TransformerManager;
import amadeus.maho.transform.handler.base.marker.BaseMarker;
import amadeus.maho.transform.mark.Erase;
import amadeus.maho.transform.mark.Share;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.remap.ClassNameRemapper;
import amadeus.maho.util.concurrent.AsyncHelper;
import amadeus.maho.util.resource.ResourcePath;
import amadeus.maho.util.tuple.Tuple;
import amadeus.maho.util.tuple.Tuple2;
import amadeus.maho.vm.transform.handler.HotSpotJITMarker;
import amadeus.maho.vm.transform.mark.HotSpotJIT;

import static amadeus.maho.util.concurrent.AsyncHelper.async;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;

@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class ShareMarker extends BaseMarker<Share> {
    
    private static final ConcurrentLinkedQueue<Tuple2<Queue<String>, Runnable>> markerQueue = { };
    
    private static final ConcurrentHashMap<String, Class<?>> loadedMapping = { };
    
    private static volatile boolean init;
    
    @Override
    public void onMark() {
        synchronized (markerQueue) {
            block:
            {
                if (annotation.required().length == 0) {
                    if (init || ASMHelper.className(Share.class).equals(sourceClass.name))
                        break block;
                    else
                        markerQueue.add(Tuple.tuple(new ConcurrentLinkedQueue<>(Set.of(Share.class.getName())), this::onShare));
                } else {
                    final Queue<String> required = new ConcurrentLinkedQueue<>(List.of(annotation.required()));
                    required.add(Share.class.getName());
                    loadedMapping.keySet().forEach(required::remove);
                    if (required.isEmpty())
                        break block;
                    else
                        markerQueue.add(Tuple.tuple(required, this::onShare));
                }
                return;
            }
        }
        onShare();
    }
    
    @SneakyThrows
    public void onShare() {
        ClassNode node = ASMHelper.newClassNode(sourceClass);
        TransformerManager.transform("share", ASMHelper.sourceName(node.name) + (handler.isNotDefault(Share::value) ? "\n->  " + annotation.value().getName() :
                handler.isNotDefault(Share::target) ? "\n->  " + annotation.target() : ""));
        if (annotation.privilegeEscalation())
            node.superName = MagicAccessor.Bridge;
        if (annotation.shareAnonymousInnerClass()) {
            final @Nullable ResourcePath contextResourcePath = manager.context()?.resourcePath() ?? null;
            if (contextResourcePath != null)
                node.innerClasses.stream()
                        .filter(innerClassNode -> ASMHelper.isAnonymousInnerClass(innerClassNode.name))
                        .forEach(innerClassNode -> {
                            final String name = ASMHelper.sourceName(innerClassNode.name);
                            final ResourcePath.ClassInfo innerClassInfo = contextResourcePath.classes()
                                    .parallel()
                                    .filter(classInfo -> classInfo.className().equals(name))
                                    .findAny()
                                    .orElseThrow();
                            final byte bytecode[] = innerClassInfo.readAll();
                            if (handler.isNotDefault(Share::value)) {
                                final String newName = name.replace(ASMHelper.sourceName(sourceClass.name), annotation.value().getName());
                                TransformerManager.transform("share", "%s\n->  %s".formatted(name, newName));
                                Maho.shareClass(newName, ClassNameRemapper.changeName(bytecode, name, newName), null);
                            } else {
                                TransformerManager.transform("share", name);
                                Maho.shareClass(name, bytecode, null);
                            }
                        });
        }
        if (handler.isNotDefault(Share::erase)) {
            final Erase erase = annotation.erase();
            final List<AnnotationNode> reservedAnnotationNodes = reservedAnnotationNodes(node, Retention.class, Target.class);
            node = new EraseTransformer(manager, erase, node).transformWithoutContext(node, null);
            if (node.visibleAnnotations == null && reservedAnnotationNodes.size() != 0)
                node.visibleAnnotations = reservedAnnotationNodes;
        }
        if (node.visibleAnnotations == null)
            node.visibleAnnotations = new ArrayList<>();
        if (!ASMHelper.hasAnnotation(node, Share.class))
            node.visibleAnnotations += new AnnotationNode(ASMHelper.classDesc(Share.class));
        if (annotation.makePublic())
            node.access = ASMHelper.changeAccess(node.access, ACC_PUBLIC);
        if (handler.isNotDefault(Share::remap))
            node = new RemapTransformer(manager, annotation.remap(), node).transformWithoutContext(node, null);
        final Class<?> clazz;
        if (handler.isNotDefault(Share::value))
            clazz = Maho.shareClass(ClassNameRemapper.changeName(node, sourceClass.name, annotation.value().getName()));
        else if (handler.isNotDefault(Share::target))
            clazz = Maho.shareClass(ClassNameRemapper.changeName(node, sourceClass.name, annotation.target()));
        else
            clazz = Maho.shareClass(node);
        final @Nullable HotSpotJIT hotSpotJIT = ASMHelper.findAnnotation(sourceClass, HotSpotJIT.class, Share.class.getClassLoader());
        if (hotSpotJIT != null)
            new HotSpotJITMarker(manager, hotSpotJIT, sourceClass).mark(null);
        final String name = clazz.getName();
        synchronized (markerQueue) {
            if (Share.class.getName().equals(name)) {
                final Class<? extends Annotation> bootShare = (Class<? extends Annotation>) clazz;
                TransformerManager.Patcher.needRetransformFilter().add(target -> target
                        .filter(retransformTarget -> manager.context() != null)
                        .filter(retransformTarget -> retransformTarget.isAnnotationPresent(bootShare))
                        .map(_ -> Boolean.FALSE));
                init = true;
            }
            loadedMapping.put(name, clazz);
            final @Nullable Queue<Future<?>> asyncTasks = manager.context()?.asyncTasks() ?? null;
            markerQueue.stream()
                    .filter(tuple -> tuple.v1.remove(name))
                    .filter(tuple -> tuple.v1.isEmpty())
                    .peek(markerQueue::remove)
                    .map(tuple -> async(tuple.v2, MahoExport.Setup.executor()))
                    .forEach(asyncTasks != null ? asyncTasks::offer : AsyncHelper::await);
        }
        if (handler.isNotDefault(Share::init))
            InitMarker.init(this.annotation.init(), clazz);
    }
    
    @SafeVarargs
    protected final List<AnnotationNode> reservedAnnotationNodes(final ClassNode node, final Class<? extends Annotation>... annotationTypes) {
        final List<AnnotationNode> result = new ArrayList<>(annotationTypes.length);
        for (final var annotationType : annotationTypes) {
            final @Nullable AnnotationNode annotationNodes[] = ASMHelper.findAnnotationNodes(node.visibleAnnotations, annotationType);
            if (annotationNodes != null)
                result *= List.of(annotationNodes);
            else {
                final @Nullable AnnotationNode annotationNode = ASMHelper.findAnnotationNode(node.visibleAnnotations, annotationType);
                if (annotationNode != null)
                    result += annotationNode;
            }
        }
        return result;
    }
    
}
