package amadeus.maho.util.dynamic;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import amadeus.maho.core.Maho;
import amadeus.maho.core.MahoExport;
import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.TransformerManager;
import amadeus.maho.transform.handler.PatchTransformer;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.ClassWriter;
import amadeus.maho.util.bytecode.context.TransformContext;
import amadeus.maho.util.bytecode.generator.MethodGenerator;
import amadeus.maho.util.runtime.ArrayHelper;
import amadeus.maho.util.runtime.MethodHandleHelper;
import amadeus.maho.util.runtime.ReflectionHelper;
import amadeus.maho.util.runtime.TypeHelper;

import static amadeus.maho.util.runtime.ReflectionHelper.*;
import static org.objectweb.asm.Opcodes.ACC_PROTECTED;

@Getter
@ToString
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Wrapper<T> {
    
    private static final AtomicInteger anonymousCounter = { };
    
    protected static int nextAnonymousCount() = anonymousCounter.getAndIncrement();
    
    final ClassLoader loader;
    
    final Class<?> in, superClass, interfaceClasses[];
    
    final Type superType, interfaceTypes[];
    
    final ClassWriter writer;
    
    final TransformContext context;
    
    @Setter
    ClassNode node;
    
    final Type wrapperType;
    
    public void node(final ClassNode value) {
        node = value;
        writer().mark(node());
    }
    
    public Wrapper(final ClassLoader loader = superClass.getClassLoader(), final Class<T> superClass, final Class<?> in = superClass, final String prefix = in.getName(), final String suffix, final Class<?>... interfaceClasses) {
        this.loader = loader;
        final boolean itf = superClass.isInterface();
        this.superClass = checkSuper(itf ? Object.class : superClass);
        this.in = in;
        this.interfaceClasses = checkInterfaces(itf ? ArrayHelper.add(interfaceClasses, superClass) : interfaceClasses);
        superType = Type.getType(superClass());
        interfaceTypes = Stream.of(interfaceClasses()).map(Type::getType).toArray(Type[]::new);
        writer = { loader() };
        context = writer().context();
        node(makeNode(prefix + "$" + suffix));
        wrapperType = Type.getObjectType(node().name);
    }
    
    protected Class<?> checkSuper(final Class<?> superClass) {
        assertionInheritable(superClass);
        return superClass;
    }
    
    protected Class<?>[] checkInterfaces(final Class<?>... interfaceClasses) {
        if (interfaceClasses.length != 0)
            Stream.of(interfaceClasses).forEach(this::assertionInterface);
        return interfaceClasses;
    }
    
    protected ClassNode makeNode(final @Nullable String fullyQualifiedName) {
        final ClassNode result = { };
        result.name = ASMHelper.className(fullyQualifiedName == null ? superClass().getName() + "$Anonymous$" + nextAnonymousCount() : fullyQualifiedName);
        result.superName = superType().getInternalName();
        Stream.of(interfaceTypes()).map(Type::getInternalName).forEach(result.interfaces::add);
        result.version = MahoExport.bytecodeVersion();
        return result;
    }
    
    public Stream<Constructor<?>> allConstructors() = Stream.of(superClass().getDeclaredConstructors());
    
    public Stream<Constructor<?>> inheritableConstructors() = allConstructors().filter(anyMatch(PROTECTED | PUBLIC));
    
    public Stream<Method> allMethods() = Stream.concat(ReflectionHelper.allMethods(superClass()).stream(), Stream.of(interfaceClasses()).map(ReflectionHelper::allMethods).flatMap(Collection::stream)).distinct();
    
    public Stream<Method> inheritableMethods() = allMethods().filter(anyMatch(PROTECTED | PUBLIC)).filter(noneMatch(STATIC | FINAL | BRIDGE));
    
    public Stream<Method> inheritableUniqueSignatureMethods() {
        final Set<String> set = new HashSet<>();
        return inheritableMethods().filter(method -> set.add(methodKey(method)));
    }
    
    public Stream<Method> unimplementedMethods() = inheritableMethods().collect(Collectors.toMap(Wrapper::methodKey, Function.identity(), (a, b) -> Modifier.isAbstract(a.getModifiers()) ? b : a)).values().stream();
    
    public static Predicate<Executable> filterDeclaringClass(final Class<?>... declaringClasses) = executable -> Stream.of(declaringClasses).anyMatch(declaringClass -> executable.getDeclaringClass() == declaringClass);
    
    public static Predicate<Method> filterReturnType(final Class<?>... returnTypes) = method -> Stream.of(returnTypes).anyMatch(returnType -> TypeHelper.isInstance(returnType, method.getReturnType()));
    
    public static Predicate<Method> filterReturnTypeExact(final Class<?>... returnTypes) = method -> Stream.of(returnTypes).anyMatch(returnType -> method.getReturnType() == method.getReturnType());
    
    public static Predicate<Executable> filterParameterCount(final int parameterCount) = executable -> executable.getParameterCount() == parameterCount;
    
    public static Predicate<Executable> filterParameterType(final int parameterIndex, final Class<?>... types) = executable -> Stream.of(types).anyMatch(type -> TypeHelper.isInstance(type, executable.getParameterTypes()[parameterIndex]));
    
    public static Predicate<Executable> filterParameterTypeExact(final int parameterIndex, final Class<?>... types) = executable -> Stream.of(types).anyMatch(type -> executable.getParameterTypes()[parameterIndex] == type);
    
    public static Predicate<Executable> filterStartsWith(final String... strings) = executable -> Stream.of(strings).anyMatch(string -> executable.getName().startsWith(string));
    
    public static Predicate<Executable> filterEndsWith(final String... strings) = executable -> Stream.of(strings).anyMatch(string -> executable.getName().endsWith(string));
    
    public static Predicate<Executable> filterEquals(final String... strings) = executable -> Stream.of(strings).anyMatch(string -> executable.getName().equals(string));
    
    public static Predicate<Executable> filterContains(final String... strings) = executable -> Stream.of(strings).anyMatch(string -> executable.getName().contains(string));
    
    public void assertionInheritable(final Class<?> type) {
        if (is(FINAL, type.getModifiers()))
            throw new UnsupportedOperationException(type + " is finalized.");
    }
    
    public void assertionInterface(final Class<?> type) {
        if (!type.isInterface())
            throw new UnsupportedOperationException(type + " is finalized.");
    }
    
    public void assertionRewritable(final Executable executable) {
        final int modifiers = executable.getModifiers();
        if (!is(STATIC, modifiers) && !is(BRIDGE, modifiers) && is(FINAL, modifiers))
            throw new IllegalStateException(executable + " is finalized.");
    }
    
    public MethodGenerator wrap(final Executable executable, final FieldNode... fieldNodes) = MethodGenerator.fromExecutable(node(), executable, fieldNodes);
    
    public void copyAllConstructors(final FieldNode... fieldNodes) = allConstructors()
            .map(constructor -> wrap(constructor, fieldNodes))
            .forEach(generator -> onlySuperCall(generator, fieldNodes));
    
    public void superCall(final MethodGenerator generator, final FieldNode... fieldNodes) {
        final int offset = generator.argumentTypes.length - fieldNodes.length;
        generator.loadThis();
        generator.loadArgs(0, offset);
        generator.invokeConstructor(superType(), fieldNodes.length == 0 ? generator.desc :
                Type.getMethodDescriptor(Type.VOID_TYPE, Arrays.copyOf(Type.getArgumentTypes(generator.desc), offset)));
        for (int i = 0; i < fieldNodes.length; i++) {
            final FieldNode fieldNode = fieldNodes[i];
            generator.loadThis();
            generator.loadArg(offset + i);
            generator.putField(wrapperType(), fieldNode.name, Type.getType(fieldNode.desc));
        }
    }
    
    public void onlySuperCall(final MethodGenerator generator, final FieldNode... fieldNodes) {
        superCall(generator, fieldNodes);
        generator.returnValue();
        generator.endMethod();
    }
    
    public FieldNode field(final Type type, final String name, final int flag = ACC_PROTECTED) {
        final FieldNode field = { flag, name, type.getDescriptor(), null, null };
        node().fields += field;
        return field;
    }
    
    public FieldNode field(final Class<?> type, final String name, final int flag = ACC_PROTECTED) = field(Type.getType(type), name, flag);
    
    public void patch(final Class<?> patcher, final boolean remap = false) {
        final ClassNode patchNode = Maho.getClassNodeFromClassNonNull(patcher);
        PatchTransformer.patch(context(), TransformerManager.runtime().remapper(), patchNode, node(), remap);
    }
    
    public byte[] writeClass() {
        final ClassNode node = node();
        final byte data[] = context().compute().writer().toBytecode(node);
        TransformerManager.DebugDumper.dumpBytecode(ASMHelper.sourceName(node.name).replace('.', '/'), data, TransformerManager.DebugDumper.dump_transform_generate);
        return data;
    }
    
    @SneakyThrows
    public Class<? extends T> defineWrapperClass() {
        final byte data[] = writeClass();
        try {
            return (Class<? extends T>) Maho.shareClass(ASMHelper.sourceName(node().name), data, loader());
        } catch (final Throwable throwable) {
            ASMHelper.addBytecodeInfo(throwable, data);
            throw throwable;
        }
    }
    
    @SneakyThrows
    public Class<? extends T> defineHiddenWrapperClass(final Class<?> in = in(), final boolean initialize = true, final MethodHandles.Lookup.ClassOption... options) {
        final byte data[] = writeClass();
        try {
            return (Class<? extends T>) MethodHandleHelper.lookup().in(in).defineHiddenClass(data, initialize, options).lookupClass();
        } catch (final Throwable throwable) {
            ASMHelper.addBytecodeInfo(throwable, data);
            throw throwable;
        }
    }
    
    public static <R, T extends R> Wrapper<R> ofAnonymousReplace(final Class<T> target, final String suffix)
            = new Wrapper<>(target.getClassLoader(), (Class<R>) target.getSuperclass(), suffix, target.getInterfaces()).let(result -> result.node(Maho.getClassNodeFromClassNonNull(target)));
    
    private static String methodKey(final Method method) = method.getName() + Type.getMethodDescriptor(method);
    
}
