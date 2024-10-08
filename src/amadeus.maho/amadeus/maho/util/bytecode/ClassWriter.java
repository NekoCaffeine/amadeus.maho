package amadeus.maho.util.bytecode;

import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

import jdk.internal.org.objectweb.asm.ClassReader;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import amadeus.maho.core.extension.DynamicLookupHelper;
import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.bytecode.context.TransformContext;
import amadeus.maho.util.bytecode.traverser.MethodTraverser;
import amadeus.maho.util.control.FunctionChain;
import amadeus.maho.util.dynamic.ClassLoaderLocal;
import amadeus.maho.util.tuple.Tuple;
import amadeus.maho.util.tuple.Tuple2;

import static org.objectweb.asm.Opcodes.ACC_INTERFACE;

@Setter
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClassWriter extends org.objectweb.asm.ClassWriter {
    
    @Getter
    private static final Optional<String> defaultSuperName = Optional.of(ASMHelper.OBJECT_NAME);
    
    @Getter
    private static final ClassLoaderLocal<ConcurrentHashMap<String, LinkedList<String>>> superCache = { name -> new ConcurrentHashMap<>() };
    
    @Getter
    @SneakyThrows
    private static final FunctionChain<Tuple2<ClassLoader, String>, String> inheritanceChainMapper = new FunctionChain<Tuple2<ClassLoader, String>, String>()
            .add(target -> target.map(tuple -> {
                final @Nullable InputStream resource = tuple.v1.getResourceAsStream(STR."\{tuple.v2}.class");
                return resource == null ? null : new ClassReader(resource).getSuperName();
            }))
            .add(_ -> defaultSuperName());
    
    protected void lookupSuper(final ClassLoader loader, final String name, final LinkedList<String> result) {
        result << name;
        if (!name.equals(ASMHelper.OBJECT_NAME)) {
            final String superName = name.equals(name()) ? superName() : inheritanceChain(loader, name);
            lookupSuper(loader, superName, result);
        }
    }
    
    protected String inheritanceChain(final ClassLoader loader, final String name) = inheritanceChainMapper().applyNullable(Tuple.tuple(loader, name));
    
    public LinkedList<String> lookupSuper(final ClassLoader loader, final String name) = superCache().get(loader).computeIfAbsent(name, it -> new LinkedList<String>().let(result -> lookupSuper(loader, it, result)));
    
    final ClassLoader loader;
    
    boolean itf;
    
    String name, superName, interfaces[];
    
    public self mark(final @Nullable ClassNode node) {
        if (node != null) {
            itf(ASMHelper.anyMatch(node.access, ACC_INTERFACE));
            name(node.name);
            superName(node.superName);
            interfaces(node.interfaces.toArray(String[]::new));
        }
    }
    
    public ClassWriter(final @Nullable ClassLoader loader = null, final int flag = 0) {
        super(flag);
        this.loader = wrapper(loader);
    }
    
    @Override
    public String getCommonSuperClass(final String type1, final String type2) {
        if (type1.equals(type2))
            return type1;
        if (type1.equals(ASMHelper.OBJECT_NAME) || type2.equals(ASMHelper.OBJECT_NAME))
            return ASMHelper.OBJECT_NAME;
        final LinkedList<String> superChain1 = lookupSuper(loader(), type1);
        if (superChain1.contains(type2))
            return type2;
        final LinkedList<String> superChain2 = lookupSuper(loader(), type2);
        if (superChain2.contains(type1))
            return type1;
        return superChain1.stream()
                .filter(superChain2::contains)
                .findFirst()
                .orElse(ASMHelper.OBJECT_NAME);
    }
    
    @Override
    protected ClassLoader getClassLoader() = loader();
    
    public TransformContext context(final ToIntFunction<ClassLoader> loaderIndexed = DynamicLookupHelper.loaderIndexed) = { this, loaderIndexed };
    
    public TransformContext.WithSource context(final ToIntFunction<ClassLoader> loaderIndexed = DynamicLookupHelper.loaderIndexed, final byte bytecode[]) = { this, loaderIndexed, bytecode };
    
    protected ClassLoader wrapper(final @Nullable ClassLoader loader) = loader == null ? ClassLoader.getPlatformClassLoader() : loader;
    
    public static byte[] toBytecode(final Consumer<ClassWriter> acceptor) = new ClassWriter(null).let(acceptor).toByteArray();
    
    public byte[] toBytecode(final ClassNode node, final Collection<MethodNode> methods) = toBytecode(node, methods::contains);
    
    public byte[] toBytecode(final ClassNode node, final Predicate<MethodNode> shouldCompute = _ -> true) {
        mark(node);
        node.methods.stream().filter(shouldCompute).forEach(methodNode -> MethodTraverser.instance().compute(methodNode, this));
        node.accept(this);
        return toByteArray();
    }
    
    public byte[] toBytecodeWithoutComputeFrame(final ClassNode node) {
        mark(node);
        node.accept(this);
        return toByteArray();
    }
    
}
