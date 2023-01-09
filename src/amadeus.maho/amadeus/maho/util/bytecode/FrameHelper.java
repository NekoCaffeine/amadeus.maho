package amadeus.maho.util.bytecode;

import java.util.Iterator;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.runtime.ArrayHelper;
import amadeus.maho.vm.transform.mark.HotSpotMethodFlags;

import static amadeus.maho.vm.reflection.hotspot.KlassMethod.Flags._force_inline;
import static org.objectweb.asm.Opcodes.*;

public interface FrameHelper {
    
    @HotSpotMethodFlags(_force_inline)
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
    
    @HotSpotMethodFlags(_force_inline)
    static Object[] empty() = ArrayHelper.emptyArray(Object.class);
    
    @HotSpotMethodFlags(_force_inline)
    static Object[] array(final Object... array) = array;
    
    @HotSpotMethodFlags(_force_inline)
    static FrameNode same() = { F_SAME, 0, null, 0, null };
    
    @HotSpotMethodFlags(_force_inline)
    static FrameNode same1(final Object stack) = { F_SAME1, 0, null, 1, new Object[]{ stack } };
    
    @HotSpotMethodFlags(_force_inline)
    static FrameNode append(final Object... locals) = { F_APPEND, locals.length, locals, 0, null };
    
    @HotSpotMethodFlags(_force_inline)
    static FrameNode chop(final int size) = { F_CHOP, size, null, 0, null };
    
    @HotSpotMethodFlags(_force_inline)
    static FrameNode full(final Object locals[], final Object stack[]) = { F_FULL, locals.length, locals, stack.length, stack };
    
    @HotSpotMethodFlags(_force_inline)
    static void dropFrame(final @Nullable InsnList instructions) {
        if (instructions != null)
            for (final Iterator<AbstractInsnNode> iterator = instructions.iterator(); iterator.hasNext(); )
                if (iterator.next() instanceof FrameNode)
                    iterator.remove();
    }
    
}
