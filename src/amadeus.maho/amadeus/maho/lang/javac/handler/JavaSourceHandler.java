package amadeus.maho.lang.javac.handler;

import java.util.stream.Collectors;

import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import amadeus.maho.lang.JavaSource;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.javac.JavacContext;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.dynamic.LookupHelper;

@TransformProvider
@NoArgsConstructor
public class JavaSourceHandler extends JavacContext {
    
    Name
            Attach     = name(JavaSource.Attach.class.getName()),
            importCode = name(LookupHelper.method1(JavaSource::importCode).getName()),
            bodyCode   = name(LookupHelper.method1(JavaSource::bodyCode).getName()),
            time   = name(LookupHelper.method1(JavaSource::time).getName());
    
    @Hook
    private static void attribClassBody(final Attr $this, final Env<AttrContext> env, final Symbol.ClassSymbol c) = instance(JavaSourceHandler.class).attachSourceIfNeed(env, (JCTree.JCClassDecl) env.tree, c);
    
    public void attachSourceIfNeed(final Env<AttrContext> env, final JCTree.JCClassDecl tree, final Symbol.ClassSymbol c) {
        final @Nullable Symbol.ClassSymbol attach = symtab.getClass(mahoModule, Attach);
        if (attach != null && attach.kind != Kinds.Kind.ERR && types.isAssignable(tree.sym.type, attach.type)) {
            final JCTree.JCAnnotation annotation = makeAttachSource(env);
            tree.mods.annotations = tree.mods.annotations.append(annotation);
            c.appendAttributes(List.of(annotate.attributeAnnotation(annotation, symtab.annotationType, env)));
        }
    }
    
    public JCTree.JCAnnotation makeAttachSource(final Env<AttrContext> env) = maker.Annotation(IdentQualifiedName(JavaSource.class), List.of(
            maker.Assign(maker.Ident(importCode), maker.Literal(importCode(env))),
            maker.Assign(maker.Ident(bodyCode), maker.Literal(env.tree.toString())),
            maker.Assign(maker.Ident(time), maker.Literal(System.currentTimeMillis()))
    ));
    
    protected String importCode(final Env<AttrContext> env) = env.toplevel.getImports().stream().map(JCTree::toString).collect(Collectors.joining("\n"));
    
}
