package amadeus.maho.util.link.rpc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.atomic.AtomicInteger;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.concurrent.ConcurrentWeakIdentityHashMap;
import amadeus.maho.util.runtime.ObjectHelper;
import amadeus.maho.util.serialization.Deserializable;
import amadeus.maho.util.serialization.Serializable;
import amadeus.maho.util.serialization.base.ByteArray;
import amadeus.maho.util.serialization.base.TrustedByteArrayOutputStream;

import static amadeus.maho.util.link.rpc.RPCPacket.*;
import static amadeus.maho.util.runtime.ObjectHelper.requireNonNull;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RPCSocket {
    
    Socket socket;
    
    RPCContext context;
    
    Executor localExecutor;
    
    AtomicInteger missing = { };
    
    ConcurrentWeakIdentityHashMap<Class<?>, Object> localInstances = { }, remoteInstances = { };
    
    CompletableFuture<String> running = { };
    
    CompletableFuture<Sync> syncFuture = { };
    
    LinkedTransferQueue<RPCPacket> pendingFlush = { };
    
    ConcurrentHashMap<Integer, CompletableFuture<Object>> pendingFutures = { };
    
    @SneakyThrows
    public void runLoop() throws IOException {
        final Executor executor = context().executor();
        executor.execute(() -> runLoop(socket().getInputStream()));
        executor.execute(() -> runLoop(socket().getOutputStream()));
    }
    
    @SneakyThrows
    public void runLoop(final InputStream input) throws IOException {
        while (!running.isDone()) {
            final RPCPacket packet = readPacket(context(), input);
            switch (packet) {
                case Sync sync         -> syncFuture.complete(sync);
                case Request request   -> {
                    final ByteArray data = request.data;
                    final int[] p_code = { ERROR_INVALID_INTERFACE };
                    try {
                        final Class<?> itf = context().interfaces()[request.interfaceIndex];
                        p_code[0] = ERROR_MISSING_INSTANCE;
                        final Object instance = requireNonNull(localInstances()[itf]);
                        p_code[0] = ERROR_INVALID_METHOD;
                        final Method method = requireNonNull(RPCContext.allMethods(itf)[request.methodIndex]);
                        localExecutor().execute(() -> {
                            try {
                                p_code[0] = ERROR_INVALID_DATA;
                                final @Nullable Object args[] = (Object[]) context().serializer().deserialization(new Deserializable.Input.Limited(new ByteArrayInputStream(data.value), data.length));
                                p_code[0] = ERROR_INVOKE_EXCEPTION;
                                final Object result = method.invoke(instance, args);
                                response(request.id, 0, result);
                            } catch (final Throwable e) {
                                response(request.id, p_code[0], e.getMessage());
                            }
                        });
                    } catch (final Throwable e) { response(request.id, p_code[0], e); }
                }
                case Response response -> {
                    final @Nullable CompletableFuture<Object> future = pendingFutures()[response.id];
                    if (future != null)
                        localExecutor().execute(() -> {
                            switch (response.code) {
                                case ERROR_SERIALIZATION_RESULT -> future.completeExceptionally(new RPCException(response.code));
                                default                         -> {
                                    try {
                                        final @Nullable Object result = context().serializer().deserialization(new Deserializable.Input.Limited(new ByteArrayInputStream(response.data.value), response.data.length));
                                        if (response.code != 0)
                                            future.completeExceptionally(new RPCException(ObjectHelper.toString(result), response.code));
                                        else
                                            future.complete(result);
                                    } catch (final Throwable e) { future.completeExceptionally(e); }
                                }
                            }
                            
                        });
                    else
                        missing.getAndIncrement();
                }
                case Close close       -> running.complete(close.message.value);
            }
        }
    }
    
    protected void response(final int id, final int code, final Object result) throws IOException {
        final Response response = { };
        response.id = id;
        try {
            final TrustedByteArrayOutputStream buffer = { context.chunkLimit() };
            final Serializable.Output.Limited output = { buffer, buffer.array().length };
            context().serializer().serialization(output, result);
            response.code = code;
            response.data = { buffer.size(), buffer.array() };
        } catch (final Throwable e) {
            response.code = ERROR_SERIALIZATION_RESULT;
            response.data = { };
        }
        pendingFlush() += response;
    }
    
    public void runLoop(final OutputStream output) throws IOException {
        writePacket(context(), output, context().sync());
        while (!running.isDone())
            try {
                writePacket(context(), output, pendingFlush.take());
            } catch (final InterruptedException _) { }
    }
    
    public <T> T projection(final ClassLoader loader = itf.getClassLoader(), final Class<T> itf)
            = (T) remoteInstances().computeIfAbsent(itf, it -> Proxy.newProxyInstance(loader, new Class[]{ it }, new RPCInvocationHandler(this, it)));
    
}
