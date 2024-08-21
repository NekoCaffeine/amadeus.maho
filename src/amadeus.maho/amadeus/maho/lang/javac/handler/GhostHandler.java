package amadeus.maho.lang.javac.handler;

import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.javac.handler.base.BaseHandler;
import amadeus.maho.lang.javac.handler.base.Handler;
import amadeus.maho.transform.GhostContext;
import amadeus.maho.util.annotation.mark.Ghost;

import static amadeus.maho.lang.javac.handler.GhostHandler.PRIORITY;

@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Handler(value = Ghost.class, ranges = Handler.Range.METHOD, priority = PRIORITY)
public class GhostHandler extends BaseHandler<Ghost> {
    
    public static final int PRIORITY = -1 << 16;
    
    Name touch = names.fromString("touch");
    
    @Override
    public void generateMethodBody(final Env<AttrContext> env, final JCTree.JCMethodDecl tree, final Ghost annotation, final JCTree.JCAnnotation annotationTree)
        = tree.body = maker.Block(0, List.of(maker.Throw(maker.Apply(List.nil(), maker.Select(IdentQualifiedName(GhostContext.class), touch), List.nil()))));
    
}
