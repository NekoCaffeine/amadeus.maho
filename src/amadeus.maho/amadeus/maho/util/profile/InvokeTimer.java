package amadeus.maho.util.profile;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.invoke.MethodType;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import amadeus.maho.intercept.Interceptor;
import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.Extension;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.container.Node;
import amadeus.maho.util.control.LinkedForkedIterator;

@FieldDefaults(level = AccessLevel.PROTECTED)
public class InvokeTimer implements Interceptor {
    
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PUBLIC)
    public static class InvokeNode {
        
        final Class<?> clazz;
        
        final String name, desc;
        
        @Getter
        protected long startTime, endTime;
        
        public long sum() = endTime() - startTime();
        
        public void start() = startTime = System.nanoTime();
        
        public void end() = endTime = System.nanoTime();
        
        @Override
        public String toString() = clazz.getName() + "#" + name + desc + " [" + sum() + "ns]";
        
    }
    
    @Getter
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class DefaultSupplier implements Supplier<InvokeTimer> {
        
        ConcurrentHashMap<Thread, InvokeTimer> timerMap = { };
        
        @Default
        Function<Thread, InvokeTimer> timerMapper = thread -> new InvokeTimer();
        
        @Override
        public InvokeTimer get() = timerMap.computeIfAbsent(Thread.currentThread(), timerMapper);
        
    }
    
    @Getter
    ArrayDeque<Node<InvokeNode>> history = { }, context = { };
    
    @Override
    public void enter(final Class<?> clazz, final String name, final MethodType methodType, final Object... args) {
        final InvokeNode invokeNode = { clazz, name, methodType.descriptorString() };
        final @Nullable Node<InvokeNode> parent = context.peekLast();
        final Node<InvokeNode> node = parent == null ? new Node<>(invokeNode) : parent.sub(invokeNode);
        if (parent != null)
            parent.nodes() += node;
        else
            history() += node;
        context().push(node);
        invokeNode.start();
    }
    
    @Override
    public void exit() = context().pollLast()?.value().end();
    
    public Stream<Node<InvokeNode>> stream() = history().stream().flatMap(node -> LinkedForkedIterator.ofRoot(Node::parent, Node::nodes, node).forward(true));
    
    @SneakyThrows
    @Extension.Operator("GTGT")
    public void dump(final Path path, final Charset charset = StandardCharsets.UTF_8) {
        final ArrayList<String> cache = { };
        try (final OutputStream out = Files.newOutputStream(path); final BufferedWriter writer = { new OutputStreamWriter(out, charset.newEncoder()) }) {
            stream().forEach(node -> {
                final int depth = node.depth() - 1;
                if (depth > -1) {
                    if (cache.size() <= depth)
                        cache += "  ".repeat(depth);
                    writer.write(cache[depth]);
                }
                writer.write(node.value().toString());
                writer.newLine();
            });
        }
    }
    
    @SneakyThrows
    @Extension.Operator("GTGTGT")
    public void dumpSplit(final Path path, final Charset charset = StandardCharsets.UTF_8) {
        final int index[] = { 0 };
        final ArrayList<String> cache = { };
        history().forEach(root -> {
            try (final OutputStream out = Files.newOutputStream(path << String.valueOf(++index[0])); final BufferedWriter writer = { new OutputStreamWriter(out, charset.newEncoder()) }) {
                LinkedForkedIterator.ofRoot(Node::parent, Node::nodes, root).forward(true).forEach(node -> {
                    final int depth = node.depth() - 1;
                    if (depth > -1) {
                        if (cache.size() <= depth)
                            cache += "  ".repeat(depth);
                        writer.write(cache[depth]);
                    }
                    writer.write(node.value().toString());
                    writer.newLine();
                });
            }
        });
    }
    
}
