package amadeus.maho.core.extension;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import amadeus.maho.core.Maho;
import amadeus.maho.core.MahoProfile;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.annotation.mark.IndirectCaller;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.container.Indexed;
import amadeus.maho.util.dynamic.ClassLocal;
import amadeus.maho.util.dynamic.DynamicMethod;
import amadeus.maho.util.profile.Sampler;
import amadeus.maho.util.tuple.Tuple;
import amadeus.maho.util.tuple.Tuple2;

import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;

public interface DynamicLookupHelper {
    
    Sampler<String> sampler = MahoProfile.sampler("makeAllPrivilegeProxy");
    
    String shareDynamicLookup = Maho.SHARE_PACKAGE + ".DynamicLookup", DynamicLookupName = ASMHelper.className(shareDynamicLookup), _new = "new";
    
    static MethodType methodType(final String desc, final @Nullable ClassLoader loader) = ASMHelper.loadMethodType(desc, loader);
    
    Indexed.Default<ClassLoader> loaderIndexed = { };
    
    static int submit(final @Nullable ClassLoader loader) = loaderIndexed.submit(loader == null ? ClassLoader.getPlatformClassLoader() : loader);
    
    static ClassLoader fetch(final int id) = loaderIndexed.fetch(id);
    
    @SneakyThrows
    static Class<?> loadClass(final int id, final String name) {
        try {
            return Class.forName(name, false, fetch(id));
        } catch (final Throwable e) {
            if (e instanceof StackOverflowError error)
                throw error;
            try {
                return Class.forName(name, false, ClassLoader.getSystemClassLoader());
            } catch (final Throwable ex) {
                if (ex instanceof StackOverflowError error)
                    throw error;
                return Maho.findLoadedClassByName(name)
                        .findFirst()
                        .orElseThrow(() -> {
                            final ClassNotFoundException result = { name };
                            result.addSuppressed(e);
                            result.addSuppressed(ex);
                            return result;
                        });
            }
        }
    }
    
    ClassLocal<Map<Method, MethodHandle>> privilegeProxies = { DynamicLookupHelper::makePrivilegeProxies };
    
    static Map<Method, MethodHandle> makePrivilegeProxies(final Class<?> clazz, final ClassNode node = Maho.getClassNodeFromClass(clazz)) = Stream.of(clazz.getDeclaredMethods())
            .filter(method -> method.isAnnotationPresent(Privilege.Mark.class))
            .map(method -> Tuple.tuple(method, dynamic(clazz, node, method).handle()))
            .collect(Collectors.toMap(Tuple2::v1, Tuple2::v2));
    
    private static DynamicMethod dynamic(final Class<?> target, final ClassNode node, final Method method) {
        final DynamicMethod dynamicMethod = DynamicMethod.ofMethod(target.getClassLoader(), STR."PrivilegeProxy$\{target.getSimpleName()}$\{method.getName()}", method, node);
        dynamicMethod.sourceFile(node.sourceFile);
        dynamicMethod.sourceDebug(node.sourceDebug);
        dynamicMethod.nestHostClass(ASMHelper.className(target));
        return dynamicMethod;
    }
    
    @SneakyThrows
    @IndirectCaller
    static MethodHandle accessHandle(final Method method) = privilegeProxies[method.getDeclaringClass()][method];
    
    Handle makeSiteByNameWithBoot = {
            H_INVOKESTATIC,
            DynamicLookupName,
            "makeSiteByNameWithBoot",
            Type.getMethodDescriptor(
                    ASMHelper.TYPE_CALL_SITE,
                    ASMHelper.TYPE_METHOD_HANDLES_LOOKUP,
                    ASMHelper.TYPE_STRING,
                    ASMHelper.TYPE_METHOD_TYPE,
                    Type.INT_TYPE,
                    ASMHelper.TYPE_STRING,
                    ASMHelper.TYPE_STRING
            ),
            true
    };
    
    Handle makeSiteByName = {
            H_INVOKESTATIC,
            DynamicLookupName,
            "makeSiteByName",
            Type.getMethodDescriptor(
                    ASMHelper.TYPE_CALL_SITE,
                    ASMHelper.TYPE_METHOD_HANDLES_LOOKUP,
                    ASMHelper.TYPE_STRING,
                    ASMHelper.TYPE_METHOD_TYPE,
                    Type.INT_TYPE,
                    Type.INT_TYPE,
                    ASMHelper.TYPE_STRING,
                    ASMHelper.TYPE_STRING
            ),
            true
    };
    
    Handle makeSiteByClass = {
            H_INVOKESTATIC,
            DynamicLookupName,
            "makeSiteByClass",
            Type.getMethodDescriptor(
                    ASMHelper.TYPE_CALL_SITE,
                    ASMHelper.TYPE_METHOD_HANDLES_LOOKUP,
                    ASMHelper.TYPE_STRING,
                    ASMHelper.TYPE_METHOD_TYPE,
                    Type.INT_TYPE,
                    ASMHelper.TYPE_CLASS,
                    ASMHelper.TYPE_STRING
            ),
            true
    };
    
}
