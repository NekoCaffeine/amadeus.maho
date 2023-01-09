package amadeus.maho.util.bytecode;

import java.io.InputStream;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.objectweb.asm.tree.ClassNode;

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
import amadeus.maho.vm.transform.mark.HotSpotJIT;

import static org.objectweb.asm.Opcodes.ACC_INTERFACE;

@HotSpotJIT
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
                final @Nullable InputStream resource = tuple.v1.getResourceAsStream(tuple.v2 + ".class");
                return resource == null ? null : ASMHelper.newClassReader(resource).getSuperName();
            }))
            .add(target -> defaultSuperName());
    
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
    
    public TransformContext context(final boolean aot = false) = { this, aot };
    
    public TransformContext.WithSource context(final boolean aot = false, final byte bytecode[]) = { this, aot, bytecode };
    
    protected ClassLoader wrapper(final @Nullable ClassLoader loader) = loader == null ? ClassLoader.getPlatformClassLoader() : loader;
    
    public static byte[] toBytecode(final Consumer<ClassWriter> accepter) = new ClassWriter(null).let(accepter).toByteArray();
    
    public byte[] toBytecode(final ClassNode node, final ComputeType... computeTypes) {
        mark(node);
        if (computeTypes.length > 0) {
            final Set<ComputeType> types = Set.of(computeTypes);
            node.methods.forEach(methodNode -> MethodTraverser.instance().compute(methodNode, this, types));
        }
        node.accept(this);
        return toByteArray();
    }
    
}
