package amadeus.maho.util.bytecode.tree;

import org.objectweb.asm.Label;
import org.objectweb.asm.tree.LabelNode;

import amadeus.maho.lang.Extension;

@Extension
public interface LabelNodeHelper {
    
    static Label label(final LabelNode $this) = $this.getLabel().let(it -> it.info = $this);
    
}
