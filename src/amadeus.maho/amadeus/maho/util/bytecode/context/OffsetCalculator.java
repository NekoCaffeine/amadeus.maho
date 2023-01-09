package amadeus.maho.util.bytecode.context;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.util.bytecode.ASMHelper;

import static org.objectweb.asm.Opcodes.ACC_STATIC;

@FieldDefaults(level = AccessLevel.PRIVATE)
public final class OffsetCalculator implements Cloneable {
    
    final List<Type> types;
    
    final boolean isStatic;
    
    final int baseOffset;
    
    int index, offset;
    
    public OffsetCalculator(final Type... types) = this(true, types);
    
    public OffsetCalculator(final boolean isStatic, final Type... types) = this(isStatic, 0, types);
    
    public OffsetCalculator(final boolean isStatic, final int baseOffset, final Type... types) {
        this.types = new ArrayList<>(List.of(types));
        this.isStatic = isStatic;
        this.baseOffset = baseOffset;
        reset();
    }
    
    public self reset() {
        index = -1;
        offset = baseOffset + (isStatic ? 0 : 1);
    }
    
    public boolean hasNext() = index < types.size() - 1;
    
    public boolean hasPrev() = index > 0;
    
    public int next() = offset(+1);
    
    public int next(final Type type) {
        types += type;
        return next();
    }
    
    public int prev() = offset(-1);
    
    public int nowIndex() = index;
    
    public int nowOffset() = offset;
    
    public self skip(final int n) = offset(n);
    
    public int offset(final int n) {
        if (index + n > types.size() || index + n < 0)
            throw new ArrayIndexOutOfBoundsException(index + n);
        if (n == 0)
            return offset;
        int lastOffset = 0;
        if (n > 0) {
            for (final int max = index + n; index < max; index++)
                offset += lastOffset = types.get(index + 1).getSize();
            return lastOffset > 1 ? offset - 1 : offset;
        } else {
            for (final int min = index + n; index > min; index--)
                offset -= types.get(index - 1).getSize();
            return offset;
        }
    }
    
    public Type nowType() = types.get(index);
    
    public int maxLength() {
        int result = isStatic ? 0 : 1;
        for (final Type type : types)
            result += type.getSize();
        return result;
    }
    
    @Override
    public OffsetCalculator clone() = { types.toArray(Type[]::new) };
    
    public static OffsetCalculator fromMethodNode(final MethodNode methodNode) = { ASMHelper.anyMatch(methodNode.access, ACC_STATIC), Type.getArgumentTypes(methodNode.desc) };
    
}
