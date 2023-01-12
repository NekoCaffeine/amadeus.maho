package amadeus.maho.lang.javac.handler;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

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
public class DebugHelperHandler extends JavacContext {
    
    Name
            CodePathPerceptionName = name(DebugHelper.CodePathPerception.class.getName()),
            codePathSetter = name(LookupHelper.<DebugHelper.CodePathPerception, DebugHelper.CodePath>methodV2(DebugHelper.CodePathPerception::codePath).getName());
    
    @Hook(at = @At(method = @At.MethodInsn(name = "initEnv")))
    private static void visitVarDef(final Attr $this, final JCTree.JCVariableDecl tree) = instance(DebugHelperHandler.class).markJumpTarget(tree);
    
    public void markJumpTarget(final JCTree.JCVariableDecl tree) {
        if (tree.sym != null && tree.sym.owner instanceof Symbol.ClassSymbol && tree.init instanceof JCTree.JCNewClass newClass &&
                types.isAssignable(attr.attribType(newClass.clazz, env(attr)), symtab.enterClass(mahoModule, CodePathPerceptionName).type)) {
            final JCTree.JCNewClass codePath = maker.at(newClass.pos).NewClass(null, List.nil(), IdentQualifiedName(DebugHelper.CodePath.class), List.of(
                    maker.ClassLiteral(tree.sym.owner.type),
                    maker.Literal(tree.sym.name.toString())
            ), null);
            final Name let = name("$new$let");
            tree.init = maker.LetExpr(List.of(maker.VarDef(maker.Modifiers(FINAL), let, tree.vartype, tree.init), maker.Exec(maker.Apply(List.nil(), maker.Select(maker.Ident(let), codePathSetter), List.of(codePath)))), maker.Ident(let));
        }
    }
    
}
