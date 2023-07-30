package amadeus.maho.core.extension;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import jdk.internal.misc.Unsafe;

import amadeus.maho.core.MahoBridge;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Callback;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Erase;
import amadeus.maho.transform.mark.Init;
import amadeus.maho.transform.mark.Share;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.bytecode.Bytecodes;

import static amadeus.maho.core.extension.DynamicLookupHelper.*;
import static org.objectweb.asm.Opcodes.*;

@SneakyThrows
@TransformProvider
@Share(target = shareDynamicLookup, erase = @Erase, init = @Init(initialized = true), makePublic = true)
interface DynamicLookup {
    
    int
            WEAK_LINKING = 1 << 31,
            CONSTANT = 1 << 30;
    
    String
            DynamicLookupHelperName = "amadeus.maho.core.extension.DynamicLookupHelper",
            MethodHandleHelperName  = "amadeus.maho.util.runtime.MethodHandleHelper",
            DynamicMethodName       = "amadeus.maho.util.dynamic.DynamicMethod";
    
    @Getter
    MethodHandles.Lookup lookup = (MethodHandles.Lookup) MethodHandles.lookup().findStatic(Class.forName(MethodHandleHelperName, true, MahoBridge.bridgeClassLoader()), "lookup", MethodType.methodType(MethodHandles.Lookup.class)).invokeExact();
    
    MethodHandle loadClass = lookup().findStatic(Class.forName(DynamicLookupHelperName, true, MahoBridge.bridgeClassLoader()), "loadClass", MethodType.methodType(Class.class, int.class, String.class));
    
    MethodHandle methodType = lookup().findStatic(Class.forName(DynamicLookupHelperName, true, MahoBridge.bridgeClassLoader()), "methodType", MethodType.methodType(MethodType.class, String.class, ClassLoader.class));
    
    MethodHandle accessHandle = lookup().findStatic(Class.forName(DynamicLookupHelperName, true, MahoBridge.bridgeClassLoader()), "accessHandle", MethodType.methodType(MethodHandle.class, Method.class));
    
    MethodHandle isInstance = lookup().findVirtual(Class.class, "isInstance", MethodType.methodType(boolean.class, Object.class));
    
    MethodHandle constructor = lookup().findStatic(Class.forName(DynamicMethodName, true, MahoBridge.bridgeClassLoader()), "constructor", MethodType.methodType(MethodHandle.class, Class.class, MethodType.class));
    
    MethodHandle proxy = proxy();
    
    @SneakyThrows
    private static MethodHandle proxy() {
        final Class<?> hotspot = Class.forName("amadeus.maho.vm.reflection.hotspot.HotSpot", true, MahoBridge.bridgeClassLoader());
        final MethodHandles.Lookup lookup = lookup();
        return lookup.findVirtual(hotspot, "copyObjectWithoutHead", MethodType.methodType(Object.class, Class.class, Object.class)).bindTo(lookup.findStatic(hotspot, "instance", MethodType.methodType(hotspot)).invoke());
    }
    
    MethodHandle allocateInstance = lookup().findVirtual(Unsafe.class, "allocateInstance", MethodType.methodType(Object.class, Class.class)).bindTo(Unsafe.getUnsafe());
    
    @Callback
    static CallSite makeSiteByNameWithBoot(final MethodHandles.Lookup lookup, final String name, final MethodType methodType, final int opcode, final String owner, final String realDesc)
            = makeSiteByClass(lookup, name, methodType, opcode, Class.forName(owner, false, null), realDesc);
    
    @Callback
    static CallSite makeSiteByName(final MethodHandles.Lookup lookup, final String name, final MethodType methodType, final int opcode, final int id, final String owner, final String realDesc)
            = makeSiteByClass(lookup, name, methodType, opcode, (Class<?>) loadClass.invokeExact(id, owner), realDesc);
    
