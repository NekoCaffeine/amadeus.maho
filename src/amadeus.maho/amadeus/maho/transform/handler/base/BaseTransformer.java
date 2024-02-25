package amadeus.maho.transform.handler.base;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import amadeus.maho.core.MahoExport;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.AOTTransformer;
import amadeus.maho.transform.ClassTransformer;
import amadeus.maho.transform.TransformerManager;
import amadeus.maho.transform.mark.Remap;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.Experimental;
import amadeus.maho.transform.mark.base.TransformMetadata;
import amadeus.maho.util.annotation.AnnotationHandler;
import amadeus.maho.util.annotation.mark.Freeze;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.ClassWriter;
import amadeus.maho.util.bytecode.context.TransformContext;
import amadeus.maho.util.bytecode.remap.RemapContext;
import amadeus.maho.util.misc.Environment;

@RequiredArgsConstructor
public abstract class BaseTransformer<A extends Annotation> implements ClassTransformer {
    
    public final TransformerManager manager;
    
    @Freeze
    public final A annotation;
    
    @Freeze
    public final ClassNode sourceClass;
    
    public final AnnotationHandler<A> handler = AnnotationHandler.asOneOfUs(annotation);
    
    public final @Nullable TransformMetadata metadata = handler.lookupValue("metadata");
    
    public final int order = metadata == null ? 0 : metadata.order();
    
    @Getter
    private volatile int debugTransformCount = 0;
    
    @Setter
    @Getter
    private @Nullable ClassLoader contextClassLoader;
    
    @Setter
    @Getter
    private boolean handleNullNode, experimental;
    
    @Getter
    protected Predicate<ClassNode> nodeFilter = node -> (node != null || handleNullNode()) && (!experimental() || MahoExport.experimental());
    
    {
        if (handler == null)
            throw new IllegalArgumentException("Unable to get the expected annotation context.");
        applyNodeFilter(checkSwitch());
        injectTransformerRemapData();
        if (ASMHelper.hasAnnotation(sourceClass, Experimental.class))
            markExperimental();
    }
    
    protected void markHandleNullNode() = handleNullNode = true;
    
    protected void markExperimental() = experimental = true;
    
    @Override
    public @Nullable ClassNode transform(final TransformContext context, final @Nullable ClassNode node, final @Nullable ClassLoader loader, final @Nullable Class<?> clazz, final @Nullable ProtectionDomain domain) {
        if (nodeFilter().test(node)) {
            debugTransformCount++;
           return doTransform(context, node, loader, clazz, domain);
        }
        return node;
    }
    
    public abstract @Nullable ClassNode doTransform(final TransformContext context, final @Nullable ClassNode node, final @Nullable ClassLoader loader, final @Nullable Class<?> clazz, final @Nullable ProtectionDomain domain);
    
    public @Nullable ClassNode transformWithoutContext(final @Nullable ClassNode node, final @Nullable ClassLoader loader) = transform(new ClassWriter(loader).mark(node).context(), node, null, null, null);
    
    public boolean valid() = true;
    
    @Override
    public boolean canAOT() = valid();
    
    @Override
    public AOTTransformer.Level aotLevel() = metadata?.aotLevel() ?? ClassTransformer.super.aotLevel();
    
    public void onAOT(final AOTTransformer aotTransformer) = aotTransformer.addClassAnnotation(annotation.annotationType());
    
    public void applyNodeFilter(final Predicate<ClassNode> next) = nodeFilter = nodeFilter.and(next);
    
