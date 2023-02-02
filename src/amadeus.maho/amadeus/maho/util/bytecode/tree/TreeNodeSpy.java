package amadeus.maho.util.bytecode.tree;

import java.lang.invoke.VarHandle;

import org.objectweb.asm.Label;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LabelNode;

import amadeus.maho.lang.Extension;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.util.runtime.MethodHandleHelper;

// This is a low-level accessor, so @Proxy is not applicable.
@Extension
public interface TreeNodeSpy {
    
    @SneakyThrows
    VarHandle
            opcodeHandle = MethodHandleHelper.lookup().findVarHandle(AbstractInsnNode.class, "opcode", int.class),
            valueHandle  = MethodHandleHelper.lookup().findVarHandle(LabelNode.class, "value", Label.class);
    
    @SneakyThrows
    static void opcode(final AbstractInsnNode node, final int opcode) = opcodeHandle.set(node, opcode);
    
    @SneakyThrows
    static void label(final LabelNode node, final Label label) = valueHandle.set(node, label);
    
    @SneakyThrows
    static Label labelGet(final LabelNode node) = (Label) valueHandle.get(node);
    
    static Label markLabel(final LabelNode $this) = $this.getLabel().let(it -> it.info = $this);
    
}
