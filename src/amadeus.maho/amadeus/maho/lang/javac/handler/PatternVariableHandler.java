package amadeus.maho.lang.javac.handler;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.tree.JCTree;

import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public class PatternVariableHandler {
    
    @Hook
    private static void visitBindingPattern(final Attr $this, final JCTree.JCBindingPattern tree) = tree.var.mods.flags |= Flags.FINAL;
    
}
