package amadeus.maho.lang.javac.handler;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.javac.handler.base.BaseHandler;
import amadeus.maho.lang.javac.handler.base.Handler;
import amadeus.maho.util.dynamic.LookupHelper;
import amadeus.maho.util.runtime.ToStringHelper;

import static com.sun.tools.javac.code.Flags.*;

@NoArgsConstructor
public abstract class RedirectMethodHandler<A extends Annotation> extends BaseHandler<A> {
    
    public static final int PRIORITY = ConstructorHandler.PRIORITY;
    
    @NoArgsConstructor
    @Handler(value = ToString.class, priority = PRIORITY)
    public static class ToStringHandler extends RedirectMethodHandler<ToString> {
        
        @SneakyThrows
        protected static final Method from = LookupHelper.method1(Object::toString), to = ToStringHelper.class.getDeclaredMethod("toString", Object.class);
        
        @Override
        protected Method from() = from;
        
        @Override
        protected Method to() = to;
        
    }
    
    protected abstract Method from();
    
    protected abstract Method to();
    
    @Override
    public void processClass(final Env<AttrContext> env, final JCTree.JCClassDecl tree, final JCTree owner, final A annotation, final JCTree.JCAnnotation annotationTree, final boolean advance) {
        final Method from = from(), to = to();
        final Name name = name(from.getName());
        if (shouldInjectMethod(env, name, names(from.getParameterTypes())))
            injectMember(env, maker.MethodDef(maker.Modifiers(PUBLIC), name, IdentQualifiedName(from.getReturnType()), List.nil(), params(from), List.nil(), maker.Block(0L, stats(from, to)), null)
                    .let(it -> followAnnotation(annotationTree, "on", it.mods)));
    }
    
    protected List<JCTree.JCVariableDecl> params(final Method method)
        = List.from(method.getParameters()).map(parameter -> maker.VarDef(maker.Modifiers(FINAL | PARAMETER), name(parameter.getName()), IdentQualifiedName(parameter.getType().getName()), null));
    
    protected List<JCTree.JCStatement> stats(final Method from, final Method to) = List.of(maker.Return(maker.Apply(List.nil(), maker.Select(IdentQualifiedName(to.getDeclaringClass().getCanonicalName()), name(to.getName())),
            List.<JCTree.JCExpression>of(maker.Ident(names._this)).appendList(List.from(from.getParameters()).map(parameter -> maker.Ident(name(parameter.getName())))))));
    
}
