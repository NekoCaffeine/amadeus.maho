package amadeus.maho.lang.javac.handler;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.Check;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Name;

import amadeus.maho.lang.Include;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.javac.JavacContext;
import amadeus.maho.lang.javac.handler.base.DelayedContext;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;

import static amadeus.maho.lang.javac.JavacContext.instance;
import static amadeus.maho.util.bytecode.Bytecodes.IRETURN;
import static com.sun.tools.javac.code.Flags.STATIC;

@TransformProvider
public interface IncludeHandler {
    
    class StaticMethodScope extends JavacContext.MembersScope {
        
        public StaticMethodScope(final Scope scope) = super(scope, symbol -> JavacContext.anyMatch(symbol.flags(), STATIC) && symbol.name != symbol.name.table.names.init);
        
    }
    
    static Stream<Type> includeTypes(final Symbol symbol) = symbol instanceof Symbol.ClassSymbol classSymbol ? classSymbol.getAnnotationMirrors().stream()
            .filter(compound -> compound.values != null && compound.type.tsym.getQualifiedName().toString().equals(Include.class.getCanonicalName()))
            .flatMap(compound -> compound.values.stream())
            .filter(pair -> pair.fst == null || pair.fst.name.toString().equals("value"))
            .limit(1)
            .map(pair -> pair.snd)
            .flatMap(value -> value instanceof Attribute.Class clazz ? Stream.of(clazz.classType) : value instanceof Attribute.Array array ? Stream.of(array.values)
                    .filter(Attribute.Class.class::isInstance)
                    .map(Attribute.Class.class::cast)
                    .map(clazz -> clazz.classType) : Stream.empty()) : Stream.empty();
    
    private static Stream<StaticMethodScope> includeTypes(final Scope origin) = includeTypes(origin.owner)
            .map(type -> type.tsym.members())
            .map(StaticMethodScope::new);
    
    private static boolean checkContainsImportableIncludeElements(final Check $this, final Symbol.TypeSymbol symbol, final Symbol.PackageSymbol pkg, final Name name, final Set<Symbol> processed) = includeTypes(symbol)
            .map(type -> type.tsym)
            .anyMatch(context -> (Privilege) $this.checkTypeContainsImportableElement(context, context, pkg, name, processed));
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)))
    private static void importAll(final Scope.StarImportScope $this, final Types types, final Scope origin, final Scope.ImportFilter filter, final JCTree.JCImport imp,
            final BiConsumer<JCTree.JCImport, Symbol.CompletionFailure> cfHandler) {
        if (origin.owner instanceof Symbol.ClassSymbol)
            instance(DelayedContext.class).todos() += _ -> includeTypes(origin).forEach(include -> { synchronized ($this) { $this.prependSubScope(new Scope.FilterImportScope(types, include, null, filter, imp, cfHandler)); }});
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)))
    private static void importByName(final Scope.NamedImportScope $this, final Types types, final Scope origin, final Name name, final Scope.ImportFilter filter, final JCTree.JCImport imp,
            final BiConsumer<JCTree.JCImport, Symbol.CompletionFailure> cfHandler) = instance(DelayedContext.class).todos() += _ -> includeTypes(origin)
            .map(include -> new Scope.FilterImportScope(types, include, name, filter, imp, cfHandler))
            .forEach(scope -> { synchronized ($this) { (Privilege) $this.appendScope(scope, name); }});
    
    @Hook(at = @At(insn = @At.Insn(opcode = IRETURN), offset = 1, ordinal = 0), before = false)
    private static Hook.Result checkTypeContainsImportableElement(final Check $this, final Symbol.TypeSymbol symbol, final Symbol.TypeSymbol origin, final Symbol.PackageSymbol pkg, final Name name, final Set<Symbol> processed)
            = Hook.Result.falseToVoid(symbol != null && symbol == origin && checkContainsImportableIncludeElements($this, symbol, pkg, name, processed));
    
}
