package amadeus.maho.lang.javac.handler;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;

import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.javac.JavacContext;
import amadeus.maho.lang.javac.MahoJavac;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.runtime.ObjectHelper;

@TransformProvider
@NoArgsConstructor
public class ValueBasedHandler extends JavacContext {
    
    @Hook(at = @At(method = @At.MethodInsn(name = "binary")))
    private static Hook.Result visitBinary(final Attr $this, final JCTree.JCBinary tree) {
        if (!(tree instanceof EqualsAndHashCodeHandler.IdentityCompareBinary)) {
            final @Nullable Boolean eq = switch (tree.getTag()) {
                case EQ -> true;
                case NE -> false;
                default -> null;
            };
            if (eq != null) {
                final ValueBasedHandler instance = instance(ValueBasedHandler.class);
                final Type botType = instance.symtab.botType;
                if (tree.lhs.type != botType && tree.rhs.type != botType && (Privilege) $this.isValueBased(tree.lhs.type)) {
                    if (tree.lhs.type.tsym == tree.rhs.type.tsym) {
                        final TreeMaker maker = instance.maker;
                        final JCTree.JCMethodInvocation apply = maker.Apply(List.nil(), maker.Select(instance.IdentQualifiedName(ObjectHelper.class), instance.name(eq ? "valueBasedEquals" : "valueBasedNotEquals")), List.of(tree.lhs, tree.rhs));
                        throw new ReAttrException(apply, tree);
                    } else if (tree.lhs.type.tsym != instance.types.boxedClass(tree.rhs.type))
                        instance.log.error(JCDiagnostic.DiagnosticFlag.MANDATORY, tree, new JCDiagnostic.Error(MahoJavac.KEY, "value.based.compare.inconsistency", tree.lhs.type, tree.rhs.type));
                }
            }
        }
        return Hook.Result.VOID;
    }
    
}
