package amadeus.maho.lang.javac.handler;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.tree.JCTree;

import amadeus.maho.lang.Privilege;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public class UnsignedIntegerHandler {
    
    @Hook
    private static void visitLiteral(final Attr $this, final JCTree.JCLiteral tree) {
        if (tree.value instanceof Number number && tree.typetag.isSubRangeOf(TypeTag.LONG) && (Privilege) ((Privilege) $this.resultInfo).pt instanceof Type.JCPrimitiveType primitiveType) {
            final long value = number.longValue();
            switch (primitiveType.getTag()) {
                case BYTE  -> {
                    if (value > 0x7FL && value <= 0xFFL) {
                        tree.value = (int) (byte) value;
                        tree.typetag = TypeTag.BYTE;
                    }
                }
                case SHORT -> {
                    if (value > 0x7FFFL && value <= 0xFFFFL) {
                        tree.value = (int) (short) value;
                        tree.typetag = TypeTag.SHORT;
                    }
                }
                case INT   -> {
                    if (value > 0x7FFFFFFFL && value <= 0xFFFFFFFFL) {
                        tree.value = (int) value;
                        tree.typetag = TypeTag.INT;
                    }
                }
            }
        }
    }
    
}
