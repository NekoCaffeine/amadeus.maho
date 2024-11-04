package amadeus.maho.lang.javac.incremental;

import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;

import amadeus.maho.core.Maho;
import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;

import static amadeus.maho.lang.javac.JavacContext.symbol;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class IncrementalScanner extends TreeScanner {
    
    public static final Context.Key<IncrementalScanner> incrementalScannerKey = { };
    
    public static final Set<String> systemModules = Stream.concat(ModuleFinder.ofSystem().findAll().stream().map(ModuleReference::descriptor).map(ModuleDescriptor::name), Stream.of(Maho.class.getModule().getName())).collect(Collectors.toSet());
    
    final IncrementalContext incrementalContext;
    
    Symbol.ClassSymbol owner;
    
    public boolean isSystemModule(final Symbol.ModuleSymbol symbol) = incrementalContext.systemModules().computeIfAbsent(symbol, it -> !it.isUnnamed() && systemModules[it.name.toString()]);
    
    public boolean inSystemModule(final Symbol symbol) = isSystemModule(symbol.packge().modle);
    
    public static IncrementalScanner instance(final Context context) = context.get(incrementalScannerKey) ?? new IncrementalScanner(context);
    
    public IncrementalScanner(final Context context) {
        incrementalContext = context.get(IncrementalContext.incrementalContextKey)!;
        context.put(incrementalScannerKey, this);
    }
    
    @Override
    public void visitClassDef(final JCTree.JCClassDecl tree) {
        incrementalContext.recordTimestamp(owner = tree.sym);
        incrementalContext.dependencies(owner).clear();
        super.visitClassDef(tree);
    }
    
    public void record(final JCTree tree) = switch (symbol(tree)) {
        case Symbol.ClassSymbol symbol
                when !inSystemModule(symbol) -> incrementalContext.recordDependency(owner, symbol.sourcefile == null ? symbol.packge().modle : symbol);
        case Symbol symbol
                when !inSystemModule(symbol) -> incrementalContext.recordDependency(owner, symbol instanceof Symbol.VarSymbol varSymbol && varSymbol.getConstValue() != null ? varSymbol.owner : symbol);
        case null,
             default                         -> { }
    };
    
    @Override
    public void visitIdent(final JCTree.JCIdent tree) {
        record(tree);
        super.visitIdent(tree);
    }
    
    @Override
    public void visitSelect(final JCTree.JCFieldAccess tree) {
        record(tree);
        super.visitSelect(tree);
    }
    
    @Override
    public void visitNewClass(final JCTree.JCNewClass tree) {
        record(tree);
        super.visitNewClass(tree);
    }
    
    @Override
    public void visitReference(final JCTree.JCMemberReference tree) {
        record(tree);
        super.visitReference(tree);
    }
    
}
