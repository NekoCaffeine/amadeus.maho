package amadeus.maho.lang.javac.handler;

import amadeus.maho.lang.Extension;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.javac.handler.base.BaseHandler;
import amadeus.maho.lang.javac.handler.base.Handler;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import static amadeus.maho.lang.javac.handler.ExtensionOperatorHandler.PRIORITY;
import static com.sun.tools.javac.code.Flags.*;

@NoArgsConstructor
@Handler(value = Extension.Operator.class, priority = PRIORITY)
public class ExtensionOperatorHandler extends BaseHandler<Extension.Operator> {
    
    public static final int PRIORITY = ReferenceHandler.PRIORITY >> 2;
    
    @Override
    public void processMethod(final Env<AttrContext> env, final JCTree.JCMethodDecl tree, final JCTree owner, final Extension.Operator annotation, final JCTree.JCAnnotation annotationTree, final boolean advance) {
        if (!(owner instanceof JCTree.JCClassDecl))
            return;
        final String value = OperatorData.operatorSymbol2operatorName.getOrDefault(annotation.value(), annotation.value());
        final Name name = name(value);
        if (shouldInjectMethod(env, name, names(tree.params, env))) {
            final boolean def = noneMatch(tree.mods.flags, STATIC) && anyMatch(modifiers(owner).flags, INTERFACE);
            if (def)
                modifiers(owner).flags |= DEFAULT;
            injectMember(env, maker.MethodDef(maker.Modifiers(tree.mods.flags & ~(ABSTRACT | NATIVE | SYNCHRONIZED) | (def ? DEFAULT : 0),
                    tree.mods.annotations), name, tree.restype, tree.typarams, tree.params, tree.thrown, maker.Block(0L, generateBody(tree)), null));
        }
    }
    
    protected List<JCTree.JCStatement> generateBody(final JCTree.JCMethodDecl decl) {
        final JCTree.JCExpression expr = maker.Apply(List.nil(), maker.Ident(decl.name), decl.params.map(param -> maker.Ident(param.name)));
        return List.of(decl.sym.getReturnType() instanceof Type.JCVoidType ? maker.Exec(expr) : maker.Return(expr));
    }
    
}
