package amadeus.maho.lang.javac.handler;

import java.util.IllegalFormatException;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.MemberEnter;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import amadeus.maho.lang.javac.handler.base.HandlerSupport;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.runtime.ObjectHelper;

@TransformProvider
public class StringHandler {
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static Type adjustMethodReturnType(final Type capture, final Attr $this, final Symbol method, final Type qualifierType, final Name methodName, final List<Type> argTypes, final Type returnType) {
        if (method != null && method.owner != null && method.owner.type == HandlerSupport.instance().symtab.stringType && method.name.toString().equals("formatted") && qualifierType.constValue() instanceof String format) {
            final Object args[] = argTypes.stream()
                    .map(Type::constValue)
                    .takeWhile(ObjectHelper::nonNull)
                    .toArray();
            if (args.length == argTypes.size())
                try {
                    return capture.constType(format.formatted(args));
                } catch (final IllegalFormatException ignored) { }
        }
        return capture;
    }
    
    @Hook
    private static Hook.Result visitTree(final MemberEnter.InitTreeVisitor $this, final JCTree tree)
            = Hook.Result.falseToVoid(tree instanceof JCTree.JCMethodInvocation invocation && TreeInfo.name(invocation.meth).toString().equals("formatted"), null);
    
}
