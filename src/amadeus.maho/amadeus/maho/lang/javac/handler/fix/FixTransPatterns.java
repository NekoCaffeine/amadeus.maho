package amadeus.maho.lang.javac.handler.fix;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.comp.TransPatterns;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeTranslator;

import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.Special;
import amadeus.maho.lang.inspection.Fixed;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public interface FixTransPatterns {
    
    @Fixed(domain = "openjdk", shortName = "JDK-8278834", url = "https://bugs.openjdk.org/browse/JDK-8278834")
    @Hook
    private static Hook.Result visitClassDef(final TransPatterns $this, final JCTree.JCClassDecl tree) {
        final Symbol.ClassSymbol prevCurrentClass = (Privilege) $this.currentClass;
        final Symbol.MethodSymbol prevMethodSym = (Privilege) $this.currentMethodSym;
        try {
            (Privilege) ($this.currentClass = tree.sym);
            (Privilege) ($this.currentMethodSym = null);
            (@Special Privilege) ((TreeTranslator) $this).visitClassDef(tree);
        } finally {
            (Privilege) ($this.currentClass = prevCurrentClass);
            (Privilege) ($this.currentMethodSym = prevMethodSym);
        }
        return Hook.Result.NULL;
    }
    
}
