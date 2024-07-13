package amadeus.maho.lang.javac.handler;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.Attr;

import amadeus.maho.lang.Privilege;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.Redirect;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.Slice;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public class TypeHandler {
    
    @Hook(forceReturn = true)
    private static boolean isReifiable(final Types $this, final Type t) = true;
    
    @Redirect(targetClass = Attr.class, slice = @Slice(@At(method = @At.MethodInsn(name = "isReifiable"))))
    private static boolean visitTypeTest(final Types $this, final Type t) = ((Privilege) $this.isReifiable).visit(t);
    
}
