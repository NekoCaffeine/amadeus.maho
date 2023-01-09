package amadeus.maho.util.bytecode.type;

import org.objectweb.asm.Type;

import static org.objectweb.asm.Type.*;

public interface IntTypeRange {
    
    static Type minType(final int value) {
        if (value == 0 || value == 1)
            return BOOLEAN_TYPE;
        if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE)
            return BYTE_TYPE;
        if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE)
            return SHORT_TYPE;
        return INT_TYPE;
    }
    
    static boolean implicitlyConvertible(final Type sourceType, final Type targetType) = switch (sourceType.getSort()) {
        case BOOLEAN -> switch (targetType.getSort()) {
            case BOOLEAN,
                 BYTE,
                 SHORT,
                 CHAR,
                 INT -> true;
            default  -> false;
        };
        case BYTE    -> switch (targetType.getSort()) {
            case BYTE,
                 SHORT,
                 CHAR,
                 INT -> true;
            default  -> false;
        };
        case SHORT,
             CHAR    -> switch (targetType.getSort()) {
            case SHORT,
                 CHAR,
                 INT -> true;
            default  -> false;
        };
        case INT     -> switch (targetType.getSort()) {
            case INT -> true;
            default  -> false;
        };
        default      -> false;
    };
    
}
