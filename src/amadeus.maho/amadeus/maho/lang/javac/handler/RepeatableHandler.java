package amadeus.maho.lang.javac.handler;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.HashSet;
import java.util.Set;

import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.javac.handler.base.BaseHandler;
import amadeus.maho.lang.javac.handler.base.Handler;

import static amadeus.maho.lang.javac.handler.RepeatableHandler.PRIORITY;
import static com.sun.tools.javac.code.Flags.*;

@NoArgsConstructor
@Handler(value = Repeatable.class, priority = PRIORITY)
public class RepeatableHandler extends BaseHandler<Repeatable> {
    
    public static final int PRIORITY = -1 << 24;
    
    @Override
    public void processClass(final Env<AttrContext> env, final JCTree.JCClassDecl tree, final JCTree owner, final Repeatable annotation, final JCTree.JCAnnotation annotationTree, final boolean advance) {
        if (annotationTree.args.isEmpty()) {
            final Name RepeatableList = name("RepeatableList");
            if (shouldInjectInnerClass(env, RepeatableList))
                injectMember(env,maker.ClassDef(maker.Modifiers(ANNOTATION | INTERFACE, tree.mods.annotations.stream().filter(it -> shouldFollowAnnotation(it.type.tsym.getQualifiedName().toString())).collect(List.collector())), RepeatableList,
                        List.nil(), null, List.nil(), List.of(maker.MethodDef(maker.Modifiers(0L), names.value, maker.TypeArray(maker.Ident(tree.name)), List.nil(), List.nil(), List.nil(), null, null))));
            annotationTree.args = annotationTree.args.append(maker.Select(maker.Select(maker.Ident(tree.name), RepeatableList), names._class));
        }
    }
    
    public static final Set<Class<? extends Annotation>> followableAnnotationTypes = new HashSet<>(List.of(Retention.class, Documented.class, Inherited.class, Target.class));
    
    public boolean shouldFollowAnnotation(final String name) = followableAnnotationTypes.stream().map(Class::getName).anyMatch(name::equals);
    
}
