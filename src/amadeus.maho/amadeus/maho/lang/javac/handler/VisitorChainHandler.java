package amadeus.maho.lang.javac.handler;

import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.VisitorChain;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.javac.handler.base.BaseHandler;
import amadeus.maho.lang.javac.handler.base.Handler;

import static amadeus.maho.lang.javac.handler.VisitorChainHandler.PRIORITY;
import static com.sun.tools.javac.code.Flags.*;

@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Handler(value = VisitorChain.class, priority = PRIORITY)
public class VisitorChainHandler extends BaseHandler<VisitorChain> {
    
    public static final int PRIORITY = -1 << 10;
    
    Name visitor = name("visitor");
    
    @Override
    public void processClass(final Env<AttrContext> env, final JCTree.JCClassDecl tree, final JCTree owner, final VisitorChain annotation, final JCTree.JCAnnotation annotationTree, final boolean advance) {
        if (shouldInjectVariable(env, visitor)) {
            final JCTree.JCModifiers modifiers = maker.Modifiers(PROTECTED | FINAL, List.of(maker.Annotation(IdentQualifiedName(Default.class), List.nil()), maker.Annotation(IdentQualifiedName(Nullable.class), List.nil())));
            final JCTree.JCVariableDecl visitorVar = maker.VarDef(modifiers, visitor, maker.Type(tree.sym.type), maker.Literal(TypeTag.BOT, null));
            injectMember(env, visitorVar);
            tree.defs.stream()
                    .cast(JCTree.JCMethodDecl.class)
                    .filter(method -> method.body == null && noneMatch(method.mods.flags, ABSTRACT))
                    .forEach(method -> method.body = maker.Block(0L, List.of(maker.If(maker.Binary(JCTree.Tag.NE, maker.Ident(visitor), maker.Literal(TypeTag.BOT, null)),
                            maker.Exec(maker.Apply(List.nil(), maker.Ident(method.name), method.params.stream().map(parameter -> maker.Ident(parameter.name)).collect(List.collector()))), null))));
        }
    }
    
}
