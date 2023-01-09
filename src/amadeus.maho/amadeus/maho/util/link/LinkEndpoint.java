package amadeus.maho.util.link;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.inspection.Nullable;

import static amadeus.maho.util.concurrent.AsyncHelper.*;

@Getter
@ToString
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LinkEndpoint {
    
    final Protocol protocol;
    
    final RemoteEndpoint endpoint;
    
    final Selector selector;
    
    final InetSocketAddress address;
    
    final String threadName;
    
    final Consumer<Throwable> throwableConsumer;
    
    @Setter
    transient boolean keepRunning;
    
    transient @Nullable AbstractSelectableChannel channel;
    
    transient @Nullable CompletableFuture<Void> future;
    
    public boolean isRunning() = future() != null;
    
    public void start() throws IOException {
        if (endpoint == RemoteEndpoint.SERVER)
            (channel = ServerSocketChannel.open().bind(address))
                    .configureBlocking(false)
                    .register(selector(), SelectionKey.OP_ACCEPT);
        else
            protocol().link((channel = SocketChannel.open(address))
                    .configureBlocking(false)
                    .register(selector(), 0), endpoint);
        future = async(this::runLoop, newThreadExecutor(threadName));
    }
    
    public void shutdown() throws IOException {
        keepRunning(false);
        selector.close();
    }
    
    protected void runLoop() {
        keepRunning(true);
        while (keepRunning())
            loop();
    }
    
    protected void loop() {
        try {
            selector.select(protocol().timeout());
            selector.keys().forEach(this::checkSelectionKeyTimeout);
            selector.selectedKeys().removeIf(this::handleSelectionKey);
        } catch (final ClosedSelectorException ignored) { } catch (final Throwable throwable) { throwableConsumer.accept(throwable); }
    }
    
    protected void checkSelectionKeyTimeout(final SelectionKey key) {
        try {
            protocol().checkTimeout(key);
        } catch (final IOException e) {
            throwableConsumer().accept(e);
            disconnect(key);
        }
    }
    
    protected boolean handleSelectionKey(final SelectionKey key) {
        if (key.isAcceptable())
            tryAccept(key);
        else
            try {
                protocol().handle(key);
            } catch (final IOException e) {
                throwableConsumer().accept(e);
                disconnect(key);
            }
        return true;
    }
    
    protected void disconnect(final SelectionKey key) {
        try {
            key.cancel();
            key.channel().close();
        } catch (final IOException e) { throwableConsumer().accept(e); }
    }
    
    protected void tryAccept(final SelectionKey key) {
        System.out.println("tryAccept");
        try {
            if (key.channel() instanceof ServerSocketChannel) {
                final SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
                if (channel != null) {
                    System.out.println("acceptSelectionKey: " + channel.getRemoteAddress());
                    protocol().checkSocketChannel(channel);
                    protocol().link(channel.configureBlocking(false).register(key.selector(), 0), endpoint());
                }
            }
        } catch (final IOException e) { throwableConsumer().accept(e); }
    }
    
}
