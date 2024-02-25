package amadeus.maho.lang.javac.handler;

import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.javac.MahoJavac;
import amadeus.maho.lang.javac.handler.base.BaseHandler;
import amadeus.maho.lang.javac.handler.base.Handler;

import static amadeus.maho.lang.javac.handler.SetterHandler.PRIORITY;
import static com.sun.tools.javac.code.Flags.*;

@NoArgsConstructor
@Handler(value = Setter.class, ranges = Handler.Range.FIELD, priority = PRIORITY)
public class SetterHandler extends BaseHandler<Setter> {
    
    public static final int PRIORITY = GetterHandler.PRIORITY << 2;
    
    @Override
    public void processVariable(final Env<AttrContext> env, final JCTree.JCVariableDecl tree, final JCTree owner, final Setter annotation, final JCTree.JCAnnotation annotationTree, final boolean advance) {
        if (owner instanceof JCTree.JCClassDecl) {
            final JCTree.JCExpression unpackedType = ReferenceHandler.unpackedType(tree.vartype, this);
            if ((noneMatch(tree.mods.flags, FINAL) || unpackedType != tree.vartype) && shouldInjectMethod(env, tree.name, unpackedType.type.tsym.getQualifiedName())) {
                final Name name = tree.name.append(name("$value"));
                injectMember(env, maker.MethodDef(maker.Modifiers(accessLevel(annotation.value()) | tree.mods.flags & STATIC | (anyMatch(env.enclClass.mods.flags, INTERFACE) ? STATIC : 0)), tree.name,
                                maker.TypeIdent(TypeTag.VOID), List.nil(), List.of(maker.VarDef(maker.Modifiers(FINAL | PARAMETER).let(it -> followAnnotation(env, tree.mods, it)), name, unpackedType, null)),
                                List.nil(), maker.Block(0L, generateBody(env, tree, name, unpackedType != tree.vartype)), null)
                        .let(it -> followAnnotation(annotationTree, "on", it.mods)).let(it -> followAnnotationWithoutNullable(env, tree.mods, it.mods)));
            }
        }
    }
    
    protected List<JCTree.JCStatement> generateBody(final Env<AttrContext> env, final JCTree.JCVariableDecl tree, final Name name, final boolean isReference)
            = List.of(maker.Exec(isReference ? marker.lookupAnnotation(tree.mods, env, tree, Getter.class)?.lazy() ?? false ?
            maker.Apply(List.nil(), maker.Select(maker.Apply(List.nil(), maker.Ident(name(tree.name + GetterHandler.REFERENCE_GETTER)), List.nil()), name("set")), List.of(maker.Ident(name))) :
            maker.Apply(List.nil(), maker.Select(maker.Ident(tree.name), name("set")), List.of(maker.Ident(name))) : maker.Assign(maker.Ident(tree.name), maker.Ident(name))));
    
    @Override
    public void processMethod(final Env<AttrContext> env, final JCTree.JCMethodDecl tree, final JCTree owner, final Setter annotation, final JCTree.JCAnnotation annotationTree, final boolean advance) {
        boolean error = false;
        if (error |= !tree.params.isEmpty())
            log.error(JCDiagnostic.DiagnosticFlag.RESOLVE_ERROR, tree, new JCDiagnostic.Error(MahoJavac.KEY, "accessor.method.has-parameter", "@Setter"));
        if (error |= owner instanceof JCTree.JCClassDecl && noneMatch(((JCTree.JCClassDecl) owner).mods.flags, INTERFACE))
            log.error(JCDiagnostic.DiagnosticFlag.RESOLVE_ERROR, tree, new JCDiagnostic.Error(MahoJavac.KEY, "accessor.method.non-interface", "@Setter"));
        if (error |= anyMatch(tree.mods.flags, STATIC))
            log.error(JCDiagnostic.DiagnosticFlag.RESOLVE_ERROR, tree, new JCDiagnostic.Error(MahoJavac.KEY, "accessor.method.static", "@Setter"));
        if (!error) {
            if (shouldInjectMethod(env, tree.name, tree.restype.type.tsym.getQualifiedName()))
                injectMember(env, maker.MethodDef(maker.Modifiers(0L), tree.name, maker.TypeIdent(TypeTag.VOID), tree.typarams, List.of(maker.VarDef(maker.Modifiers(0L), names.value, tree.restype, null)), List.nil(), null, null)
                        .let(it -> followAnnotationWithoutNullable(env, tree.mods, it.mods)));
        }
    }
    
}
