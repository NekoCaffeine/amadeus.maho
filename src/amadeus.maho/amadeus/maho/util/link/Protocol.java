package amadeus.maho.util.link;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.ToString;
import amadeus.maho.util.runtime.ByteBufferHelper;

public interface Protocol {
    
    interface State {
        
        static State magic(final RemoteEndpoint endpoint, final LongSupplier magicSupplier, final State next) = new State() {
            
            @Override
            public void init(final Context context) {
                if (context.endpoint() == endpoint) {
                    final ByteBuffer buffer = context.writeBuffer();
                    buffer.putLong(magicSupplier.getAsLong());
                    buffer.flip();
                    context.key().interestOps(SelectionKey.OP_WRITE);
                } else {
                    context.readBuffer().limit(Long.BYTES);
                    context.key().interestOps(SelectionKey.OP_READ);
                }
            }
            
            @Override
            public void handle(final Context context) throws IOException {
                if (context.endpoint() == endpoint) {
                    final ByteBuffer buffer = context.writeBuffer();
                    context.socketChannel().write(buffer);
                    if (buffer.remaining() != 0)
                        return;
                    buffer.clear();
                    context.markLastCheckedTime();
                    context.state(next);
                } else {
                    final ByteBuffer buffer = context.readBuffer();
                    context.socketChannel().read(buffer);
                    if (buffer.remaining() != 0)
                        return;
                    buffer.flip();
                    final long value = buffer.getLong();
                    buffer.clear();
                    if (value != magicSupplier.getAsLong())
                        context.disconnect(MESSAGE_INVALID_VALUE);
                    context.markLastCheckedTime();
                    context.state(next);
                }
            }
        };
    
        void init(final Context context);
        
        void handle(Context context) throws IOException;
        
    }
    
    @Getter
    @ToString
    @FieldDefaults(level = AccessLevel.PRIVATE)
    class Context {
        
        final Protocol protocol;
        
        final SelectionKey key;
        
        final RemoteEndpoint endpoint;
        
        State state;
        
        long lastCheckedTime = System.currentTimeMillis();
        
        @Setter
        ByteBuffer readBuffer;
        
        @Setter
        ByteBuffer writeBuffer;
        
        final AtomicReference attachmentReference = { };
        
        public void state(final State value) {
            state = value;
            state.init(this);
        }
        
        public void markLastCheckedTime() = lastCheckedTime = System.currentTimeMillis();
        
        public <T> T attachment() = (T) attachmentReference.get();
    
        public <T, R> T attach(final R value) = (T) attachmentReference.getAndSet(value);
    
        public ByteBuffer expansionBuffer(final ByteBuffer buffer, final int size, final Context context, final RemoteEndpoint target) = ByteBufferHelper.expansionBuffer(buffer, size);
    
        public boolean isLocal(final RemoteEndpoint endpoint) = endpoint() == endpoint;
        
        public boolean isRemote(final RemoteEndpoint endpoint) = endpoint() != endpoint;
        
        public void swapOps() {
            final SelectionKey key = key();
            if (key.isReadable() != key.isWritable())
                if (key.isReadable())
                    key.interestOps(SelectionKey.OP_WRITE);
                else
                    key.interestOps(SelectionKey.OP_READ);
        }
        
        public SocketChannel socketChannel() = (SocketChannel) key().channel();
        
        public void disconnect(final String message) throws IOException {
            key().interestOps(SelectionKey.OP_CONNECT);
            throw new IOException(protocol().disconnectMessage(this, message));
        }
        
        public Context(final Protocol protocol, final SelectionKey key, final RemoteEndpoint endpoint, final ByteBuffer readBuffer, final ByteBuffer writeBuffer) {
            this.protocol = protocol;
            this.key = key;
            this.endpoint = endpoint;
            this.readBuffer = readBuffer;
            this.writeBuffer = writeBuffer;
        }
        
    }
    
    String MESSAGE_INVALID_VALUE = "Invalid set.";
    
    State initState();
    
    default long timeout() = 3000L;
    
    default ByteBuffer initBuffer(final SelectionKey key, final RemoteEndpoint endpoint, final boolean local) {
        final ByteBuffer result = ByteBuffer.allocate(1024);
        if (!local)
            result.flip();
        return result;
    }
    
    default Context initContext(final SelectionKey key, final RemoteEndpoint endpoint) = { this, key, endpoint, initBuffer(key, endpoint, false), initBuffer(key, endpoint, true) };
    
    default void checkSocketChannel(final SocketChannel channel) { }
    
    default void link(final SelectionKey key, final RemoteEndpoint endpoint) {
        final Context context = initContext(key, endpoint);
        context.state(initState());
        key.attach(context);
    }
    
    default void checkTimeout(final SelectionKey key) throws IOException {
        if (key.attachment() instanceof Context context)
            if (timeout() > 0 && System.currentTimeMillis() - context.lastCheckedTime() > timeout())
                throw new IOException(new TimeoutException());
    }
    
    default void handle(final SelectionKey key) throws IOException {
        if (key.attachment() instanceof Context context) {
            if (context.state() == null)
                throw new IOException("context.state() == null");
            context.state().handle(context);
        }
    }
    
    default String disconnectMessage(final Context context, final String message) throws IOException = STR."disconnect[\{context.socketChannel().getRemoteAddress()}]: \{message}";
    
}
