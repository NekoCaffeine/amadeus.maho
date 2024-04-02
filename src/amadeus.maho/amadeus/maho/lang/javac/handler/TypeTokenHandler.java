package amadeus.maho.lang.javac.handler;

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.javac.JavacContext;
import amadeus.maho.lang.javac.MahoJavac;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.dynamic.LookupHelper;
import amadeus.maho.util.type.TypeToken;

@TransformProvider
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TypeTokenHandler extends JavacContext {
    
    Name
            typeTokenName           = name(TypeToken.class),
            captureName             = name(LookupHelper.<Type, TypeToken<?>>method1(TypeToken::capture).getName()),
            locateName              = name(LookupHelper.<Type, TypeToken<?>>method1(TypeToken::locate).getName()),
            runtimeTypeName         = name(LookupHelper.<String, Type>method1(TypeToken::runtimeType).getName()),
            runtimeTypeVariableName = name(LookupHelper.<String, Integer, TypeVariable<?>>method2(TypeToken::runtimeTypeVariable).getName());
    
    SignatureGenerator signatureGenerator = { types };
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)))
    private static void visitApply(final Attr $this, final JCTree.JCMethodInvocation tree) {
        if (tree.args.isEmpty()) {
            final Symbol symbol = symbol(tree.meth);
            final TypeTokenHandler instance = instance(TypeTokenHandler.class);
            if (symbol instanceof Symbol.MethodSymbol methodSymbol && methodSymbol.owner.getQualifiedName() == instance.typeTokenName)
                if (methodSymbol.name == instance.captureName) {
                    if (tree.typeargs.head != null) {
                        instance.signatureGenerator.reset().assembleSig(tree.typeargs.head.type);
                        final TreeMaker maker = instance.maker.at(tree);
                        tree.args = tree.args.prepend(maker.Apply(List.nil(), maker.Select(instance.IdentQualifiedName(TypeToken.class), instance.runtimeTypeName), List.of(maker.Literal(instance.signatureGenerator.toString()))));
                        throw new ReAttrException(tree, tree);
                    } else
                        instance.log.error(JCDiagnostic.DiagnosticFlag.RESOLVE_ERROR, tree, new JCDiagnostic.Error(MahoJavac.KEY, "type-token.missing.type-arg"));
                } else if (methodSymbol.name == instance.locateName) {
                    if (tree.typeargs.size() == 2) {
                        if (tree.typeargs[1].type instanceof com.sun.tools.javac.code.Type.ClassType classType && classType.typarams_field != null && !classType.typarams_field.isEmpty()) {
                            final Symbol.TypeSymbol typeVarSymbol = tree.typeargs.head.type.tsym;
                            int index = -1;
                            final List<com.sun.tools.javac.code.Type> typeVars = classType.typarams_field;
                            for (int i = 0; i < typeVars.size(); i++) {
                                if (typeVars[i].tsym == typeVarSymbol)
                                    if (index == -1)
                                        index = i;
                                    else
                                        index = -2;
                            }
                            if (index < 0)
                                instance.log.error(JCDiagnostic.DiagnosticFlag.RESOLVE_ERROR, tree, new JCDiagnostic.Error(MahoJavac.KEY, "type-token.unable.locate"));
                            else {
                                instance.signatureGenerator.reset().assembleSig(tree.typeargs.tail.head.type);
                                final TreeMaker maker = instance.maker.at(tree);
                                tree.args = tree.args.prepend(maker.Apply(List.nil(), maker.Select(instance.IdentQualifiedName(TypeToken.class), instance.runtimeTypeVariableName),
                                        List.of(maker.Literal(instance.signatureGenerator.toString()), maker.Literal(index))));
                                throw new ReAttrException(tree, tree);
                            }
                        } else
                            instance.log.error(JCDiagnostic.DiagnosticFlag.RESOLVE_ERROR, tree, new JCDiagnostic.Error(MahoJavac.KEY, "type-token.2nd.must-be.parameterized-type"));
                    } else
                        instance.log.error(JCDiagnostic.DiagnosticFlag.RESOLVE_ERROR, tree, new JCDiagnostic.Error(MahoJavac.KEY, "type-token.missing.type-arg"));
                }
        }
    }
    
}
