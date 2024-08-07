package amadeus.maho.util.bytecode.context;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.function.ToIntFunction;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import amadeus.maho.core.extension.DynamicLookupHelper;
import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Extension;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.util.bytecode.ClassWriter;
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
    
    final ToIntFunction<ClassLoader> loaderIndexed;
    
    boolean modified;
    
    final Set<MethodNode> shouldComputeMethods = new HashSet<>();
    
    public boolean aot() = loaderIndexed() != DynamicLookupHelper.loaderIndexed;
    
    @Extension.Operator("GET")
    public int id(final ClassLoader loader) = loaderIndexed().applyAsInt(loader);
    
    public self markModified() = modified = true;
    
    public self markCompute(final ClassNode node) = shouldComputeMethods() *= node.methods;
    
    public self markCompute(final MethodNode methodNode) = shouldComputeMethods() += methodNode;
    
    public self compute() = shouldComputeMethods().let(it -> it.forEach(this::compute)).let(Set::clear);
    
    public self compute(final MethodNode methodNode) = MethodTraverser.instance().compute(methodNode, writer());
    
}
