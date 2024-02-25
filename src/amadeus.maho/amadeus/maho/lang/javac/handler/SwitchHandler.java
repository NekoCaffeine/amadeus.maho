package amadeus.maho.lang.javac.handler;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.Flow;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Log;

import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.javac.handler.base.HandlerSupport;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public class SwitchHandler {
    
    @Hook
    private static Hook.Result condType(final Attr $this, final List<JCDiagnostic.DiagnosticPosition> positions, final List<Type> condTypes)
            = Hook.Result.falseToVoid(condTypes.stream().allMatch(Type.JCVoidType.class::isInstance), HandlerSupport.instance().symtab.voidType);
    
    @Hook(at = @At(method = @At.MethodInsn(name = "scanStats")), before = false)
    private static void visitSwitchExpression(final Flow.AliveAnalyzer $this, final JCTree.JCSwitchExpression tree) {
        if ((Privilege) $this.alive == Flow.Liveness.ALIVE && tree.type instanceof Type.JCVoidType) {
            (Privilege) $this.scanSyntheticBreak((Privilege) Privilege.Outer.<Flow>access($this).make, tree);
            (Privilege) ($this.alive = Flow.Liveness.DEAD);
        }
    }
    
    @Hook
    private static Hook.Result report(final Log $this, final JCDiagnostic diagnostic) = Hook.Result.falseToVoid("compiler.err.switch.expression.no.result.expressions".equals(diagnostic.getCode()));
    
}
