package amadeus.maho.util.bytecode.context;

import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.util.bytecode.ClassWriter;
import amadeus.maho.util.bytecode.ComputeType;
import amadeus.maho.util.bytecode.traverser.MethodTraverser;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TransformContext {
    
    @Getter
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class WithSource extends TransformContext {
        
        byte bytecode[];
        
        @SneakyThrows
        @Getter(lazy = true)
        String md5 = ByteBuffer.wrap(bytecode).checksum("md5");
        
    }
    
    final ClassWriter writer;
    
    @Default
    final boolean aot = false;
    
    boolean modified;
    
    final Map<MethodNode, Set<ComputeType>> markComputeTypes = new HashMap<>();
    
    public self markModified() = modified = true;
    
    public self markCompute(final ClassNode node, final ComputeType... computeTypes) = node.methods.forEach(methodNode -> markCompute(methodNode, computeTypes));
    
    public self markCompute(final MethodNode methodNode, final ComputeType... computeTypes) = markComputeTypes().computeIfAbsent(methodNode, _ -> EnumSet.noneOf(ComputeType.class)) *= List.of(computeTypes);
    
    public self compute() = markComputeTypes().let(it -> it.forEach(this::compute)).let(Map::clear);
    
    public self compute(final ClassNode node, final ComputeType... computeTypes) = node.methods.forEach(methodNode -> compute(methodNode, computeTypes));
    
    public self compute(final MethodNode methodNode, final ComputeType... computeTypes) = compute(methodNode, Set.of(computeTypes));
    
    public self compute(final MethodNode methodNode, final Set<ComputeType> computeTypes) = MethodTraverser.instance().compute(methodNode, writer(), computeTypes);
    
}
