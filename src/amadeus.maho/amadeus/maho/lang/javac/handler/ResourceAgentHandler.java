package amadeus.maho.lang.javac.handler;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.JCDiagnostic;

import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.ResourceAgent;
import amadeus.maho.lang.javac.MahoJavac;
import amadeus.maho.lang.javac.handler.base.BaseHandler;
import amadeus.maho.lang.javac.handler.base.Handler;


@NoArgsConstructor
@Handler(ResourceAgent.class)
public class ResourceAgentHandler extends BaseHandler<ResourceAgent> {
    
    @Override
    public void processMethod(final Env<AttrContext> env, final JCTree.JCMethodDecl tree, final JCTree owner, final ResourceAgent annotation, final JCTree.JCAnnotation annotationTree, final boolean advance) {
        if (!valid(tree.sym))
            log.error(JCDiagnostic.DiagnosticFlag.RESOLVE_ERROR, annotationTree, new JCDiagnostic.Error(MahoJavac.KEY, "resource.agent.invalid.parameter"));
        try {
            final Pattern pattern = Pattern.compile(annotation.value());
            final Map<String, Integer> namedGroupsIndex = pattern.namedGroups();
            final List<String> missingKey = tree.sym.params().stream().map(symbol -> symbol.name.toString()).filterNot(namedGroupsIndex.keySet()::contains).toList();
            if (!missingKey.isEmpty())
                log.error(JCDiagnostic.DiagnosticFlag.RESOLVE_ERROR, annotationTree, new JCDiagnostic.Error(MahoJavac.KEY, "resource.agent.missing.key", missingKey));
        } catch (final PatternSyntaxException ignored) { }
    }
    
    public boolean valid(final Symbol.MethodSymbol methodSymbol) {
        final Symbol.TypeSymbol stringSymbol = symtab.stringType.tsym;
        return methodSymbol.params().stream().map(it -> it.type.tsym).allMatch(sym -> sym == stringSymbol);
    }
    
}