    public void injectTransformerRemapData() {
        if (this instanceof RemapContext context && metadata != null && metadata.remap()) {
            for (final Method member : annotation.getClass().getMethods()) {
                boolean flag = false;
                final String name = member.getName();
                if (member.isAnnotationPresent(Remap.Class.class)) {
                    checkMethodReturnType(member);
                    if (member.getReturnType() == Class.class)
                        handler.changeValue(name, manager.remapper().mapType(handler.<Type>lookupSourceValue(name) ?? Type.getType(handler.<Class<?>>lookupValue(name))));
                    else if (member.getReturnType() == Class[].class)
                        handler.changeValue(name, (handler.<List<Type>>lookupSourceValue(name) ?? Stream.of(handler.<Class<?>[]>lookupValue(name)).map(Type::getType).toList()).stream()
                                .map(manager.remapper()::mapType)
                                .toArray(Type[]::new));
                    else {
                        final String string = handler.lookupSourceValue(name);
                        handler.changeValue(name, string.isEmpty() || string.equals(At.Lookup.WILDCARD) ? string : manager.mapType(string));
                    }
                    flag = true;
                }
                if (member.isAnnotationPresent(Remap.Field.class)) {
                    checkMethodReturnType(member);
                    checkRemapContext();
                    checkFlag(flag, member);
                    final String string = handler.lookupSourceValue(name);
                    handler.changeValue(name, string.isEmpty() || string.equals(At.Lookup.WILDCARD) ? string : manager.mapFieldName(context.lookupOwner(name), string));
                    flag = true;
                }
                if (member.isAnnotationPresent(Remap.Method.class)) {
                    checkMethodReturnType(member);
                    checkRemapContext();
                    checkFlag(flag, member);
                    final String string = handler.lookupSourceValue(name);
                    handler.changeValue(name, string.isEmpty() || string.equals(At.Lookup.WILDCARD) ? string : manager.mapMethodName(context.lookupOwner(name), string, context.lookupDescriptor(name)));
                    flag = true;
                }
                if (member.isAnnotationPresent(Remap.Descriptor.class)) {
                    checkMethodReturnType(member);
                    checkFlag(flag, member);
                    final String string = handler.lookupSourceValue(name);
                    handler.changeValue(name, string.isEmpty() || string.equals(At.Lookup.WILDCARD) ? string : manager.remapper().mapDesc(string));
                }
            }
        }
    }
    
    private void checkMethodReturnType(final Method method) {
        if (!method.getDeclaringClass().isAnnotation() || method.getReturnType() != String.class && method.getReturnType() != Class.class)
            throw new IllegalArgumentException("@Remap.? can only be marked on the annotation methods with a return target of String or Class");
    }
    
    private void checkRemapContext() {
        if (!(this instanceof RemapContext))
            throw new IllegalArgumentException(STR."\{getClass()} must implement interface \{RemapContext.class.getName()}");
    }
    
    private void checkFlag(final boolean flag, final Method method) {
        if (flag)
            throw new IllegalArgumentException(STR."There can only be one kind of @Remap.X on the \{method}");
    }
    
    protected Predicate<ClassNode> checkSwitch() {
        if (metadata != null) {
            final String enable[] = metadata.enable(), disable[] = metadata.disable();
            if (disable.length != 0 || enable.length != 0) {
                final List<Predicate<ClassNode>>
                        enableList = Stream.of(enable).map(expr -> mapPredicate(expr, manager.environment())).toList(),
                        disableList = Stream.of(disable).map(expr -> mapPredicate(expr, manager.environment())).toList();
                return node -> disableList.stream().noneMatch(predicate -> predicate.test(node)) && enableList.stream().allMatch(predicate -> predicate.test(node));
            }
        }
        return node -> true;
    }
    
    public static Predicate<ClassNode> mapPredicate(final String value, final Environment environment) {
        String expr = value;
        boolean opposite = false;
        if (expr.startsWith("!")) {
            opposite = true;
            expr = expr.substring(1);
        }
        final boolean flag = !opposite;
        if (expr.startsWith("#")) {
            final String name = expr.substring(1);
            return node -> name.tryLoad(false) != null == flag;
        }
        final String condition = expr;
        return node -> {
            final @Nullable String lookup = environment.lookup(condition);
            return lookup != null && environment.lookup(condition, false) == flag;
        };
    }
    
    @Override
    public String toString() = STR."\{annotation} => \{sourceClass.name}";
    
    @Override
    public int compareTo(final ClassTransformer target) = order - (target instanceof BaseTransformer transformer ? transformer.order : 0);
    
}
