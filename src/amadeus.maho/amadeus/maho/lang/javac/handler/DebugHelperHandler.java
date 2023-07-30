package amadeus.maho.lang.javac.handler;

import java.lang.reflect.Field;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.javac.JavacContext;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.dynamic.LookupHelper;
import amadeus.maho.util.runtime.DebugHelper;

import static com.sun.tools.javac.code.Flags.FINAL;

@TransformProvider
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DebugHelperHandler extends JavacContext {
    
    Name
            CodePathPerceptionName = name(DebugHelper.CodePathPerception.class.getName()),
            codePathSetter         = name(LookupHelper.<DebugHelper.CodePathPerception, Field>methodV2(DebugHelper.CodePathPerception::codePath).getName());
    
    @Hook(at = @At(method = @At.MethodInsn(name = "initEnv")))
    private static void visitVarDef(final Attr $this, final JCTree.JCVariableDecl tree) = instance(DebugHelperHandler.class).markCodePath(tree);
    
    public void markCodePath(final JCTree.JCVariableDecl tree) {
        final Symbol.ClassSymbol symbol = symtab.enterClass(mahoModule, CodePathPerceptionName);
        try {
            symbol.complete();
        } catch (final Symbol.CompletionFailure failure) { return; }
        if (!symbol.type.isErroneous())
            if (tree.sym != null && tree.sym.owner instanceof Symbol.ClassSymbol && tree.init instanceof JCTree.JCNewClass newClass && types.isAssignable(attr.attribType(newClass.clazz, env(attr)), symbol.type)) {
                final Name let = name("$new$let");
                tree.init = maker.LetExpr(List.of(maker.VarDef(maker.Modifiers(FINAL), let, tree.vartype, tree.init),
                        maker.Exec(maker.Apply(List.nil(), maker.Select(maker.Ident(let), codePathSetter), List.of(maker.Ident(fieldConstant(tree, env(attr), tree.sym)))))), maker.Ident(let));
            }
    }
    
}
