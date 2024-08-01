package amadeus.maho.util.misc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

import amadeus.maho.core.Maho;
import amadeus.maho.core.MahoProfile;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.vm.reflection.hotspot.HotSpot;

@SneakyThrows
public interface Dumper {
    
    String head = " ".repeat(4);
    
    ConcurrentHashMap<String, BiConsumer<List<String>, String>> dumper = {
            Map.of(
                    "Maho", Maho::dump,
                    "Environment", Environment.local()::dump,
                    "HotSpot", HotSpot.instance()::dump,
                    "Profile", MahoProfile::dump
            )
    };
    
    static void dump(final Path target, final Stream<Map.Entry<String, BiConsumer<List<String>, String>>> info, final String subHead = head) throws IOException
            = info.forEach(entry -> Files.write(target / entry.getKey(), new ArrayList<String>().let(it -> entry.getValue().accept(it, subHead)), StandardCharsets.UTF_8));

    static void dump(final Path target, final BiConsumer<List<String>, String> info, final String subHead = head) throws IOException
            = Files.write(target, new ArrayList<String>().let(it -> info.accept(it, subHead)), StandardCharsets.UTF_8);
    
    static String string(final Stream<BiConsumer<List<String>, String>> info, final String subHead = head) throws IOException
            = String.join("\n", new ArrayList<String>().let(it -> info.forEach(consumer -> consumer.accept(it, subHead))));
    
    static void print(final Consumer<String> printer = System.out::println, final Stream<BiConsumer<List<String>, String>> info, final String subHead = head) throws IOException
            = printer.accept(string(info, subHead));
    
}
