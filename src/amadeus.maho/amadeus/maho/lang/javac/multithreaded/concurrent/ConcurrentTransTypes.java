package amadeus.maho.lang.javac.multithreaded.concurrent;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.CompileStates;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.TransTypes;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;

import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.Special;
import amadeus.maho.util.runtime.DebugHelper;

import static com.sun.tools.javac.code.Flags.TYPE_TRANSLATED;

@NoArgsConstructor
public class ConcurrentTransTypes extends TransTypes {
    
    @Override
    public void visitClassDef(final JCTree.JCClassDecl tree) = result = tree;
    
    @Override
    protected void translateClass(final Symbol.ClassSymbol c) {
        throw DebugHelper.breakpointBeforeThrow(new IllegalStateException());
    }
    
    public void translateClass(final Symbol.ClassSymbol c, final Env<AttrContext> myEnv) {
        synchronized (c) {
            if ((c.flags_field & TYPE_TRANSLATED) != 0)
                throw DebugHelper.breakpointBeforeThrow(new IllegalStateException());
            c.flags_field |= TYPE_TRANSLATED;
        }
        final CompileStates compileStates = (Privilege) this.compileStates;
        final boolean envHasCompState = compileStates.get(myEnv) != null;
        if (!envHasCompState && c.outermostClass() == c)
            throw Assert.error(STR."No info for outermost class: \{myEnv.enclClass.sym}");
        if (envHasCompState && CompileStates.CompileState.FLOW.isAfter(compileStates.get(myEnv)))
            throw Assert.error(String.format((Privilege) TransTypes.statePreviousToFlowAssertMsg, compileStates.get(myEnv), myEnv.enclClass.sym));
        final Env<AttrContext> oldEnv = (Privilege) this.env;
        try {
            (Privilege) (this.env = myEnv);
            final TreeMaker savedMake = (Privilege) this.make;
            final Type savedPt = (Privilege) this.pt;
            (Privilege) (this.make = savedMake.forToplevel(myEnv.toplevel));
            // noinspection DataFlowIssue
            (Privilege) (this.pt = null);
            try {
                final JCTree.JCClassDecl tree = (JCTree.JCClassDecl) myEnv.tree;
                tree.typarams = List.nil();
                (@Special Privilege) ((TreeTranslator) this).visitClassDef(tree);
                ((Privilege) this.make).at(tree.pos);
                final ListBuffer<JCTree> bridges = { };
                (Privilege) addBridges(tree.pos(), c, bridges);
                tree.type = ((Privilege) this.types).erasure(tree.type);
                tree.defs = bridges.toList().prependList(tree.defs);
            } finally {
                (Privilege) (this.make = savedMake);
                (Privilege) (this.pt = savedPt);
            }
        } finally { (Privilege) (this.env = oldEnv); }
    }
    
}
