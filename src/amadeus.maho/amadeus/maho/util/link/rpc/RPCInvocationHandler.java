package amadeus.maho.util.link.rpc;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.serialization.Serializable;
import amadeus.maho.util.serialization.base.TrustedByteArrayOutputStream;

import static amadeus.maho.util.concurrent.AsyncHelper.await;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RPCInvocationHandler implements InvocationHandler {
    
    record Metadata(int interfaceIndex, Map<String, Integer> methodIndexes) { }
    
    RPCSocket socket;
    
    Class<?> itf;
    
    Metadata metadata = metadata(itf);
    
    AtomicInteger id = { };
    
    protected Metadata metadata(final Class<?> remoteInterface) {
        final RPCPacket.Sync sync = await(socket().syncFuture());
        @Nullable RPCPacket.Sync.Itf itf = null;
        int interfaceIndex = -1;
        final String name = remoteInterface.getCanonicalName();
        for (int i = 0; i < sync.interfaces.length; i++) {
            if (sync.interfaces[i].name.value.equals(name)) {
                itf = sync.interfaces[i];
                interfaceIndex = i;
                break;
            }
        }
        if (itf == null)
            throw new IllegalArgumentException(STR."Interface not found: \{name}");
        final HashMap<String, Integer> methodIndexes = { };
        for (int i = 0; i < itf.methods; i++)
            methodIndexes[itf.identities[i].value] = i;
        return { interfaceIndex, methodIndexes };
    }
    
    @Override
    public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable = switch (RPCContext.methodIdentity(method)) {
        case "toString()Ljava/lang/String;" -> proxy.toString();
        case "hashCode()I"                  -> proxy.hashCode();
        case "equals(Ljava/lang/Object;)Z"  -> proxy.equals(args[0]);
        default                             -> invoke(method, args);
    };
    
    public CompletableFuture<Object> invokeAsync(final Method method, final Object[] args) throws IOException {
        final Metadata metadata = metadata();
        final @Nullable Integer methodIndex = metadata.methodIndexes()[RPCContext.methodIdentity(method)];
        if (methodIndex == null)
            throw new IllegalArgumentException(STR."Method not found: \{method}");
        final RPCSocket socket = socket();
        final RPCContext context = socket.context();
        final TrustedByteArrayOutputStream buffer = { context.chunkLimit() };
        final Serializable.Output.Limited output = { buffer, buffer.array().length };
        context.serializer().serialization(output, args);
        final RPCPacket.Request request = { };
        request.id = id.incrementAndGet();
        request.interfaceIndex = metadata.interfaceIndex();
        request.methodIndex = methodIndex;
        request.data = { buffer.size(), buffer.array() };
        final CompletableFuture<Object> future = { };
        socket.pendingFlush() += request;
        socket.pendingFutures()[request.id] = future;
        return future;
    }
    
    public Object invoke(final Method method, final Object[] args) throws Throwable = await(invokeAsync(method, args));
    
}
