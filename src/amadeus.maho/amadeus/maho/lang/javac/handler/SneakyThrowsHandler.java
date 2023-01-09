package amadeus.maho.lang.javac.handler;

import java.util.ArrayList;
import java.util.stream.Stream;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.Flow;
import com.sun.tools.javac.comp.InferenceContext;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;

import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.javac.JavacContext;
import amadeus.maho.lang.javac.handler.base.HandlerMarker;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public class SneakyThrowsHandler {
    
    public static boolean inSneakyThrowsMarkDomains(final Stream<? extends JCTree> stream) = stream.map(JavacContext::symbol).nonnull().anyMatch(symbol -> JavacContext.hasAnnotation(symbol, SneakyThrows.class));
    
    // Does not check for unsafe method calls in the domains marked by @SneakyThrows.
    @Hook
    private static Hook.Result markThrown(final Flow.FlowAnalyzer $this, final JCTree tree, final Type exc)
        = Hook.Result.falseToVoid(tree instanceof JCTree.JCMethodInvocation || inSneakyThrowsMarkDomains(HandlerMarker.flowContext().stream()));
    
    // When in the domains marked by @SneakyThrows, the method reference is not thrown for type checking.
    @Hook
    private static Hook.Result checkExConstraints(final Attr $this, final List<Type> expr, final List<Type> type, final InferenceContext context)
        = Hook.Result.falseToVoid(inSneakyThrowsMarkDomains(new ArrayList<>(HandlerMarker.attrContext()).stream()));
    
}
