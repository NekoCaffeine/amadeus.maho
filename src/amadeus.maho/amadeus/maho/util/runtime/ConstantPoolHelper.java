package amadeus.maho.util.runtime;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import jdk.internal.reflect.ConstantPool;

import amadeus.maho.lang.Extension;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.base.TransformProvider;

import static jdk.internal.reflect.ConstantPool.Tag.FIELDREF;

@Extension
@TransformProvider
public interface ConstantPoolHelper {
    
    @SneakyThrows
    MethodHandle constantPool = MethodHandleHelper.lookup().findVirtual(Class.class, "getConstantPool", MethodType.methodType(ConstantPool.class));
    
    @SneakyThrows
    static ConstantPool constantPool(final Class<?> $this) = (ConstantPool) constantPool.invokeExact($this);
    
    static List<Executable> methods(final ConstantPool pool) = new ArrayList<Executable>().let(it -> {
        for (int i = 0, len = pool.getSize(); i < len; i++)
            switch (pool.getTagAt(i)) {
                case METHODREF, INTERFACEMETHODREF -> it += (Executable) pool.getMethodAt(i);
            }
    });
    
    static List<Field> fields(final ConstantPool pool) = new ArrayList<Field>().let(it -> {
        for (int i = 0, len = pool.getSize(); i < len; i++)
            if (pool.getTagAt(i) == FIELDREF)
                it += pool.getFieldAt(i);
    });
    
    static @Nullable Constructor<?> lastConstructor(final ConstantPool pool) {
        for (int i = pool.getSize() - 1; i > -1; i--)
            if (switch (pool.getTagAt(i)) {
                case METHODREF, INTERFACEMETHODREF -> pool.getMethodAt(i);
                default                            -> null;
            } instanceof Constructor<?> constructor)
                return constructor;
        return null;
    }
    
    static @Nullable Constructor<?> firstConstructor(final ConstantPool pool) {
        for (int i = 0, len = pool.getSize(); i < len; i++)
            if (switch (pool.getTagAt(i)) {
                case METHODREF, INTERFACEMETHODREF -> pool.getMethodAt(i);
                default                            -> null;
            } instanceof Constructor<?> constructor)
                return constructor;
        return null;
    }
    
    static @Nullable Method lastMethod(final ConstantPool pool) {
        for (int i = pool.getSize() - 1; i > -1; i--)
            if (switch (pool.getTagAt(i)) {
                case METHODREF, INTERFACEMETHODREF -> pool.getMethodAt(i);
                default                            -> null;
            } instanceof Method method)
                return method;
        return null;
    }
    
    static @Nullable Method firstMethod(final ConstantPool pool) {
        for (int i = 0, len = pool.getSize(); i < len; i++)
            if (switch (pool.getTagAt(i)) {
                case METHODREF, INTERFACEMETHODREF -> pool.getMethodAt(i);
                default                            -> null;
            } instanceof Method method)
                return method;
        return null;
    }
    
    static @Nullable Method lastMethodWithoutBoxed(final ConstantPool pool) {
        @Nullable Method last = null;
        for (int i = pool.getSize() - 1; i > -1; i--) {
            if (switch (pool.getTagAt(i)) {
                case METHODREF, INTERFACEMETHODREF -> pool.getMethodAt(i);
                default                            -> null;
            } instanceof Method method) {
                if (last != null)
                    if (last.getParameters()[0].getType() == method.getReturnType())
                        return method;
                    else
                        return last;
                if (TypeHelper.isBoxedMethod(method))
                    last = method;
                else
                    return method;
            }
        }
        return last;
    }
    
    static @Nullable Executable lastExecutableWithoutBoxed(final ConstantPool pool) {
        @Nullable Method last = null;
        for (int i = pool.getSize() - 1; i > -1; i--) {
            switch (switch (pool.getTagAt(i)) {
                case METHODREF, INTERFACEMETHODREF -> pool.getMethodAt(i);
                default                            -> null;
            }) {
                case Method method -> {
                    if (last != null)
                        if (last.getParameters()[0].getType() == method.getReturnType())
                            return method;
                        else
                            return last;
                    if (TypeHelper.isBoxedMethod(method))
                        last = method;
                    else
                        return method;
                }
                case Constructor<?> constructor -> { return constructor; }
                case null, default -> { }
            }
        }
        return last;
    }
    
    static @Nullable Field firstField(final ConstantPool pool) {
        for (int i = 0, len = pool.getSize(); i < len; i++)
            if (pool.getTagAt(i) == FIELDREF)
                return pool.getFieldAt(i);
        return null;
    }
    
    static @Nullable Field lastField(final ConstantPool pool) {
        for (int i = pool.getSize() - 1; i > -1; i--)
            if (pool.getTagAt(i) == FIELDREF)
                return pool.getFieldAt(i);
        return null;
    }
    
}
