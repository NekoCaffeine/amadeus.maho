package amadeus.maho.lang.javac.handler;

import java.util.stream.Stream;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Check;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;

import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.Unsupported;
import amadeus.maho.lang.javac.JavacContext;
import amadeus.maho.lang.javac.handler.base.HandlerSupport;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.runtime.ObjectHelper;
import amadeus.maho.util.tuple.Tuple2;

@TransformProvider
public class UnsupportedHandler {
    
    @Hook
    private static void checkAllDefined(final Check $this, final JCDiagnostic.DiagnosticPosition pos, final Symbol.ClassSymbol symbol) {
        final HandlerSupport instance = JavacContext.instance(HandlerSupport.class);
        final TreeMaker maker = instance.maker;
        final Env<AttrContext> env = (Privilege) instance.typeEnvs.get(symbol);
        final java.util.List<Tuple2<Unsupported, JCTree.JCAnnotation>> annotations = instance.getAnnotationsByType(env.enclClass.mods, env, Unsupported.class);
        if (!annotations.isEmpty()) {
            maker.at(annotations[0].v2.pos);
            Stream.generate(() -> instance.types.firstUnimplementedAbstract(symbol))
                    .takeWhile(ObjectHelper::nonNull)
                    .map(method -> new Symbol.MethodSymbol(method.flags() & ~Flags.ABSTRACT, method.name, instance.types.memberType(symbol.type, method), method.owner))
                    .forEach(method -> instance.injectMember(env, maker.MethodDef(method, maker.Block(0L, List.of(
                            maker.Throw(maker.NewClass(null, List.nil(), instance.IdentQualifiedName(UnsupportedOperationException.class), List.nil(), null)))))));
        }
    }
    
}
