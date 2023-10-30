package amadeus.maho.transform.handler;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import amadeus.maho.core.Maho;
import amadeus.maho.core.MahoExport;
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
import amadeus.maho.util.bytecode.remap.ClassNameRemapHandler;
import amadeus.maho.util.tuple.Tuple;
import amadeus.maho.util.tuple.Tuple2;

import static amadeus.maho.util.concurrent.AsyncHelper.async;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;

@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class ShareMarker extends BaseMarker<Share> {
    
    private static final ConcurrentLinkedQueue<Tuple2<Queue<String>, Runnable>> markerQueue = { };
    
    private static final ConcurrentHashMap<String, Class<?>> loadedMapping = { };
    
    private static volatile boolean init;
    
    @Override
    public void onMark(final TransformerManager.Context context) {
        synchronized (markerQueue) {
            block:
            {
                if (annotation.required().length == 0) {
                    if (init || ASMHelper.className(Share.class).equals(sourceClass.name))
                        break block;
                    else
                        markerQueue.add(Tuple.tuple(new ConcurrentLinkedQueue<>(Set.of(Share.class.getName())), () -> onShare(context)));
                } else {
                    final Queue<String> required = new ConcurrentLinkedQueue<>(List.of(annotation.required()));
                    required.add(Share.class.getName());
                    loadedMapping.keySet().forEach(required::remove);
                    if (required.isEmpty())
                        break block;
                    else
                        markerQueue.add(Tuple.tuple(required, () -> onShare(context)));
                }
                return;
            }
        }
        onShare(context);
    }
    
    @SneakyThrows
    public void onShare(final TransformerManager.Context context) {
        final @Nullable String target = handler.isNotDefault(Share::value) ?  handler.<Type>lookupSourceValue(Share::value).getClassName() : handler.isNotDefault(Share::target) ? annotation.target() : null;
        ClassNode node = ASMHelper.newClassNode(sourceClass);
        TransformerManager.transform("share", ASMHelper.sourceName(node.name) + (target == null ? "" : "\n->  " + target));
        if (handler.isNotDefault(Share::erase)) {
            final Erase erase = annotation.erase();
            final List<AnnotationNode> reservedAnnotationNodes = reservedAnnotationNodes(node, Retention.class, Target.class);
            node = new EraseTransformer(manager, erase, node).transformWithoutContext(node, null);
            if (node.visibleAnnotations == null && !reservedAnnotationNodes.isEmpty())
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
        final Class<?> shared = target != null ? Maho.shareClass(ClassNameRemapHandler.of(Map.of(sourceClass.name, ASMHelper.className(target))).mapClassNode(node)) : Maho.shareClass(node);
        TransformerManager.Patcher.preLoadedClasses() += shared;
        final String name = shared.getName();
        synchronized (markerQueue) {
            if (Share.class.getName().equals(name))
                init = true;
            loadedMapping[name] = shared;
            markerQueue.stream()
                    .filter(tuple -> tuple.v1.remove(name))
                    .filter(tuple -> tuple.v1.isEmpty())
                    .peek(markerQueue::remove)
                    .map(tuple -> async(tuple.v2, MahoExport.Setup.executor()))
                    .forEach(context.asyncTasks::offer);
        }
        if (handler.isNotDefault(Share::init))
            InitMarker.init(annotation.init(), shared);
    }
    
    @SafeVarargs
    private List<AnnotationNode> reservedAnnotationNodes(final ClassNode node, final Class<? extends Annotation>... annotationTypes) {
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
