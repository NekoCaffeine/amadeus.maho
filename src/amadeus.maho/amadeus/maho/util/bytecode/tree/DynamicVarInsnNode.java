package amadeus.maho.util.bytecode.tree;

import java.util.function.Predicate;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.VarInsnNode;

import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.runtime.StreamHelper;

import static amadeus.maho.util.math.MathHelper.max;

public class DynamicVarInsnNode extends VarInsnNode {
    
    public DynamicVarInsnNode(final int opcode, final int var) = super(opcode, var);
    
    public static void normalizationInsnList(final InsnList list) = normalizationInsnList(list, 0);
    
    public static void normalizationInsnList(final InsnList list, final int stackSize) {
        final int max = StreamHelper.fromIterable(list)
                .filter(VarInsnNode.class::isInstance)
                .filter(((Predicate<AbstractInsnNode>) DynamicVarInsnNode.class::isInstance).negate())
                .map(VarInsnNode.class::cast)
                .map(var -> var.var + ASMHelper.varSize(var.getOpcode()))
                .max(Integer::compareTo)
                .orElse(0);
        final int index = max(max, stackSize);
        StreamHelper.fromIterable(list)
                .cast(DynamicVarInsnNode.class)
                .forEach(var -> list.set(var, new VarInsnNode(var.getOpcode(), var.var + index)));
    }
    
    @Override
    public void accept(final MethodVisitor methodVisitor) { throw new UnsupportedOperationException(); }
    
}
