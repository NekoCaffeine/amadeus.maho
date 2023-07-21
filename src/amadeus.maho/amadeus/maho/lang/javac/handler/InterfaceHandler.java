package amadeus.maho.lang.javac.handler;

import com.sun.tools.javac.parser.JavacParser;
import com.sun.tools.javac.parser.Tokens;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;

import static com.sun.tools.javac.code.Flags.STATIC;

@TransformProvider
public class InterfaceHandler {
    
    @Hook
    private static Hook.Result variableDeclaratorRest(final JavacParser $this, final int pos, final JCTree.JCModifiers mods, final JCTree.JCExpression type, final Name name,
            @Hook.Reference boolean reqInit, final Tokens.Comment dc, final boolean localDecl, final boolean compound) {
        reqInit = false;
        return { };
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static void classOrInterfaceOrRecordBodyDeclaration(final List<JCTree> capture, final JavacParser $this, final Name className, final boolean isInterface, final boolean isRecord) {
        if (isInterface)
            capture.stream().cast(JCTree.JCBlock.class).forEach(block -> block.flags |= STATIC);
    }
    
}