    @SneakyThrows
    static CallSite makeSiteByClass(MethodHandles.Lookup lookup, final String name, MethodType methodType, final int opcode, final Class<?> owner, final String realDesc) {
        final ClassLoader envClassLoader = lookup.lookupClass().getClassLoader(), loader = owner.getClassLoader();
        lookup = lookup();
        final MethodType sourceType = methodType;
        final boolean hasRealType = !realDesc.isEmpty();
        if (hasRealType) {
            methodType = (MethodType) DynamicLookup.methodType.invokeExact(realDesc, envClassLoader);
            if (sourceType.parameterCount() != methodType.parameterCount())
                throw new IllegalArgumentException(sourceType + " -> " + methodType);
        }
        final MethodType sourceRealType = methodType;
        Class<?> returnType = null;
        List<Map.Entry<Class<?>, Class<?>>> parametersType = null;
        if (envClassLoader != loader) {
            returnType = methodType.returnType().isPrimitive() ? methodType.returnType() : Class.forName(methodType.returnType().getName(), false, loader);
            if (returnType != methodType.returnType())
                methodType = methodType.changeReturnType(returnType);
            parametersType = Stream.of(methodType.parameterArray())
                    .map(type -> Map.<Class<?>, Class<?>>entry(type, type.isPrimitive() ? type : Class.forName(type.getName(), false, loader)))
                    .toList();
            for (int i = 0; i < parametersType.size(); i++) {
                final var entry = parametersType.get(i);
                if (entry.getKey() != entry.getValue())
                    methodType.changeParameterType(i, entry.getValue());
            }
        }
        MethodHandle handle;
        try {
            handle = switch (opcode & Bytecodes.MAX) {
                case INVOKESTATIC       -> lookup.findStatic(owner, name, methodType);
                case INVOKEVIRTUAL,
                        INVOKEINTERFACE -> lookup.findVirtual(owner, name, methodType.dropParameterTypes(0, 1));
                case INVOKESPECIAL      -> _new.equals(name) ? (MethodHandle) constructor.invokeExact(owner, methodType.dropParameterTypes(0, 1)) : lookup.findSpecial(owner, name, methodType.dropParameterTypes(0, 1), owner);
                case NEW                -> lookupConstructor(lookup, owner, methodType.changeReturnType(void.class));
                case GETSTATIC          -> lookup.findStaticGetter(owner, name, methodType.returnType());
                case PUTSTATIC          -> putCatch(lookup.findStaticSetter(owner, name, methodType.parameterType(0)), methodType);
                case GETFIELD           -> lookup.findGetter(owner, name, methodType.returnType());
                case PUTFIELD           -> putCatch(lookup.findSetter(owner, name, methodType.parameterType(1)), methodType);
                case INSTANCEOF         -> isInstance.bindTo(owner);
                default                 -> throw new UnsupportedOperationException("Unsupported opcode: " + opcode);
            };
        } catch (final ReflectiveOperationException e) {
            if ((opcode & WEAK_LINKING) != 0 && (e instanceof NoSuchFieldException || e instanceof NoSuchMethodException))
                return new ConstantCallSite(MethodHandles.empty(sourceType));
            throw e;
        }
        if (envClassLoader != loader) {
            if (returnType != sourceRealType.returnType()) {
                final MethodHandle proxy = MethodHandles.insertArguments(DynamicLookup.proxy, 0, sourceRealType.returnType());
                handle = MethodHandles.collectArguments(MethodHandles.explicitCastArguments(proxy, proxy.type()
                        .changeReturnType(sourceType.returnType())
                        .changeParameterType(0, handle.type().returnType())), 0, handle);
            }
            for (int i = 0; i < parametersType.size(); i++) {
                final var entry = parametersType.get(i);
                if (entry.getKey() != entry.getValue()) {
                    final MethodHandle proxy = MethodHandles.insertArguments(DynamicLookup.proxy, 0, entry.getValue());
                    handle = MethodHandles.filterArguments(handle, i, MethodHandles.explicitCastArguments(proxy, proxy.type()
                            .changeReturnType(entry.getValue())
                            .changeParameterType(0, entry.getKey())));
                }
            }
        }
        if (hasRealType)
            handle = MethodHandles.explicitCastArguments(handle, sourceType);
        return new ConstantCallSite(handle);
    }
    
