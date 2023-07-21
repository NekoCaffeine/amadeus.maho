package amadeus.maho.util.dynamic;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Function;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodNode;

import amadeus.maho.core.Maho;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.javac.handler.LookupHandler;
import amadeus.maho.lang.mark.OptimizedBy;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.function.Consumer0;
import amadeus.maho.util.function.Consumer1;
import amadeus.maho.util.function.Consumer10;
import amadeus.maho.util.function.Consumer11;
import amadeus.maho.util.function.Consumer12;
import amadeus.maho.util.function.Consumer13;
import amadeus.maho.util.function.Consumer14;
import amadeus.maho.util.function.Consumer15;
import amadeus.maho.util.function.Consumer2;
import amadeus.maho.util.function.Consumer3;
import amadeus.maho.util.function.Consumer4;
import amadeus.maho.util.function.Consumer5;
import amadeus.maho.util.function.Consumer6;
import amadeus.maho.util.function.Consumer7;
import amadeus.maho.util.function.Consumer8;
import amadeus.maho.util.function.Consumer9;
import amadeus.maho.util.function.Function0;
import amadeus.maho.util.function.Function1;
import amadeus.maho.util.function.Function10;
import amadeus.maho.util.function.Function11;
import amadeus.maho.util.function.Function12;
import amadeus.maho.util.function.Function13;
import amadeus.maho.util.function.Function14;
import amadeus.maho.util.function.Function15;
import amadeus.maho.util.function.Function2;
import amadeus.maho.util.function.Function3;
import amadeus.maho.util.function.Function4;
import amadeus.maho.util.function.Function5;
import amadeus.maho.util.function.Function6;
import amadeus.maho.util.function.Function7;
import amadeus.maho.util.function.Function8;
import amadeus.maho.util.function.Function9;
import amadeus.maho.util.runtime.MethodHandleHelper;
import amadeus.maho.util.runtime.ObjectHelper;

@OptimizedBy(LookupHandler.class) // inline dynamic constant
public interface LookupHelper {
    
    @SneakyThrows
    static <T> @Nullable Field lookupField(final Function<T, ?> fieldGetter) {
        final @Nullable Method getter = fieldGetter.getClass().constantPool().lastMethodWithoutBoxed();
        if (getter == null)
            return null;
        final String name = getter.getName(), desc = Type.getMethodDescriptor(getter);
        final @Nullable ClassNode node = Maho.getClassNodeFromClass(getter.getDeclaringClass());
        if (node == null)
            return null;
        final @Nullable MethodNode target = node.methods.stream()
                .filter(methodNode -> methodNode.name.equals(name) && methodNode.desc.equals(desc))
                .findAny()
                .orElse(null);
        if (target == null)
            return null;
        for (final var insn : target.instructions)
            if (insn instanceof FieldInsnNode fieldInsn)
                return ASMHelper.loadType(Type.getObjectType(fieldInsn.owner), false, getter.getDeclaringClass().getClassLoader()).getDeclaredField(fieldInsn.name);
        return null;
    }
    
    static <T> Field field(final Function<T, ?> fieldGetter) = ObjectHelper.requireNonNull(lookupField(fieldGetter));
    
    @SneakyThrows
    static <T> VarHandle varHandle(final Function<T, ?> fieldGetter) = MethodHandleHelper.lookup().unreflectVarHandle(lookupField(fieldGetter));
    
    static Method lookupMethod(final Object methodReference) = ObjectHelper.requireNonNull(methodReference.getClass().constantPool().lastMethodWithoutBoxed());
    
    static <R> Constructor<R> lookupConstructor(final Object methodReference) = (Constructor<R>) methodReference.getClass().constantPool().lastConstructor();
    
    @SneakyThrows
    static MethodHandle lookupMethodHandle(final Object methodReference) = MethodHandleHelper.lookup().unreflect(lookupMethod(methodReference));
    
    @SneakyThrows
    static MethodHandle lookupConstructorHandle(final Object methodReference) = MethodHandleHelper.lookup().unreflectConstructor(lookupConstructor(methodReference));
    
    static Method methodV0(final Consumer0 methodReference) = lookupMethod(methodReference);
    
    static <R> Method method0(final Function0<R> methodReference) = lookupMethod(methodReference);
    
    static <T1> Method methodV1(final Consumer1<T1> methodReference) = lookupMethod(methodReference);
    
    static <T1, R> Method method1(final Function1<T1, R> methodReference) = lookupMethod(methodReference);
    
    static <T1, T2> Method methodV2(final Consumer2<T1, T2> methodReference) = lookupMethod(methodReference);
    
    static <T1, T2, R> Method method2(final Function2<T1, T2, R> methodReference) = lookupMethod(methodReference);
    
