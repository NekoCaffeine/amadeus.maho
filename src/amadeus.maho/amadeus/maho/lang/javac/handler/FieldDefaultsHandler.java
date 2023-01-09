package amadeus.maho.lang.javac.handler;

import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.javac.handler.base.BaseHandler;
import amadeus.maho.lang.javac.handler.base.Handler;

import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree;

import static amadeus.maho.lang.javac.handler.FieldDefaultsHandler.PRIORITY;
import static com.sun.tools.javac.code.Flags.*;

@NoArgsConstructor
@Handler(value = FieldDefaults.class, ranges = Handler.Range.FIELD, priority = PRIORITY)
public class FieldDefaultsHandler extends BaseHandler<FieldDefaults> {
    
    public static final int PRIORITY = -1 << 8;
    
    @Override
    public boolean shouldProcess(final boolean advance) = advance;
    
    @Override
    public void processVariable(final Env<AttrContext> env, final JCTree.JCVariableDecl tree, final JCTree owner, final FieldDefaults annotation, final JCTree.JCAnnotation annotationTree, final boolean advance) {
        if (owner instanceof JCTree.JCClassDecl && noneMatch(tree.mods.flags, STATIC)) {
            if (annotation.makeFinal())
                tree.mods.flags |= FINAL;
            if (noneMatch(tree.mods.flags, AccessibleHandler.ACCESS_MARKS))
                tree.mods.flags |= accessLevel(annotation.level());
        }
    }
    
}
