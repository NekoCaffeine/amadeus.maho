package amadeus.maho.util.bytecode.tree;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;

import amadeus.maho.util.runtime.StreamHelper;

public class NodeCopier {
    
    protected final Map<LabelNode, LabelNode> labelMap = labelMap();
    
    public static Map<LabelNode, LabelNode> labelMap() = new HashMap<>() {
        
        @Override
        public LabelNode get(final Object key) {
            LabelNode result = super.get(key);
            if (result == null)
                put((LabelNode) key, result = { });
            return result;
        }
        
    };
    
    public <T extends AbstractInsnNode> T copy(final T node) {
        if (node == null)
            return null;
        return (T) node.clone(labelMap);
    }
    
    public static InsnList merge(final InsnList... lists) {
        final InsnList result = { };
        final NodeCopier copier = { };
        Stream.of(lists)
                .flatMap(StreamHelper::fromIterable)
                .map(copier::copy)
                .forEach(result::add);
        return result;
    }
    
}
