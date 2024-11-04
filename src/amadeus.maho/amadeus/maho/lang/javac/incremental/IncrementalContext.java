package amadeus.maho.lang.javac.incremental;

import java.io.ByteArrayInputStream;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.tools.JavaFileObject;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.file.Locations;
import com.sun.tools.javac.util.Context;

import amadeus.maho.core.MahoExport;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.javac.JavacContext;
import amadeus.maho.lang.javac.multithreaded.SharedComponent;
import amadeus.maho.lang.javac.multithreaded.dispatch.DispatchContext;
import amadeus.maho.util.concurrent.ConcurrentWeakIdentityHashMap;
import amadeus.maho.util.container.Indexed;
import amadeus.maho.util.runtime.DebugHelper;
import amadeus.maho.util.serialization.Deserializable;
import amadeus.maho.util.serialization.FilesRecord;
import amadeus.maho.util.serialization.Serializable;
import amadeus.maho.util.serialization.base.TrustedByteArrayOutputStream;

public record IncrementalContext(
        String jdkVersion = Runtime.version().toString(),
        String mahoVersion = MahoExport.VERSION,
        ConcurrentHashMap<String, Long> timestamps = { },
        ConcurrentHashMap<String, String> moduleVersions = { },
        ConcurrentHashMap<DependencyItem.Class, Set<DependencyItem>> dependencies = { },
        ConcurrentWeakIdentityHashMap<Symbol, List<DependencyItem>> cache = { },
        ConcurrentWeakIdentityHashMap<Symbol.ModuleSymbol, Boolean> systemModules = { }) implements SharedComponent {
    
    @SneakyThrows
    public interface Serializer {
        
        record TargetFiles(Path runtimeVersion, Path timestamps, Path moduleVersions, Path indexed, Path dependencies) implements FilesRecord {
            
            public static final TargetFiles relative = FilesRecord.of(TargetFiles.class);
            
        }
        
        List<Class<? extends DependencyItem>> dependencyItemTypes = List.of(DependencyItem.Module.class, DependencyItem.Class.class, DependencyItem.Field.class, DependencyItem.Method.class);
        
        static IncrementalContext deserialize(final Path dir) {
            final TargetFiles files = TargetFiles.relative.resolve(dir);
            try {
                final List<String> versions = Files.readAllLines(files.runtimeVersion());
                final IncrementalContext context = { versions[0], versions[1] };
                Files.lines(files.timestamps()).map(line -> line.split(":")).forEach(parts -> context.timestamps().put(parts[0], Long.parseLong(parts[1])));
                Files.lines(files.moduleVersions()).map(line -> line.split(":")).forEach(parts -> context.moduleVersions().put(parts[0], parts[1]));
                final Indexed<String> indexed = Indexed.of();
                Files.lines(files.indexed()).forEach(indexed::id);
                final byte buffer[] = Files.readAllBytes(files.dependencies());
                final Deserializable.Input.Limited input = { new ByteArrayInputStream(buffer), buffer.length };
                while (input.offset() < input.limit()) {
                    final DependencyItem.Class owner = { indexed[input.readVarInt()], indexed[input.readVarInt()] };
                    final int count = input.readVarInt();
                    final Set<DependencyItem> dependencies = context.dependencies(owner);
                    for (int i = 0; i < count; i++)
                        dependencies += switch (input.readVarInt()) {
                            case 0  -> new DependencyItem.Module(indexed[input.readVarInt()]);
                            case 1  -> new DependencyItem.Class(indexed[input.readVarInt()], indexed[input.readVarInt()]);
                            case 2  -> new DependencyItem.Field(new DependencyItem.Class(indexed[input.readVarInt()], indexed[input.readVarInt()]), indexed[input.readVarInt()], indexed[input.readVarInt()]);
                            case 3  -> new DependencyItem.Method(new DependencyItem.Class(indexed[input.readVarInt()], indexed[input.readVarInt()]), indexed[input.readVarInt()], indexed[input.readVarInt()]);
                            default -> throw new UnsupportedOperationException();
                        };
                }
                return context;
            } catch (final Throwable throwable) { DebugHelper.breakpoint(throwable); }
            return { };
        }
        
        static boolean serialize(final IncrementalContext context, final Path dir) {
            final TargetFiles files = TargetFiles.relative.resolve(dir);
            try {
                Files.write(files.runtimeVersion(), List.of(STR."""
                    \{context.jdkVersion()}
                    \{context.mahoVersion()}
                """));
                Files.write(files.timestamps(), context.timestamps().entrySet().stream().map(entry -> STR."\{entry.getKey()}:\{entry.getValue()}").toList());
                Files.write(files.moduleVersions(), context.moduleVersions().entrySet().stream().map(entry -> STR."\{entry.getKey()}:\{entry.getValue()}").toList());
                final Indexed<String> indexed = Indexed.of();
                final TrustedByteArrayOutputStream buffer = { 1024 };
                final Serializable.Output output = { buffer };
                context.dependencies().forEach((owner, dependencies) -> {
                    output.writeVarInt(indexed[owner.module()]);
                    output.writeVarInt(indexed[owner.name()]);
                    output.writeVarInt(dependencies.size());
                    dependencies.forEach(dependency -> {
                        switch (dependency) {
                            case DependencyItem.Module module -> {
                                output.writeVarInt(0);
                                output.writeVarInt(indexed[module.name()]);
                            }
                            case DependencyItem.Class clazz   -> {
                                output.writeVarInt(1);
                                output.writeVarInt(indexed[clazz.module()]);
                                output.writeVarInt(indexed[clazz.name()]);
                            }
                            case DependencyItem.Field field   -> {
                                output.writeVarInt(2);
                                output.writeVarInt(indexed[field.owner().module()]);
                                output.writeVarInt(indexed[field.owner().name()]);
                                output.writeVarInt(indexed[field.name()]);
                                output.writeVarInt(indexed[field.signature()]);
                            }
                            case DependencyItem.Method method -> {
                                output.writeVarInt(3);
                                output.writeVarInt(indexed[method.owner().module()]);
                                output.writeVarInt(indexed[method.owner().name()]);
                                output.writeVarInt(indexed[method.name()]);
                                output.writeVarInt(indexed[method.signature()]);
                            }
                            default                           -> throw new IncompatibleClassChangeError();
                        }
                    });
                });
                Files.write(files.indexed(), indexed.values());
                try (final SeekableByteChannel channel = Files.newByteChannel(files.dependencies())) {
                    channel.write(buffer.toByteBuffer());
                }
                return true;
            } catch (final Throwable throwable) { DebugHelper.breakpoint(throwable); }
            return false;
        }
        
    }
    
    public static final Context.Key<IncrementalContext> incrementalContextKey = { };
    
    public boolean compatible(final String jdkVersion = Runtime.version().toString(), final String mahoVersion = MahoExport.VERSION) = jdkVersion().equals(jdkVersion) && mahoVersion().equals(mahoVersion);
    
    public List<DependencyItem> asDependencyItems(final Symbol symbol) = cache().computeIfAbsent(symbol, it -> switch (symbol) {
        case Symbol.ClassSymbol classSymbol   -> classSymbol.sourcefile == null ?
                List.of(new DependencyItem.Module(classSymbol.packge().modle.name.toString())) :
                List.of(new DependencyItem.Class(classSymbol.packge().modle.name.toString(), symbol.flatName().toString()));
        case Symbol.VarSymbol varSymbol       -> List.of(new DependencyItem.Field(asClassDependencyItem((Symbol.ClassSymbol) varSymbol.owner), varSymbol.name.toString(), signature(varSymbol)));
        case Symbol.MethodSymbol methodSymbol -> List.of(new DependencyItem.Method(asClassDependencyItem((Symbol.ClassSymbol) methodSymbol.owner), methodSymbol.name.toString(), signature(methodSymbol)));
        default                               -> List.of();
    });
    
    public DependencyItem.Class asClassDependencyItem(final Symbol.ClassSymbol symbol) = (DependencyItem.Class) asDependencyItems(symbol).getFirst();
    
    public Set<DependencyItem> dependencies(final Symbol.ClassSymbol symbol) = dependencies(asClassDependencyItem(symbol));
    
    public Set<DependencyItem> dependencies(final DependencyItem.Class item) = dependencies().computeIfAbsent(item, _ -> ConcurrentHashMap.newKeySet());
    
    public void recordTimestamp(final Symbol.ClassSymbol symbol) = recordTimestamp(symbol.sourcefile);
    
    public void recordTimestamp(final JavaFileObject file) = timestamps().computeIfAbsent(file.getName(), _ -> file.getLastModified());
    
    public void recordDependency(final Symbol.ClassSymbol symbol, final Symbol dependency) {
        if (dependency instanceof Symbol.ModuleSymbol module && !moduleVersions().contains(module.name.toString()))
            moduleVersions()[module.name.toString()] = moduleVersion(module);
        recordDependencies(asClassDependencyItem(symbol), asDependencyItems(dependency));
    }
    
    public void recordDependencies(final DependencyItem.Class owner, final Collection<DependencyItem> dependencies) = dependencies(owner) *= dependencies;
    
    public void clear() {
        timestamps().clear();
        moduleVersions().clear();
        dependencies().clear();
        cache().clear();
    }
    
    public IncrementalGraph graph(final DispatchContext context) = { context, this };
    
    public Queue<Env<AttrContext>> queue(final DispatchContext context) = graph(context).mark();
    
    public static String signature(final Symbol symbol) = JavacContext.instance().signatureGenerator.signature(symbol.type);
    
    public static String moduleVersion(final Symbol.ModuleSymbol module) {
        try {
            return module.version?.toString() ?? String.valueOf(Files.getLastModifiedTime(((Privilege) ((Locations.LocationHandler) module.classLocation).getPaths()).iterator().next()));
        } catch (final Throwable throwable) {
            DebugHelper.breakpoint(throwable);
            return "?";
        }
    }
    
}
