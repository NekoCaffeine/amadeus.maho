package amadeus.maho.util.link.rpc;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.util.concurrent.ConcurrentWeakIdentityHashMap;
import amadeus.maho.util.runtime.ObjectHelper;
import amadeus.maho.util.runtime.ReflectionHelper;
import amadeus.maho.util.serialization.Serializer;
import amadeus.maho.util.serialization.base.LengthFirstString;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RPCContext {
    
    Executor executor;
    
    Serializer.Root serializer;
    
    Class<?> interfaces[];
    
    @Default
    int maxPending = 64, chunkLimit = 8192;
    
    RPCPacket.Sync sync = sync(interfaces);
    
    private static final ConcurrentWeakIdentityHashMap<Method, String> methodNameAndSignatures = { };
    
    public static String methodIdentity(final Method method) = methodNameAndSignatures.computeIfAbsent(method, m -> org.objectweb.asm.commons.Method.getMethod(m).toString());
    
    private static final ConcurrentWeakIdentityHashMap<Class<?>, List<Method>> allMethods = { };
    
    public static List<Method> allMethods(final Class<?> itf) = allMethods.computeIfAbsent(itf, it -> ReflectionHelper.allMethods(it).stream().filterNot(ObjectHelper.objectBaseMethods::contains).toList());
    
    public static RPCPacket.Sync sync(final Class<?> interfaces[]) {
        final RPCPacket.Sync sync = { };
        sync.count = interfaces.length;
        sync.interfaces = Stream.of(interfaces).map(RPCContext::sync).toArray(RPCPacket.Sync.Itf[]::new);
        return sync;
    }
    
    public static RPCPacket.Sync.Itf sync(final Class<?> itf) {
        final List<Method> methods = allMethods(itf);
        final RPCPacket.Sync.Itf sync = { };
        sync.name = { itf.getCanonicalName() };
        sync.methods = methods.size();
        sync.identities = methods.stream().map(RPCContext::methodIdentity).map(LengthFirstString::new).toArray(LengthFirstString[]::new);
        return sync;
    }
    
}
