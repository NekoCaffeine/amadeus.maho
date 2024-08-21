package amadeus.maho.lang.javac.handler;

import java.lang.reflect.Method;

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
import amadeus.maho.util.dynamic.LookupHelper;
import amadeus.maho.util.llm.LLM;
import amadeus.maho.util.llm.LLMApi;

import static amadeus.maho.lang.javac.handler.GetterHandler.PRIORITY;

@NoArgsConstructor
@Handler(value = LLM.class, ranges = Handler.Range.FIELD, priority = PRIORITY)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LLMHandler extends BaseHandler<LLM> {
    
    public static final int PRIORITY = -1 << 8;
    
    private static final Method invokeDefaultInstanceMethod = LookupHelper.<Method, Object[], Object>method2(LLMApi::invokeDefaultInstance);
    
    LookupHandler lookupHandler = instance(context, LookupHandler.class);
    
    Name invokeDefaultInstanceName = name(invokeDefaultInstanceMethod.getName());
    
    @Override
    public void generateMethodBody(final Env<AttrContext> env, final JCTree.JCMethodDecl tree, final LLM annotation, final JCTree.JCAnnotation annotationTree) = tree.body = maker.Block(0L, List.of(maker.Return(
            maker.TypeCast(tree.sym.getReturnType(), maker.Apply(List.nil(), maker.Select(IdentQualifiedName(LLMApi.class), invokeDefaultInstanceName), List.of(
                    maker.Ident(lookupHandler.constant(tree, env, "method", Method.class, tree.sym)), maker.NewArray(maker.Ident(symtab.objectType.tsym), List.nil(), tree.params.map(param -> maker.Ident(param.name)))
            )))
    )));
    
}
