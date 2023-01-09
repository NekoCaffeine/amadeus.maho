package amadeus.maho.lang.javac.handler;

import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Check;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.jvm.Gen;
import com.sun.tools.javac.tree.JCTree;

import static amadeus.maho.lang.javac.JavacContext.anyMatch;
import static com.sun.tools.javac.code.Flags.STATIC;

@TransformProvider
public class StaticMethodHandler {
    
    // Makes it possible to declare static methods in subclasses that have consistent signatures but no return type inheritance.
    @Hook
    private static Hook.Result checkOverride(final Check $this, final Env<AttrContext> env, final JCTree.JCMethodDecl tree, final Symbol.MethodSymbol sym) = Hook.Result.falseToVoid(anyMatch(tree.mods.flags, STATIC), null);
    
    // Make INVOKESTATIC generate accurate target class.
    @Hook
    private static Hook.Result binaryQualifier(final Gen $this, final Symbol symbol, final Type site) = !site.hasTag(TypeTag.ARRAY) && anyMatch(symbol.flags(), STATIC) ? new Hook.Result(symbol) : Hook.Result.VOID;
    
}
