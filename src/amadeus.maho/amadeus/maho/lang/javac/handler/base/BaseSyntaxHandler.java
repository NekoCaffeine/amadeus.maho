package amadeus.maho.lang.javac.handler.base;

import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree;

import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.javac.JavacContext;

@NoArgsConstructor
public abstract class BaseSyntaxHandler extends JavacContext {
    
    public void process(final Env<AttrContext> env, final JCTree tree, final JCTree owner, final boolean advance) { }
    
    public void preprocessing(final Env<AttrContext> env) { }
    
    public void attribTree(final JCTree tree, final Env<AttrContext> env) { }
    
    public void injectMember(final Env<AttrContext> env, final JCTree tree, final boolean advance = false) = marker.injectMember(env, tree, advance);
    
}
