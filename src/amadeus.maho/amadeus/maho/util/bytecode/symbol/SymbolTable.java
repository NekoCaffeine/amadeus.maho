package amadeus.maho.util.bytecode.symbol;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.ModuleNode;
import org.objectweb.asm.tree.TypeInsnNode;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.container.MapTable;
import amadeus.maho.util.resource.ResourcePath;

import static amadeus.maho.util.bytecode.Bytecodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.H_NEWINVOKESPECIAL;

@SneakyThrows
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SymbolTable {
    
    @ToString
    @EqualsAndHashCode
    public record Member(String owner, String identity) {
        
        public static Member of(final String owner, final String identity = "") = new Member(owner.replace('/', '.'), identity);
        
        public static Member ofHandle(final Handle handle) = of(handle.getOwner(), handle.getName() + handle.getDesc());
        
    }
    
    @ToString
    @EqualsAndHashCode
    public record Reference(Member member, boolean special = false) { }
    
    private static final String UNNAMED = "?";
    
    @Getter
    MapTable<String, String, Set<String>> records = MapTable.ofConcurrentHashMapTable();
    
    @Getter
    Map<String, Set<String>> parents = new ConcurrentHashMap<>();
    
    @Getter
    MapTable<String, Member, Set<Reference>> dependencies = MapTable.ofConcurrentHashMapTable();
    
    public self load(final Path root, final boolean loadDependencies = false) {
        try (final ResourcePath.ResourceTree resourceTree = ResourcePath.ResourceTree.of(root)) {
            loadTree(resourceTree, loadDependencies);
        }
    }
    
    public self loadJmodDir(final Path dir, final boolean loadDependencies = false) = Files.list(dir)
            .parallel()
            .filter(Files::isRegularFile)
            .filter(path -> path.extensionName().equalsIgnoreCase("jmod"))
            .forEach(path -> load(path, loadDependencies));
    
    public self loadImage(final Path root = Path.of(System.getProperty("java.home")), final boolean loadDependencies = false) = loadJmodDir(root / "jmods", loadDependencies);
    
    public self loadTree(final ResourcePath.ResourceTree resourceTree, final boolean loadDependencies) {
        final @Nullable ResourcePath.ClassInfo moduleInfo = resourceTree.findModuleInfo();
        final String name;
        if (moduleInfo != null) {
            final ModuleNode node = ASMHelper.newClassNode(moduleInfo.readAll()).module;
            name = node.name;
        } else
            name = UNNAMED;
        final Map<String, Set<String>> classes = records()[name];
        resourceTree.classes().parallel().forEach(info -> {
            final String className = info.className();
            final Set<String> parents = parents().computeIfAbsent(className, _ -> ConcurrentHashMap.newKeySet());
            final Set<String> members = classes.computeIfAbsent(className, _ -> ConcurrentHashMap.newKeySet());
            final ClassNode node = ASMHelper.newClassNode(info.readAll(), loadDependencies ? ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES : ClassReader.SKIP_CODE);
            if (node.superName != null)
                parents += ASMHelper.sourceName(node.superName);
            node.interfaces.stream().map(ASMHelper::sourceName).forEach(parents::add);
            node.fields.stream().map(field -> field.name + field.desc).forEach(members::add);
            node.methods.stream().map(method -> method.name + method.desc).forEach(members::add);
            if (loadDependencies) {
                node.methods.forEach(method -> {
                    if (method.instructions != null) {
                        final Set<Reference> memberSet = dependencies[name].computeIfAbsent(new Member(className, method.name + method.desc), _ -> ConcurrentHashMap.newKeySet());
                        method.instructions.forEach(insn -> {
                            switch (insn) {
                                case TypeInsnNode typeInsn                   -> memberSet += new Reference(Member.of(typeInsn.desc));
                                case FieldInsnNode fieldInsn                 -> memberSet += new Reference(Member.of(fieldInsn.owner, fieldInsn.name + fieldInsn.desc));
                                case MethodInsnNode methodInsn               -> memberSet += new Reference(Member.of(methodInsn.owner, methodInsn.name + methodInsn.desc),
                                        methodInsn.getOpcode() == INVOKESPECIAL && methodInsn.name.equals(ASMHelper._INIT_) || methodInsn.name.equals(ASMHelper._CLINIT_));
                                case InvokeDynamicInsnNode invokeDynamicInsn -> {
                                    memberSet += new Reference(Member.ofHandle(invokeDynamicInsn.bsm), invokeDynamicInsn.bsm.getTag() == H_NEWINVOKESPECIAL);
                                    Stream.of(invokeDynamicInsn.bsmArgs).forEach(arg -> {
                                        if (arg instanceof Handle handle)
                                            memberSet += new Reference(Member.ofHandle(handle), handle.getTag() == H_NEWINVOKESPECIAL);
                                    });
                                }
                                default                                      -> { }
                            }
                            
                        });
                    }
                });
            }
        });
    }
    
    public void dump(final List<String> list, final String subHead) {
        records().backingMap().forEach((module, classes) -> {
            list += module;
            classes.forEach((name, members) -> {
                list += subHead + name;
                members.forEach(member -> list += subHead.repeat(2) + member);
            });
        });
    }
    
    public Map<String, Set<String>> mergeClasses() {
        final Map<String, Set<String>> merged = new HashMap<>();
        records().backingMap().forEach((module, classes) -> merged *= classes);
        return Map.copyOf(merged);
    }
    
    private boolean missing(final Function<String, Set<String>> membersFunction, final Function<String, Set<String>> parentsFunction, final Reference reference, final Set<String> visited = new HashSet<>())
        = missing(membersFunction, parentsFunction, reference.member().owner(), reference.member().identity(), reference.special(), visited);
    
    private static boolean missing(final Function<String, Set<String>> membersFunction, final Function<String, Set<String>> parentsFunction, final String owner, final String identity, final boolean special,
            final Set<String> visited = new HashSet<>()) {
        if (visited.add(owner)) {
            final @Nullable Set<String> members = membersFunction[owner];
            if (members == null)
                return true;
            if (identity.isEmpty())
                return false;
            if (members.contains(identity))
                return false;
            if (special)
                return true;
            final @Nullable Set<String> parents = parentsFunction[owner];
            if (parents == null)
                return true;
            return parents.stream().allMatch(parent -> missing(membersFunction, parentsFunction, parent, identity, false, visited));
        }
        return false;
    }
    
    public MapTable<String, Member, Set<Member>> missing(final SymbolTable other) {
        final MapTable<String, Member, Set<Member>> result = MapTable.ofConcurrentHashMapTable();
        final Map<String, Set<String>> merged = mergeClasses(), mergedOther = other.mergeClasses();
        final ConcurrentHashMap<Reference, Boolean> missingCache = new ConcurrentHashMap<>(), missingOtherCache = new ConcurrentHashMap<>();
        final Predicate<Reference>
                missingPredicate = reference -> missingCache.computeIfAbsent(reference, ref -> missing(merged::get, parents()::get, ref)),
                missingOtherPredicate = reference -> missingOtherCache.computeIfAbsent(reference, ref -> other.missing(owner -> mergedOther[owner] ?? merged[owner], owner -> other.parents()[owner] ?? parents()[owner], ref));
        other.dependencies().forEachParallel((module, method, dependencies) -> dependencies.forEach(dependency -> {
            if (!dependency.member().owner().startsWith("["))
                if (missingPredicate.test(dependency) && missingOtherPredicate.test(dependency)) {
                    final Set<Member> missingMembers = result[module].computeIfAbsent(method, _ -> new HashSet<>());
                    if (!dependency.member().identity().isEmpty())
                        missingMembers += dependency.member();
                }
        }));
        return result;
    }
    
}