    @Callback
    static CallSite makePrivilegeSite(MethodHandles.Lookup lookup, final String name, final MethodType methodType, final int opcode, final String className, final String methodDesc) {
        final @Nullable ClassLoader loader = lookup.lookupClass().getClassLoader();
        final Class<?> owner = Class.forName(className, true, loader);
        final MethodType realType = (MethodType) DynamicLookup.methodType.invokeExact(methodDesc, loader);
        lookup = lookup(); // privilege
        try {
            return new ConstantCallSite(MethodHandles.explicitCastArguments(switch (opcode & Bytecodes.MAX) {
                case INVOKESTATIC       -> lookup.findStatic(owner, name, realType);
                case INVOKEVIRTUAL,
                        INVOKEINTERFACE -> lookup.findVirtual(owner, name, realType.dropParameterTypes(0, 1));
                case INVOKESPECIAL      -> _new.equals(name) ? (MethodHandle) constructor.invokeExact(owner, realType.dropParameterTypes(0, 1)) : lookup.findSpecial(owner, name, realType.dropParameterTypes(0, 1), owner);
                case NEW                -> lookupConstructor(lookup, owner, realType.changeReturnType(void.class));
                case GETSTATIC          -> lookup.findStaticGetter(owner, name, realType.returnType());
                case PUTSTATIC          -> putCatch(lookup.findStaticSetter(owner, name, realType.parameterType(0)), realType);
                case GETFIELD           -> lookup.findGetter(owner, name, realType.returnType());
                case PUTFIELD           -> putCatch(lookup.findSetter(owner, name, realType.parameterType(1)), realType);
                default                 -> throw new UnsupportedOperationException("Unsupported opcode: " + opcode);
            }, methodType));
        } catch (final ReflectiveOperationException e) {
            if ((opcode & WEAK_LINKING) != 0 && (e instanceof NoSuchFieldException || e instanceof NoSuchMethodException))
                return new ConstantCallSite(MethodHandles.empty(methodType));
            throw e;
        }
    }
    
    private static MethodHandle putCatch(final MethodHandle handle, final MethodType methodType) {
        if (methodType.returnType() == void.class)
            return handle;
        MethodHandle identity = MethodHandles.identity(methodType.returnType());
        if (methodType.parameterCount() > 1)
            identity = MethodHandles.dropArguments(identity, 0, methodType.parameterType(0));
        return MethodHandles.foldArguments(identity, handle);
    }
    
    private static MethodHandle lookupConstructor(final MethodHandles.Lookup lookup, final Class<?> owner, final MethodType methodType) {
        try {
            final MethodHandle _new = lookup.findSpecial(owner, "new", methodType, owner), identity = MethodHandles.dropArguments(MethodHandles.identity(owner), 1, methodType.parameterArray());
            return MethodHandles.collectArguments(MethodHandles.foldArguments(identity, _new), 0, MethodHandles.explicitCastArguments(MethodHandles.insertArguments(allocateInstance, 0, owner), MethodType.methodType(owner)));
        } catch (final NoSuchMethodException e) { return lookup.findConstructor(owner, methodType); }
    }
    
    @Callback
    static ConstantCallSite makePrivilegeProxy(final MethodHandles.Lookup lookup, final String name, final MethodType methodType)
            = { (MethodHandle) accessHandle.invoke(lookup.lookupClass().getDeclaredMethod(name, methodType.parameterArray())) };
    
    @Callback
    static long allocateMemory(final long bytes) = Unsafe.getUnsafe().allocateMemory(bytes);
    
    @Callback
    static void freeMemory(final long address) = Unsafe.getUnsafe().freeMemory(address);
    
    @Callback
    static Field field(final Class<?> owner, final String name) = owner.getDeclaredField(name);
    
    @Callback
    static Method method(final Class<?> owner, final String name, final MethodType methodType) = owner.getDeclaredMethod(name, methodType.parameterArray());
    
    @Callback
    static Constructor<?> constructor(final Class<?> owner, final MethodType methodType) = owner.getConstructor(methodType.parameterArray());
    
    @Callback
    static VarHandle varHandle(final Class<?> owner, final String name) = lookup().unreflectVarHandle(field(owner, name));
    
    @Callback
    static MethodHandle methodHandle(final Class<?> owner, final String name, final MethodType methodType) = lookup().unreflect(method(owner, name, methodType));
    
    @Callback
    static MethodHandle constructorHandle(final Class<?> owner, final MethodType methodType) = lookup().unreflectConstructor(constructor(owner, methodType));
    
}
