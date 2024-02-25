package amadeus.maho.util.bytecode.tree;

import org.objectweb.asm.Label;
import org.objectweb.asm.tree.LabelNode;

public interface LabelNodeHelper {
    
    static Label label(final LabelNode node) = node.getLabel().let(it -> it.info = node);
    
}
