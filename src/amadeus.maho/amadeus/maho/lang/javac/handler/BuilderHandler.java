package amadeus.maho.lang.javac.handler;

import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;

import amadeus.maho.lang.Builder;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.javac.handler.base.BaseHandler;
import amadeus.maho.lang.javac.handler.base.Handler;

import static amadeus.maho.lang.javac.handler.BuilderHandler.PRIORITY;
import static com.sun.tools.javac.code.Flags.*;

@NoArgsConstructor
@Handler(value = Builder.class, priority = PRIORITY)
public class BuilderHandler extends BaseHandler<Builder> {
    
    public static final int PRIORITY = ConstructorHandler.PRIORITY << 2;
    
    @Override
    public void processMethod(final Env<AttrContext> env, final JCTree.JCMethodDecl tree, final JCTree owner, final Builder annotation, final JCTree.JCAnnotation annotationTree, final boolean advance) {
        if (!(owner instanceof JCTree.JCClassDecl) || !names.init.equals(tree.name))
            return;
        final Name InnerBuilder = name("InnerBuilder");
        final boolean empty = env.enclClass.typarams.isEmpty();
        if (shouldInjectInnerClass(env, InnerBuilder)) {
            final Name $value = name("$value");
            final ListBuffer<JCTree> defines = { };
            defines.append(maker.MethodDef(maker.Modifiers(PUBLIC).let(it -> followAnnotation(env, tree.mods, it)), name("build"), empty ? maker.Ident(env.enclClass.name) :
                    maker.TypeApply(maker.Ident(env.enclClass.name), env.enclClass.typarams.map(parameter -> maker.Ident(parameter.name))), List.nil(), List.nil(), tree.thrown, maker.Block(0L, List.of(maker.Return(maker.NewClass(null, List.nil(),
                    empty ? maker.Ident(env.enclClass.name) : maker.TypeApply(maker.Ident(env.enclClass.name), List.nil()), tree.params.stream()
                            .map(param -> maker.VarDef(maker.Modifiers(PRIVATE), param.name, maker.Type(param.sym.type), null))
                            .peek(defines::append)
                            .map(var -> maker.MethodDef(maker.Modifiers(PUBLIC), var.name, empty ? maker.Ident(InnerBuilder) : maker.TypeApply(maker.Ident(InnerBuilder),
                                                    env.enclClass.typarams.map(parameter -> maker.Ident(parameter.name))), List.nil(), List.of(maker.VarDef(maker.Modifiers(FINAL | PARAMETER), var.name.append($value), var.vartype, null)),
                                            List.nil(), maker.Block(0L, List.of(maker.Exec(maker.Assign(maker.Ident(var.name), maker.Ident(var.name.append($value)))), maker.Return(maker.Ident(names._this)))), null)
                                    .let(it -> followAnnotation(env, var.mods, it.mods)))
                            .peek(defines::append)
                            .map(with -> with.name)
                            .map(maker::Ident)
                            .collect(List.collector()), null)))), null));
            injectMember(env, maker.ClassDef(maker.Modifiers(PUBLIC | STATIC), InnerBuilder, env.enclClass.typarams, null, List.nil(), defines.toList()).let(it -> followAnnotation(annotationTree, "on", it.mods)));
        }
        final Name builder = name("builder");
        if (shouldInjectMethod(env, builder))
            injectMember(env, maker.MethodDef(maker.Modifiers(PUBLIC | STATIC), builder, empty ? maker.Ident(InnerBuilder) : maker.TypeApply(maker.Ident(InnerBuilder),
                                    env.enclClass.typarams.map(parameter -> maker.Ident(parameter.name))), env.enclClass.typarams.map(typeParameter -> maker.TypeParameter(typeParameter.name, typeParameter.bounds, typeParameter.annotations)),
                            List.nil(), List.nil(), maker.Block(0L, List.of(maker.Return(maker.NewClass(null, List.nil(), empty ? maker.Ident(InnerBuilder) : maker.TypeApply(maker.Ident(InnerBuilder), List.nil()), List.nil(), null)))), null)
                    .let(it -> followAnnotation(env, tree.mods, it.mods)));
    }
    
}
