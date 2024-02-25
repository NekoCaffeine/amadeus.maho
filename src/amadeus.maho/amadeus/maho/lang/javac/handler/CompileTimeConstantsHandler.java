package amadeus.maho.lang.javac.handler;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Name;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.CompileTimeConstants;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.javac.JavacContext;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.dynamic.LookupHelper;

@TransformProvider
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CompileTimeConstantsHandler extends JavacContext {
    
    long compilingTimeMillis = System.currentTimeMillis();
    
    Name CompileTimeConstantsName = name(CompileTimeConstants.class), compilingTimeMillisName = name(LookupHelper.method0(CompileTimeConstants::compilingTimeMillis).getName());
    
    public void fold(final JCTree.JCMethodInvocation invocation) {
        if ((Privilege) attr.result instanceof Type.JCPrimitiveType primitiveType && primitiveType.getTag() == TypeTag.LONG && primitiveType.constValue() == null &&
            symbol(invocation.meth) instanceof Symbol.MethodSymbol methodSymbol && methodSymbol.name == compilingTimeMillisName &&
            methodSymbol.owner instanceof Symbol.ClassSymbol classSymbol && classSymbol.fullname == CompileTimeConstantsName)
            (Privilege) (attr.result = primitiveType.constType(compilingTimeMillis));
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)))
    private static void visitApply(final Attr $this, final JCTree.JCMethodInvocation invocation) = instance(CompileTimeConstantsHandler.class).fold(invocation);
    
}
