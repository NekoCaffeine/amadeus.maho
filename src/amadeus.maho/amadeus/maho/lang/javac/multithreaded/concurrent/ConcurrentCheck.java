package amadeus.maho.lang.javac.multithreaded.concurrent;

import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.Check;
import com.sun.tools.javac.resources.CompilerProperties;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Pair;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.javac.multithreaded.SharedComponent;

import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Kinds.Kind.*;
import static com.sun.tools.javac.code.Scope.LookupKind.NON_RECURSIVE;
import static com.sun.tools.javac.code.TypeTag.CLASS;

@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConcurrentCheck extends Check {
    
    public record SharedData(
            ConcurrentHashMap<Pair<Symbol.ModuleSymbol, Name>, Symbol.ClassSymbol> compiled = { },
            ConcurrentHashMap<Pair<Name, Name>, Integer> localClassNameIndexes = { }) implements SharedComponent {
        
        public static SharedData instance(final Context context) = context.get(SharedData.class) ?? new SharedData().let(it -> context.put(SharedData.class, it));
        
        public void newRound() {
            compiled.clear();
            localClassNameIndexes.clear();
        }
        
    }
    
    SharedData shared;
    
    HashSet<Symbol> checkNonCyclicContext = { }, checkNonCyclicElementsContext = { };
    
    public ConcurrentCheck(final Context context) {
        super(context);
        shared = SharedData.instance(context);
    }
    
    @Override
    public void checkNonCyclic(final JCDiagnostic.DiagnosticPosition pos, final Type type) = checkNonCyclicInternal(pos, type);
    
    private boolean checkNonCyclicInternal(final JCDiagnostic.DiagnosticPosition pos, final Type type) {
        boolean complete = true;
        final Symbol symbol = type.tsym;
        if ((symbol.flags_field & ACYCLIC) != 0)
            return true;
        if (checkNonCyclicContext[symbol])
            (Privilege) super.noteCyclic(pos, (Symbol.ClassSymbol) symbol);
        else if (!symbol.type.isErroneous())
            try {
                checkNonCyclicContext += symbol;
                if (symbol.type.hasTag(CLASS)) {
                    final Type.ClassType clazz = (Type.ClassType) symbol.type;
                    if (clazz.interfaces_field != null)
                        for (List<Type> l = clazz.interfaces_field; l.nonEmpty(); l = l.tail)
                            complete &= checkNonCyclicInternal(pos, l.head);
                    if (clazz.supertype_field != null) {
                        final @Nullable Type st = clazz.supertype_field;
                        if (st != null && st.hasTag(CLASS))
                            complete &= checkNonCyclicInternal(pos, st);
                    }
                    if (symbol.owner.kind == TYP)
                        complete &= checkNonCyclicInternal(pos, symbol.owner.type);
                }
            } finally { checkNonCyclicContext -= symbol; }
        if (complete)
            complete = (symbol.flags_field & UNATTRIBUTED) == 0 && symbol.isCompleted();
        if (complete)
            symbol.flags_field |= ACYCLIC;
        return complete;
    }
    
    @Override
    public void checkNonCyclicElements(final JCTree.JCClassDecl tree) {
        if ((tree.sym.flags_field & ANNOTATION) == 0)
            return;
        try {
            checkNonCyclicElementsContext += tree.sym;
            for (final JCTree def : tree.defs)
                if (def instanceof JCTree.JCMethodDecl methodDecl)
                    checkAnnotationResType(methodDecl.pos(), methodDecl.restype.type);
        } finally {
            checkNonCyclicElementsContext -= tree.sym;
            tree.sym.flags_field |= ACYCLIC_ANN;
        }
    }
    
    @Override
    protected void checkNonCyclicElementsInternal(final JCDiagnostic.DiagnosticPosition pos, final Symbol.TypeSymbol symbol) {
        if ((symbol.flags_field & ACYCLIC_ANN) != 0)
            return;
        if (checkNonCyclicElementsContext[symbol]) {
            ((Privilege) log).error(pos, CompilerProperties.Errors.CyclicAnnotationElement(symbol));
            return;
        }
        try {
            checkNonCyclicElementsContext += symbol;
            for (final Symbol member : symbol.members().getSymbols(NON_RECURSIVE))
                if (member instanceof Symbol.MethodSymbol methodSymbol)
                    checkAnnotationResType(pos, methodSymbol.type.getReturnType());
        } finally {
            checkNonCyclicElementsContext -= symbol;
            symbol.flags_field |= ACYCLIC_ANN;
        }
    }
    
    @Override
    public Name localClassName(final Symbol.ClassSymbol symbol) {
        final Name enclFlatname = symbol.owner.enclClass().flatname;
        final String enclFlatnameString = enclFlatname.toString();
        final Pair<Name, Name> key = { enclFlatname, symbol.name };
        synchronized (shared) {
            final Integer index = shared.localClassNameIndexes.get(key);
            for (int i = index == null ? 1 : index; ; i++) {
                final Name flatname = ((Privilege) names).fromString(enclFlatnameString + syntheticNameChar + i + symbol.name);
                if (getCompiled(symbol.packge().modle, flatname) == null) {
                    shared.localClassNameIndexes.put(key, i + 1);
                    return flatname;
                }
            }
        }
    }
    
    @Override
    public void clearLocalClassNameIndexes(final Symbol.ClassSymbol symbol) {
        if (symbol.owner != null && symbol.owner.kind != NIL)
            synchronized (shared) {
                shared.localClassNameIndexes.remove(new Pair<>(symbol.owner.enclClass().flatname, symbol.name));
            }
    }
    
    @Override
    public void newRound() = shared.newRound();
    
    @Override
    public void putCompiled(final Symbol.ClassSymbol symbol) = shared.compiled.put(Pair.of(symbol.packge().modle, symbol.flatname), symbol);
    
    @Override
    public Symbol.ClassSymbol getCompiled(final Symbol.ClassSymbol symbol) = shared.compiled.get(Pair.of(symbol.packge().modle, symbol.flatname));
    
    @Override
    public Symbol.ClassSymbol getCompiled(final Symbol.ModuleSymbol moduleSymbol, final Name flatname) = shared.compiled.get(Pair.of(moduleSymbol, flatname));
    
    @Override
    public void removeCompiled(final Symbol.ClassSymbol symbol) = shared.compiled.remove(Pair.of(symbol.packge().modle, symbol.flatname));
    
}
