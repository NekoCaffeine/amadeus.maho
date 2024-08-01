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

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.runtime.ObjectHelper;

import static amadeus.maho.lang.javac.JavacContext.*;
import static amadeus.maho.util.bytecode.Bytecodes.ALOAD;
import static java.lang.StringTemplate.*;

@TransformProvider
public interface StringHandler {
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static Type adjustMethodReturnType(final Type capture, final Attr $this, final @Nullable Symbol method, final Type qualifierType, final Name methodName, final List<Type> argTypes, final Type returnType) {
        if (method != null && method.owner != null && method.owner.type == instance().symtab.stringType && method.name.toString().equals("formatted") && qualifierType.constValue() instanceof String format) {
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
    
    @Hook(at = @At(var = @At.VarInsn(opcode = ALOAD, var = 3), ordinal = 1))
    private static Hook.Result visitStringTemplate(final Attr $this, final JCTree.JCStringTemplate tree, @Hook.LocalVar(index = 3) @Hook.Reference Type resultType) {
        final java.util.List<Object> constArgs = tree.expressions.stream().map(expression -> expression.type?.constValue() ?? null).toList();
        if (!constArgs[null] && symbol(tree.processor) instanceof Symbol.VarSymbol symbol && symbol.owner.type.tsym == instance().symtab.stringTemplateType.tsym && symbol.name == symbol.name.table.names.STR) {
            resultType = resultType.constType(STR.process(of(tree.fragments, constArgs)));
            return { };
        }
        return Hook.Result.VOID;
    }
    
}
