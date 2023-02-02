package amadeus.maho.util.bytecode;

import java.util.Iterator;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.runtime.ArrayHelper;

import static org.objectweb.asm.Opcodes.*;

public interface FrameHelper {
    
    static Object info(final Type type) = switch (type.getSort()) {
        case Type.VOID,
             Type.METHOD -> throw new IllegalArgumentException(type.getDescriptor());
        case Type.INT,
             Type.BOOLEAN,
             Type.BYTE,
             Type.SHORT,
             Type.CHAR   -> INTEGER;
        case Type.FLOAT  -> FLOAT;
        case Type.DOUBLE -> DOUBLE;
        case Type.LONG   -> LONG;
        default          -> type.getInternalName();
    };
    
    static Object[] empty() = ArrayHelper.emptyArray(Object.class);
    
    static Object[] array(final Object... array) = array;
    
    static FrameNode same() = { F_SAME, 0, null, 0, null };
    
    static FrameNode same1(final Object stack) = { F_SAME1, 0, null, 1, new Object[]{ stack } };
    
    static FrameNode append(final Object... locals) = { F_APPEND, locals.length, locals, 0, null };
    
    static FrameNode chop(final int size) = { F_CHOP, size, null, 0, null };
    
    static FrameNode full(final Object locals[], final Object stack[]) = { F_FULL, locals.length, locals, stack.length, stack };
    
    static void dropFrame(final @Nullable InsnList instructions) {
        if (instructions != null)
            for (final Iterator<AbstractInsnNode> iterator = instructions.iterator(); iterator.hasNext(); )
                if (iterator.next() instanceof FrameNode)
                    iterator.remove();
    }
    
}
