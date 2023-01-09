package amadeus.maho.lang.javac.handler.base;

import java.util.Optional;

import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.javac.JavacContext;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;

@NoArgsConstructor
public abstract class BaseSyntaxHandler extends JavacContext {
    
    public void process(final Env<AttrContext> env, final JCTree tree, final JCTree owner, final boolean advance) { }
    
    public void preprocessing(final Env<AttrContext> env) { }
    
    public void attribTree(final JCTree tree, final Env<AttrContext> env) { }
    
    public void injectMember(final Env<AttrContext> env, final JCTree tree, final boolean advance = false) = marker.injectMember(env, tree, advance);
    
    public void removeAnnotation(final JCTree tree, final JCTree.JCAnnotation annotationTree) = Optional.ofNullable(modifiers(tree))
            .filter(modifiers -> modifiers.annotations != null)
            .ifPresent(modifiers -> modifiers.annotations = modifiers.annotations.stream().filter(it -> it != annotationTree).collect(List.collector()));
    
    public @Nullable Symbol.OperatorSymbol resolveUnary(final JCDiagnostic.DiagnosticPosition pos, final JCTree.Tag tag, final Type op) = null;
    
    public @Nullable Symbol.OperatorSymbol resolveBinary(final JCDiagnostic.DiagnosticPosition pos, final JCTree.Tag tag, final Type op1, final Type op2) = null;
    
}