    static <T1, T2, T3> Method methodV3(final Consumer3<T1, T2, T3> methodReference) = lookupMethod(methodReference);
    
    static <T1, T2, T3, R> Method method3(final Function3<T1, T2, T3, R> methodReference) = lookupMethod(methodReference);
    
    static <T1, T2, T3, T4> Method methodV4(final Consumer4<T1, T2, T3, T4> methodReference) = lookupMethod(methodReference);
    
    static <T1, T2, T3, T4, R> Method method4(final Function4<T1, T2, T3, T4, R> methodReference) = lookupMethod(methodReference);
    
    static <T1, T2, T3, T4, T5> Method methodV5(final Consumer5<T1, T2, T3, T4, T5> methodReference) = lookupMethod(methodReference);
    
    static <T1, T2, T3, T4, T5, R> Method method5(final Function5<T1, T2, T3, T4, T5, R> methodReference) = lookupMethod(methodReference);
    
    static <T1, T2, T3, T4, T5, T6> Method methodV6(final Consumer6<T1, T2, T3, T4, T5, T6> methodReference) = lookupMethod(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, R> Method method6(final Function6<T1, T2, T3, T4, T5, T6, R> methodReference) = lookupMethod(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7> Method methodV7(final Consumer7<T1, T2, T3, T4, T5, T6, T7> methodReference) = lookupMethod(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, R> Method method7(final Function7<T1, T2, T3, T4, T5, T6, T7, R> methodReference) = lookupMethod(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8> Method methodV8(final Consumer8<T1, T2, T3, T4, T5, T6, T7, T8> methodReference) = lookupMethod(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, R> Method method8(final Function8<T1, T2, T3, T4, T5, T6, T7, T8, R> methodReference) = lookupMethod(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9> Method methodV9(final Consumer9<T1, T2, T3, T4, T5, T6, T7, T8, T9> methodReference) = lookupMethod(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9, R> Method method9(final Function9<T1, T2, T3, T4, T5, T6, T7, T8, T9, R> methodReference) = lookupMethod(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> Method methodV10(final Consumer10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> methodReference) = lookupMethod(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, R> Method method10(final Function10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, R> methodReference) = lookupMethod(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> Method methodV11(final Consumer11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> methodReference) = lookupMethod(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, R> Method method11(final Function11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, R> methodReference) = lookupMethod(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> Method methodV12(final Consumer12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> methodReference) = lookupMethod(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, R> Method method12(final Function12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, R> methodReference) = lookupMethod(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> Method methodV13(final Consumer13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> methodReference) = lookupMethod(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, R> Method method13(final Function13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, R> methodReference) = lookupMethod(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> Method methodV14(final Consumer14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> methodReference) = lookupMethod(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, R> Method method14(final Function14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, R> methodReference) = lookupMethod(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> Method methodV15(final Consumer15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> methodReference) = lookupMethod(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, R> Method method15(final Function15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, R> methodReference) = lookupMethod(methodReference);
    
    static <R> Constructor<R> constructor0(final Function0<R> methodReference) = lookupConstructor(methodReference);
    
    static <T1, R> Constructor<R> constructor1(final Function1<T1, R> methodReference) = lookupConstructor(methodReference);
    
    static <T1, T2, R> Constructor<R> constructor2(final Function2<T1, T2, R> methodReference) = lookupConstructor(methodReference);
    
    static <T1, T2, T3, R> Constructor<R> constructor3(final Function3<T1, T2, T3, R> methodReference) = lookupConstructor(methodReference);
    
    static <T1, T2, T3, T4, R> Constructor<R> constructor4(final Function4<T1, T2, T3, T4, R> methodReference) = lookupConstructor(methodReference);
    
    static <T1, T2, T3, T4, T5, R> Constructor<R> constructor5(final Function5<T1, T2, T3, T4, T5, R> methodReference) = lookupConstructor(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, R> Constructor<R> constructor6(final Function6<T1, T2, T3, T4, T5, T6, R> methodReference) = lookupConstructor(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, R> Constructor<R> constructor7(final Function7<T1, T2, T3, T4, T5, T6, T7, R> methodReference) = lookupConstructor(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, R> Constructor<R> constructor8(final Function8<T1, T2, T3, T4, T5, T6, T7, T8, R> methodReference) = lookupConstructor(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9, R> Constructor<R> constructor9(final Function9<T1, T2, T3, T4, T5, T6, T7, T8, T9, R> methodReference) = lookupConstructor(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, R> Constructor<R> constructor10(final Function10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, R> methodReference) = lookupConstructor(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, R> Constructor<R> constructor11(final Function11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, R> methodReference) = lookupConstructor(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, R> Constructor<R> constructor12(final Function12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, R> methodReference) = lookupConstructor(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, R> Constructor<R> constructor13(final Function13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, R> methodReference) = lookupConstructor(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, R> Constructor<R> constructor14(final Function14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, R> methodReference) = lookupConstructor(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, R> Constructor<R> constructor15(final Function15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, R> methodReference) = lookupConstructor(methodReference);
    
    static MethodHandle methodHandleV0(final Consumer0 methodReference) = lookupMethodHandle(methodReference);
    
    static <R> MethodHandle methodHandle0(final Function0<R> methodReference) = lookupMethodHandle(methodReference);
    
    static <T1> MethodHandle methodHandleV1(final Consumer1<T1> methodReference) = lookupMethodHandle(methodReference);
    
    static <T1, R> MethodHandle methodHandle1(final Function1<T1, R> methodReference) = lookupMethodHandle(methodReference);
    
    static <T1, T2> MethodHandle methodHandleV2(final Consumer2<T1, T2> methodReference) = lookupMethodHandle(methodReference);
    
    static <T1, T2, R> MethodHandle methodHandle2(final Function2<T1, T2, R> methodReference) = lookupMethodHandle(methodReference);
    
    static <T1, T2, T3> MethodHandle methodHandleV3(final Consumer3<T1, T2, T3> methodReference) = lookupMethodHandle(methodReference);
    
    static <T1, T2, T3, R> MethodHandle methodHandle3(final Function3<T1, T2, T3, R> methodReference) = lookupMethodHandle(methodReference);
    
    static <T1, T2, T3, T4> MethodHandle methodHandleV4(final Consumer4<T1, T2, T3, T4> methodReference) = lookupMethodHandle(methodReference);
    
    static <T1, T2, T3, T4, R> MethodHandle methodHandle4(final Function4<T1, T2, T3, T4, R> methodReference) = lookupMethodHandle(methodReference);
    
    static <T1, T2, T3, T4, T5> MethodHandle methodHandleV5(final Consumer5<T1, T2, T3, T4, T5> methodReference) = lookupMethodHandle(methodReference);
    
    static <T1, T2, T3, T4, T5, R> MethodHandle methodHandle5(final Function5<T1, T2, T3, T4, T5, R> methodReference) = lookupMethodHandle(methodReference);
    
    static <T1, T2, T3, T4, T5, T6> MethodHandle methodHandleV6(final Consumer6<T1, T2, T3, T4, T5, T6> methodReference) = lookupMethodHandle(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, R> MethodHandle methodHandle6(final Function6<T1, T2, T3, T4, T5, T6, R> methodReference) = lookupMethodHandle(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7> MethodHandle methodHandleV7(final Consumer7<T1, T2, T3, T4, T5, T6, T7> methodReference) = lookupMethodHandle(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, R> MethodHandle methodHandle7(final Function7<T1, T2, T3, T4, T5, T6, T7, R> methodReference) = lookupMethodHandle(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8> MethodHandle methodHandleV8(final Consumer8<T1, T2, T3, T4, T5, T6, T7, T8> methodReference) = lookupMethodHandle(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, R> MethodHandle methodHandle8(final Function8<T1, T2, T3, T4, T5, T6, T7, T8, R> methodReference) = lookupMethodHandle(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9> MethodHandle methodHandleV9(final Consumer9<T1, T2, T3, T4, T5, T6, T7, T8, T9> methodReference) = lookupMethodHandle(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9, R> MethodHandle methodHandle9(final Function9<T1, T2, T3, T4, T5, T6, T7, T8, T9, R> methodReference) = lookupMethodHandle(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> MethodHandle methodHandleV10(final Consumer10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> methodReference) = lookupMethodHandle(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, R> MethodHandle methodHandle10(final Function10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, R> methodReference) = lookupMethodHandle(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> MethodHandle methodHandleV11(final Consumer11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> methodReference) = lookupMethodHandle(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, R> MethodHandle methodHandle11(final Function11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, R> methodReference) = lookupMethodHandle(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> MethodHandle methodHandleV12(final Consumer12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> methodReference) = lookupMethodHandle(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, R> MethodHandle methodHandle12(final Function12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, R> methodReference) = lookupMethodHandle(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> MethodHandle methodHandleV13(final Consumer13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> methodReference) = lookupMethodHandle(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, R> MethodHandle methodHandle13(final Function13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, R> methodReference) = lookupMethodHandle(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> MethodHandle methodHandleV14(final Consumer14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> methodReference) = lookupMethodHandle(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, R> MethodHandle methodHandle14(final Function14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, R> methodReference) = lookupMethodHandle(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> MethodHandle methodHandleV15(final Consumer15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> methodReference) = lookupMethodHandle(methodReference);
    
    static <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, R> MethodHandle methodHandle15(final Function15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, R> methodReference) = lookupMethodHandle(methodReference);
    
}
