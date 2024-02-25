package amadeus.maho.lang.javac.handler.base;

import java.lang.annotation.Annotation;
import java.util.Optional;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.javac.JavacContext;

import static com.sun.tools.javac.code.Flags.SYNTHETIC;

@Getter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public abstract class BaseHandler<A extends Annotation> extends JavacContext implements Comparable<BaseHandler<A>> {
    
    Handler handler = getClass().getAnnotation(Handler.class);
    
    @Override
    public int compareTo(final BaseHandler<A> other) = Long.compare(handler().priority(), other.handler().priority());
    
    public boolean derivedFilter(final Env<AttrContext> env, final JCTree tree) = nonGenerating(tree) && noneMatch(modifiers(tree).flags, SYNTHETIC);
    
    public void process(final Env<AttrContext> env, final JCTree tree, final JCTree owner, final A annotation, final JCTree.JCAnnotation annotationTree, final boolean advance) {
        switch (tree) {
            case JCTree.JCClassDecl decl    -> processClass(env, decl, owner, annotation, annotationTree, advance);
            case JCTree.JCVariableDecl decl -> processVariable(env, decl, owner, annotation, annotationTree, advance);
            case JCTree.JCMethodDecl decl   -> processMethod(env, decl, owner, annotation, annotationTree, advance);
            default                         -> throw new AssertionError(STR."Unreachable area: \{tree.getClass()}");
        }
    }
    
    public boolean shouldProcess(final boolean advance) = !advance;
    
    public void preprocessing(final Env<AttrContext> env) { }
    
    public void processVariable(final Env<AttrContext> env, final JCTree.JCVariableDecl tree, final JCTree owner, final A annotation, final JCTree.JCAnnotation annotationTree, final boolean advance) { }
    
    public void processMethod(final Env<AttrContext> env, final JCTree.JCMethodDecl tree, final JCTree owner, final A annotation, final JCTree.JCAnnotation annotationTree, final boolean advance) { }
    
    public void processClass(final Env<AttrContext> env, final JCTree.JCClassDecl tree, final JCTree owner, final A annotation, final JCTree.JCAnnotation annotationTree, final boolean advance) { }
    
    protected long accessLevel(final AccessLevel access) = switch (access) {
        case PUBLIC    -> Flags.PUBLIC;
        case PRIVATE   -> Flags.PRIVATE;
        case PROTECTED -> Flags.PROTECTED;
        case PACKAGE   -> 0L;
    };
    
    public void injectMember(final Env<AttrContext> env, final JCTree tree, final boolean advance = false) = marker.injectMember(env, tree, advance);
    
    public void removeAnnotation(final JCTree tree, final JCTree.JCAnnotation annotationTree) = Optional.ofNullable(modifiers(tree))
            .filter(modifiers -> modifiers.annotations != null)
            .ifPresent(modifiers -> modifiers.annotations = modifiers.annotations.stream().filter(it -> it != annotationTree).collect(List.collector()));
}
