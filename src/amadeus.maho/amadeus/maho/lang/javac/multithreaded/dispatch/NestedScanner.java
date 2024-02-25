package amadeus.maho.lang.javac.multithreaded.dispatch;

import java.util.ArrayList;
import java.util.LinkedHashSet;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;

import static com.sun.tools.javac.code.TypeTag.CLASS;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NestedScanner extends TreeScanner {
    
    final Context context;
    
    final Types types = Types.instance(context);
    final Enter enter = Enter.instance(context);
    
    final Env<AttrContext> env;
    
    final JCTree untranslated = env.tree;
    
    final ArrayList<Symbol.ClassSymbol> symbols = { };
    
    final LinkedHashSet<Env<AttrContext>> dependencies = { };
    
    boolean hasLambdas, hasPatterns;
    
    @Override
    public void visitClassDef(final JCTree.JCClassDecl tree) {
        symbols += tree.sym;
        Type supertype = types.supertype(tree.sym.type);
        boolean envForSuperTypeFound = false;
        while (!envForSuperTypeFound && supertype.hasTag(CLASS)) {
            final Symbol.ClassSymbol superClassSymbol = supertype.tsym.outermostClass();
            final Env<AttrContext> superEnv = enter.getEnv(superClassSymbol);
            if (superEnv != null && env != superEnv) {
                if (dependencies.add(superEnv)) {
                    final boolean
                            prevHasLambdas = hasLambdas,
                            prevHasPatterns = hasPatterns;
                    try {
                        scan(superEnv.tree);
                    } finally {
                        hasLambdas = prevHasLambdas;
                        hasPatterns = prevHasPatterns;
                    }
                }
                envForSuperTypeFound = true;
            }
            supertype = types.supertype(supertype);
        }
        super.visitClassDef(tree);
    }
    
    @Override
    public void visitLambda(final JCTree.JCLambda tree) {
        hasLambdas = true;
        super.visitLambda(tree);
    }
    
    @Override
    public void visitReference(final JCTree.JCMemberReference tree) {
        hasLambdas = true;
        super.visitReference(tree);
    }
    
    @Override
    public void visitBindingPattern(final JCTree.JCBindingPattern tree) {
        hasPatterns = true;
        super.visitBindingPattern(tree);
    }
    
    @Override
    public void visitRecordPattern(final JCTree.JCRecordPattern that) {
        hasPatterns = true;
        super.visitRecordPattern(that);
    }
    
    @Override
    public void visitSwitch(final JCTree.JCSwitch tree) {
        hasPatterns |= tree.patternSwitch;
        super.visitSwitch(tree);
    }
    
    @Override
    public void visitSwitchExpression(final JCTree.JCSwitchExpression tree) {
        hasPatterns |= tree.patternSwitch;
        super.visitSwitchExpression(tree);
    }
    
}
