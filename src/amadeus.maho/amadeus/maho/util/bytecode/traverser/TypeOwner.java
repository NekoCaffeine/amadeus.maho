package amadeus.maho.util.bytecode.traverser;

import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.TypeInsnNode;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.tree.LabelNodeHelper;

@EqualsAndHashCode
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TypeOwner {
    
    private static class NULL { }
    
    private static class TOP { }
    
    public static final TypeOwner
            INTEGER       = { Type.INT_TYPE },
            LONG          = { Type.LONG_TYPE },
            FLOAT         = { Type.FLOAT_TYPE },
            DOUBLE        = { Type.DOUBLE_TYPE },
            CLASS         = { ASMHelper.TYPE_CLASS },
            METHOD_TYPE   = { ASMHelper.TYPE_METHOD_TYPE },
            METHOD_HANDLE = { ASMHelper.TYPE_METHOD_HANDLE },
            NULL          = { Type.getType(NULL.class) },
            INVALID       = { Type.getType(TOP.class) };
    
    @Getter
    final Type type;
    
    @Getter
    @Default
    final @Nullable Object flag = null;
    
    public boolean isReferenceType() = this != INVALID && switch (type().getSort()) {
        case Type.OBJECT,
             Type.ARRAY -> true;
        default         -> false;
    };
    
    public TypeOwner erase() = flag() == null ? this : of(type());
    
    @Override
    public String toString() {
        final String name = type().getInternalName();
        return flag() == Opcodes.UNINITIALIZED_THIS ? STR."\{name}:U" : flag() instanceof Label ? STR."\{name}:L" : name;
    }
    
    public static TypeOwner of(final Type type) = switch (type.getSort()) {
        case Type.BOOLEAN,
             Type.BYTE,
             Type.SHORT,
             Type.INT,
             Type.CHAR   -> INTEGER;
        case Type.LONG   -> LONG;
        case Type.FLOAT  -> FLOAT;
        case Type.DOUBLE -> DOUBLE;
        default          -> new TypeOwner(type);
    };
    
    public static TypeOwner ofNull(final Type type) = switch (type.getSort()) {
        case Type.BOOLEAN,
             Type.BYTE,
             Type.SHORT,
             Type.INT,
             Type.CHAR   -> INTEGER;
        case Type.LONG   -> LONG;
        case Type.FLOAT  -> FLOAT;
        case Type.DOUBLE -> DOUBLE;
        default          -> NULL;
    };
    
    public static TypeOwner ofThis(final Type type, final boolean initialized) = { type, initialized ? null : Opcodes.UNINITIALIZED_THIS };
    
    public static TypeOwner ofNew(final TypeInsnNode insn) = { Type.getObjectType(insn.desc), label(insn) };
    
    private static Label label(final AbstractInsnNode insn) {
        AbstractInsnNode now = insn;
        loop:
        while ((now = now.getPrevious()) != null)
            switch (now.getOpcode()) {
                case -1 -> {
                    if (now instanceof LabelNode labelNode)
                        return LabelNodeHelper.label(labelNode);
                }
                default -> { break loop; }
            }
        throw new IllegalStateException("Missing LabelNode in front of opcode NEW.");
    }
    
    public static TypeOwner ofThrowable(final @Nullable String internalName) = of(internalName == null ? ASMHelper.TYPE_THROWABLE : Type.getObjectType(internalName));
    
}
