package amadeus.maho.util.dynamic;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Method;
import java.lang.reflect.TypeVariable;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import jdk.internal.org.objectweb.asm.MethodVisitor;

import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

import amadeus.maho.core.Maho;
import amadeus.maho.core.MahoExport;
import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.AOTTransformer;
import amadeus.maho.transform.TransformerManager;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.Preload;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.InvisibleType;
import amadeus.maho.transform.mark.base.TransformMetadata;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.annotation.mark.HiddenDanger;
import amadeus.maho.util.annotation.mark.IndirectCaller;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.Bytecodes;
import amadeus.maho.util.bytecode.ClassWriter;
import amadeus.maho.util.bytecode.ComputeType;
import amadeus.maho.util.bytecode.generator.MethodGenerator;
import amadeus.maho.util.runtime.ArrayHelper;
import amadeus.maho.util.runtime.MethodHandleHelper;
import amadeus.maho.util.runtime.TypeHelper;
import amadeus.maho.util.runtime.UnsafeHelper;
import amadeus.maho.util.tuple.Tuple2;

import static amadeus.maho.core.extension.MagicAccessor.*;
import static amadeus.maho.util.math.MathHelper.*;
import static org.objectweb.asm.Opcodes.*;

@RequiredArgsConstructor(on = @IndirectCaller)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DynamicMethod {
    
    @Preload(initialized = true)
    @SneakyThrows
    @TransformProvider
    private interface Delegating {
        
        String DelegatingClassLoader = "jdk.internal.reflect.DelegatingClassLoader";
        
        MethodHandle constructor = MethodHandleHelper.lookup().findConstructor(Class.forName(DelegatingClassLoader), MethodType.methodType(void.class, ClassLoader.class));
        
        static @InvisibleType(DelegatingClassLoader) ClassLoader delegating(final @Nullable ClassLoader parent) = (ClassLoader) constructor.invoke(parent);
        
        String
                MemberName                  = "java.lang.invoke.MemberName",
                InnerClassLambdaMetafactory = "java.lang.invoke.InnerClassLambdaMetafactory",
                ForwardingMethodGenerator   = "java.lang.invoke.InnerClassLambdaMetafactory$ForwardingMethodGenerator";
        
        Class<?>
                InnerClassLambdaMetafactoryClass = Class.forName(InnerClassLambdaMetafactory),
                ForwardingMethodGeneratorClass   = Class.forName(ForwardingMethodGenerator);
        
        VarHandle
                this$0              = MethodHandleHelper.lookup().findVarHandle(ForwardingMethodGeneratorClass, "this$0", InnerClassLambdaMetafactoryClass),
                useImplMethodHandle = MethodHandleHelper.lookup().findVarHandle(InnerClassLambdaMetafactoryClass, "useImplMethodHandle", boolean.class),
                implKind            = MethodHandleHelper.lookup().findVarHandle(InnerClassLambdaMetafactoryClass, "implKind", int.class),
                implClass           = MethodHandleHelper.lookup().findVarHandle(InnerClassLambdaMetafactoryClass, "implClass", Class.class);
        
        @Hook(avoidRecursion = true, at = @At(insn = @At.Insn(opcode = ICONST_M1 /* TRUSTED == -1 */), ordinal = 0, offset = 1), capture = true, metadata = @TransformMetadata(aotLevel = AOTTransformer.Level.RUNTIME))
        private static boolean checkAccess(final boolean capture /* allowedModes == TRUSTED */, final MethodHandles.Lookup $this, final byte refKind, final Class<?> refClass, final @InvisibleType(MemberName) Object memberName)
                = capture || bridgeClass.isAssignableFrom($this.lookupClass());
        
        // # Cross-package constructor reference support
        
        @Hook(avoidRecursion = true, at = @At(field = @At.FieldInsn(name = "useImplMethodHandle")), capture = true, metadata = @TransformMetadata(aotLevel = AOTTransformer.Level.RUNTIME))
        private static boolean _init_(final boolean capture, final @InvisibleType(InnerClassLambdaMetafactory) Object $this,
                final MethodHandles.Lookup caller,
                final MethodType invokedType,
                final String samMethodName,
                final MethodType samMethodType,
                final MethodHandle implMethod,
                final MethodType instantiatedMethodType,
                final boolean isSerializable,
                final Class<?> markerInterfaces[],
                final MethodType additionalBridges[])
                = capture || bridgeClass.isAssignableFrom(caller.lookupClass());
        
        @Hook(at = @At(intInsn = @At.IntInsn(opcode = Bytecodes.BIPUSH, operand = MethodHandleInfo.REF_newInvokeSpecial), ordinal = 0, offset = 1), capture = true, avoidRecursion = true,
                metadata = @TransformMetadata(aotLevel = AOTTransformer.Level.RUNTIME))
        private static boolean generate_$NEW_DUP(final boolean capture, final @InvisibleType(ForwardingMethodGenerator) MethodVisitor $this, final MethodType methodType)
                = capture && !((boolean) useImplMethodHandle.get(this$0.get($this)));
        
        @Hook(at = @At(intInsn = @At.IntInsn(opcode = Bytecodes.BIPUSH, operand = MethodHandleInfo.REF_invokeStatic), ordinal = 0, offset = 1), capture = true, avoidRecursion = true,
                metadata = @TransformMetadata(aotLevel = AOTTransformer.Level.RUNTIME))
        private static boolean generate_$insertParameterTypes(final boolean capture, final @InvisibleType(ForwardingMethodGenerator) MethodVisitor $this, final MethodType methodType)
                = capture && (int) implKind.get(this$0.get($this)) != MethodHandleInfo.REF_newInvokeSpecial;
        
        @Hook(at = @At(method = @At.MethodInsn(name = "descriptorString"), ordinal = 0), capture = true, avoidRecursion = true,
                metadata = @TransformMetadata(aotLevel = AOTTransformer.Level.RUNTIME))
        private static MethodType generate_$changeReturnType(final MethodType capture, final @InvisibleType(ForwardingMethodGenerator) MethodVisitor $this, final MethodType methodType) {
            final var metafactory = this$0.get($this);
            return (int) implKind.get(metafactory) != MethodHandleInfo.REF_newInvokeSpecial ? capture : capture.changeReturnType((Class<?>) implClass.get(metafactory));
        }
        
    }
    
    @Setter
    @Getter
    @ToString
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Lambda<T> extends DynamicMethod {
        
        final Class<T> functionalInterfaceType;
        final Method   functionalMethod;
        final Class<?> genericTypes[];
        
        @Nullable MethodNode genericBridgeBody;
        
        @IndirectCaller
        public Lambda(final @Nullable ClassLoader loader, final String debugName, final Class<? super T> functionalInterfaceType, final Class... genericTypes) {
            super(loader, debugName);
            // check FunctionalInterface or IllegalArgumentException
            functionalMethod = LambdaHelper.lookupFunctionalMethodWithInterface(functionalInterfaceType);
            this.functionalInterfaceType = (Class<T>) functionalInterfaceType;
            this.genericTypes = genericTypes;
            final Method functionalMethod = functionalMethod();
            if (genericTypes.length == 0)
                initBody(functionalMethod.getName(), Type.getMethodDescriptor(functionalMethod));
            else {
                final TypeVariable<?> parameters[] = functionalInterfaceType.getTypeParameters();
                if (parameters.length != genericTypes.length)
                    throw new IncompatibleClassChangeError(STR."The length of the generic parameter list is inconsistent. expected: \{ArrayHelper.toString(parameters)}, actual: \{ArrayHelper.toString(genericTypes)}");
                final java.lang.reflect.Type argsParameters[] = functionalMethod.getGenericParameterTypes();
                // @formatter:off
                final Type
                        returnType = Type.getType(Stream.of(parameters)
                                .filter(functionalMethod.getGenericReturnType()::equals)
                                .findFirst()
                                .map(type -> genericTypes[ArrayHelper.indexOf(parameters, type)])
                                .orElseGet(functionalMethod::getReturnType)),
                        argsTypes[] = Stream.of(argsParameters)
                                .map(arg -> Stream.of(parameters)
                                        .filter(arg::equals)
                                        .findFirst()
                                        .map(type -> genericTypes[ArrayHelper.indexOf(parameters, type)])
                                        .orElseGet(() -> functionalMethod.getParameterTypes()[ArrayHelper.indexOf(argsParameters, arg)]))
                                .map(Type::getType)
                                .toArray(Type[]::new);
                // @formatter:on
                final Type genericMethodType = Type.getMethodType(returnType, argsTypes), sourceMethodType = Type.getType(functionalMethod);
                if (genericMethodType.equals(sourceMethodType))
                    initBody(functionalMethod.getName(), sourceMethodType.getDescriptor());
                else {
                    initBody(functionalMethod.getName(), genericMethodType.getDescriptor());
                    genericBridgeBody(new MethodNode(ACC_PUBLIC | ACC_FINAL | ACC_SYNTHETIC | ACC_BRIDGE, functionalMethod.getName(), sourceMethodType.getDescriptor(), null, null));
                    final MethodGenerator generator = MethodGenerator.fromMethodNode(genericBridgeBody());
                    generator.loadThis();
                    generator.loadArgs(genericMethodType.getArgumentTypes());
                    generator.invokeSpecial(wrapperType(), new org.objectweb.asm.commons.Method(genericBridgeBody().name, genericMethodType.getDescriptor()), false);
                    generator.checkCast(sourceMethodType.getReturnType(), genericMethodType.getReturnType());
                    generator.returnValue();
                    generator.endMethod();
                }
            }
            final MethodNode body = body();
            body.access = ASMHelper.changeAccess(body.access, ACC_PUBLIC);
        }
        
        @Override
        public boolean isStatic() = false;
        
        @Override
        protected Class<?> defineWrapperClass() {
            interfaces() -= functionalInterfaceType;
            interfaces() >> functionalInterfaceType;
            return super.defineWrapperClass();
        }
        
        @Override
        public ClassNode wrapper() = super.wrapper().let(it -> {
            final @Nullable MethodNode genericBridgeBody = genericBridgeBody();
            if (genericBridgeBody != null)
                it.methods += genericBridgeBody;
            final @Nullable String signature = signature();
            if (signature != null)
                it.signature = signature;
        });
        
        protected @Nullable String signature() {
            final Class<?> genericTypes[] = genericTypes();
            if (genericTypes.length > 0) {
                final SignatureWriter signatureWriter = { };
                signatureWriter.visitClassBound();
                signatureWriter.visitClassType(Bridge);
                signatureWriter.visitEnd();
                signatureWriter.visitClassBound();
                signatureWriter.visitClassType(ASMHelper.className(functionalInterfaceType()));
                signatureWriter.visitTypeArgument('=');
                Stream.of(genericTypes)
                        .forEach(type -> {
                            if (type.isArray()) {
                                final int p_dimensions[] = { 0 };
                                final Class<?> rootComponentType = TypeHelper.getRootComponentType(type, p_dimensions);
                                while (--p_dimensions[0] > -1)
                                    signatureWriter.visitArrayType();
                                signatureWriter.visitClassType(ASMHelper.className(rootComponentType));
                            } else
                                signatureWriter.visitClassType(ASMHelper.className(type));
                            signatureWriter.visitEnd();
                        });
                signatureWriter.visitEnd();
                if (interfaces().size() > 1)
                    interfaces().stream()
                            .skip(1L)
                            .map(ASMHelper::className)
                            .forEach(name -> {
                                signatureWriter.visitClassBound();
                                signatureWriter.visitClassType(name);
                                signatureWriter.visitEnd();
                            });
                return signatureWriter.toString();
            } else
                return null;
        }
        
        @SneakyThrows
        public T allocateInstance() = UnsafeHelper.allocateInstance(wrapperClass());
        
    }
    
    public static final String
            CLASS_NAME  = ASMHelper.className(DynamicMethod.class.getName()),
            METHOD_NAME = "$invoke";
    
    protected static final AtomicInteger counter = { };
    
    @IndirectCaller
    protected static String contextDebugName() = STR."\{CallerContext.caller().getSimpleName()}$\{counter.getAndIncrement()}";
    
    @IndirectCaller
    public static DynamicMethod ofMethod(final @Nullable ClassLoader loader, final String debugName = contextDebugName(), final Method method,
            final ClassNode node = Maho.getClassNodeFromClassNonNull(method.getDeclaringClass())) {
        final DynamicMethod dynamicMethod = { loader };
        final Tuple2<ClassNode, MethodNode> tuple = methodBody(method, node);
        final MethodNode body = { tuple.v2.access, METHOD_NAME, tuple.v2.desc, null, null };
        tuple.v2.accept(body);
        body.access &= ~(ACC_PROTECTED | ACC_PUBLIC);
        body.access |= ACC_PRIVATE | ACC_FINAL | ACC_SYNTHETIC;
        if (noneMatch(body.access, ACC_STATIC)) {
            body.access |= ACC_STATIC;
            body.desc = body.desc.replace("(", STR."(\{ASMHelper.classDesc(method.getDeclaringClass())}");
        }
        dynamicMethod.body(body);
        dynamicMethod.sourceFile(tuple.v1.sourceFile);
        dynamicMethod.sourceDebug(tuple.v1.sourceDebug);
        return dynamicMethod;
    }
    
    public static Tuple2<ClassNode, MethodNode> methodBody(final Method method, final ClassNode node = Maho.getClassNodeFromClassNonNull(method.getDeclaringClass())) {
        final String name = method.getName(), desc = Type.getMethodDescriptor(method);
        for (final MethodNode methodNode : node.methods)
            if (methodNode.name.equals(name) && methodNode.desc.equals(desc))
                return { node, methodNode };
        throw new UnsupportedOperationException(STR."set method body: \{method}");
    }
    
    public static MethodHandle constructor(final Class<?> owner, final MethodType methodType) {
        final Type ownerType = Type.getType(owner);
        final DynamicMethod method = { owner.getClassLoader() };
        method.initBody(Type.getMethodDescriptor(Type.VOID_TYPE, Stream.concat(Stream.of(ownerType), Stream.of(methodType.parameterArray()).map(Type::getType)).toArray(Type[]::new)));
        final MethodGenerator generator = method.generator();
        generator.loadArgs();
        generator.invokeSpecial(ownerType, new org.objectweb.asm.commons.Method(ASMHelper._INIT_, methodType.descriptorString()), false);
        generator.returnValue();
        generator.endMethod();
        return method.handle();
    }
    
    public static MethodHandle constructor(final Class<?> owner, final Class<?>... argTypes) = constructor(owner, MethodType.methodType(void.class, argTypes));
    
    @Getter
    @Default
    final @Nullable ClassLoader loader = CallerContext.caller().getClassLoader();
    
    @Getter
    @Default
    final String debugName = contextDebugName();
    
    public String debugName() = debugName.replace('.', '$');
    
    @Getter
    final Type wrapperType = Type.getObjectType(STR."\{CLASS_NAME}$$\{debugName()}");
    
    @Getter
    final LinkedList<FieldNode> closure = { };
    
    @Getter
    final LinkedList<Class<?>> interfaces = { };
    
    @Setter(AccessLevel.PROTECTED)
    @Getter(AccessLevel.PROTECTED)
    MethodNode body;
    
    @Getter
    @Setter
    @Nullable String sourceFile, sourceDebug;
    
    @Getter
    @Setter
    @Nullable String outerClass;
    
    @Getter
    @Setter
    @Nullable String nestHostClass;
    
    @Getter
    @Setter
    @Nullable List<String> nestMembers;
    
    @Setter
    @Getter
    int version = MahoExport.bytecodeVersion();
    
    @Getter(lazy = true)
    Class<?> wrapperClass = defineWrapperClass();
    
    public String methodName() = body().name;
    
    public void methodName(final String name) = body().name = name;
    
    public String methodDesc() = body().desc;
    
    public @Nullable String methodSignature() = body().signature;
    
    public void initBody(final String name = METHOD_NAME, final String descriptor) = body(new MethodNode(ACC_PRIVATE | ACC_FINAL | ACC_SYNTHETIC, name, descriptor, null, null));
    
    public FieldNode closure(final Type type, final String name) = { ACC_PRIVATE, name, type.getDescriptor(), null, null };
    
    public void addClosure(final FieldNode... fieldNodes) = closure() *= List.of(fieldNodes);
    
    public void addInterfaces(final Class<?>... interfaces) = interfaces() *= List.of(interfaces);
    
    public InsnList instructions() = body().instructions;
    
    public MethodGenerator generator() {
        if (isStatic())
            markAccess(ACC_STATIC);
        return MethodGenerator.fromMethodNode(body());
    }
    
    public void markSynchronized() = markAccess(ACC_SYNCHRONIZED);
    
    public void markAccess(final int access) = body().access |= access;
    
    public void requestMinimumVersion(final int version) = version(max(version(), version));
    
    public boolean isStatic() = closure().stream().allMatch(fieldNode -> ASMHelper.anyMatch(fieldNode.access, ACC_STATIC));
    
    protected static final String HOLDER_CLASS_NAME = STR."\{DynamicMethod.class.getName()}$Holder";
    
    protected Class<?> holder() {
        final ClassNode node = { };
        node.sourceFile = sourceFile();
        node.sourceDebug = sourceDebug();
        node.name = ASMHelper.className(HOLDER_CLASS_NAME);
        node.superName = ASMHelper.OBJECT_NAME;
        node.access = ACC_PUBLIC | ACC_FINAL | ACC_SYNTHETIC;
        node.version = MahoExport.bytecodeVersion();
        return Maho.shareClass(node, Delegating.delegating(loader()));
    }
    
    @SneakyThrows
    @HiddenDanger(HiddenDanger.INVALID_BYTECODE)
    protected Class<?> defineWrapperClass() {
        closure().forEach(fieldNode -> fieldNode.access |= ACC_SYNTHETIC);
        final byte bytecode[] = toBytecode(loader());
        final String debugName = debugName();
        if (debugName != null)
            TransformerManager.DebugDumper.dumpBytecode(wrapperType.getInternalName(), bytecode, TransformerManager.DebugDumper.dump_transform_generate);
        return MethodHandleHelper.lookup().in(holder()).defineHiddenClass(bytecode, false).lookupClass();
    }
    
    @SneakyThrows
    public MethodHandle handle() {
        final MethodType methodType = ASMHelper.loadMethodType(methodDesc(), loader());
        return isStatic() ?
                MethodHandleHelper.lookup().findStatic(wrapperClass(), methodName(), methodType) :
                MethodHandleHelper.lookup().findVirtual(wrapperClass(), methodName(), methodType);
    }
    
    public void checkFieldNode(final FieldNode fieldNode) {
        if (!closure().contains(fieldNode))
            throw new IllegalArgumentException(STR."\{fieldNode.name}/\{fieldNode.desc}");
    }
    
    public MethodHandle getter(final FieldNode fieldNode) = getter(fieldNode.name, fieldNode.desc, ASMHelper.anyMatch(fieldNode.access, ACC_STATIC));
    
    @SneakyThrows
    public MethodHandle getter(final String name, final String desc, final boolean isStatic) {
        final Class<?> type = ASMHelper.loadType(Type.getType(desc), false, loader());
        return isStatic ?
                MethodHandleHelper.lookup().findStaticGetter(wrapperClass(), name, type) :
                MethodHandleHelper.lookup().findGetter(wrapperClass(), name, type);
    }
    
    public MethodHandle setter(final FieldNode fieldNode) = setter(fieldNode.name, fieldNode.desc, ASMHelper.anyMatch(fieldNode.access, ACC_STATIC));
    
    @SneakyThrows
    public MethodHandle setter(final String name, final String desc, final boolean isStatic) {
        final Class<?> type = ASMHelper.loadType(Type.getType(desc), false, loader());
        return isStatic ?
                MethodHandleHelper.lookup().findStaticSetter(wrapperClass(), name, type) :
                MethodHandleHelper.lookup().findSetter(wrapperClass(), name, type);
    }
    
    @SneakyThrows
    public Object allocateInstance() = UnsafeHelper.allocateInstance(wrapperClass());
    
    public byte[] toBytecode(final @Nullable ClassLoader loader) = new ClassWriter(loader).toBytecode(wrapper());
    
    public ClassNode wrapper() {
        final ClassNode result = { };
        result.sourceFile = sourceFile();
        result.sourceDebug = sourceDebug();
        result.outerClass = outerClass();
        result.nestHostClass = nestHostClass();
        result.nestMembers = nestMembers();
        result.name = wrapperType().getInternalName();
        result.access = ACC_PUBLIC | ACC_FINAL | ACC_SYNTHETIC;
        result.superName = Bridge;
        result.interfaces *= interfaces().stream().map(ASMHelper::className).toList();
        result.version = version();
        result.fields *= closure();
        result.methods += body();
        return result;
    }
    
}
