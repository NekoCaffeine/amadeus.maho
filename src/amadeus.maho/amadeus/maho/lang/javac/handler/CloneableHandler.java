package amadeus.maho.lang.javac.handler;

import java.util.Iterator;
import java.util.stream.Stream;

import amadeus.maho.lang.Cloneable;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.javac.handler.base.BaseHandler;
import amadeus.maho.lang.javac.handler.base.Handler;

import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import static amadeus.maho.lang.javac.handler.CloneableHandler.PRIORITY;
import static com.sun.tools.javac.code.Flags.*;

@NoArgsConstructor
@Handler(value = Cloneable.class, priority = PRIORITY)
public class CloneableHandler extends BaseHandler<Cloneable> {
    
    public static final int PRIORITY = ConstructorHandler.PRIORITY << 1;
    
    @Override
    public void processClass(final Env<AttrContext> env, final JCTree.JCClassDecl tree, final JCTree owner, final Cloneable annotation, final JCTree.JCAnnotation annotationTree, final boolean advance) {
        if (tree.implementing.stream().noneMatch(expression -> expression.type.tsym == symtab.cloneableType.tsym))
            tree.implementing = tree.implementing.append(maker.Type(symtab.cloneableType));
        final JCTree.JCExpression type = maker.Type(tree.sym.type);
        final Name source = name("source"), set = name("set"), swap = name("swap");
        if (shouldInjectMethod(env, names.init))
            injectMember(env, maker.MethodDef(maker.Modifiers(PUBLIC), names.init, maker.TypeIdent(TypeTag.VOID), List.nil(), List.nil(), List.nil(), maker.Block(0L, List.nil()), null));
        if (shouldInjectMethod(env, names.init, tree.sym.getQualifiedName()))
            injectMember(env, maker.MethodDef(maker.Modifiers(PUBLIC), names.init, maker.TypeIdent(TypeTag.VOID), List.nil(), List.of(maker.VarDef(maker.Modifiers(FINAL | PARAMETER), source, type, null)),
                    List.nil(), maker.Block(0L, List.of(maker.Exec(maker.Apply(List.nil(), maker.Ident(set), List.of(maker.Ident(source)))))), null));
        if (shouldInjectMethod(env, set, tree.sym.getQualifiedName())) {
            final Symbol.TypeSymbol superSymbol = tree.sym.getSuperclass().tsym;
            final Iterator<Symbol> iterator = superSymbol.members().getSymbolsByName(set, symbol ->
                    symbol instanceof Symbol.MethodSymbol methodSymbol && anyMatch(symbol.flags(), PROTECTED | PUBLIC) && methodSymbol.params().size() == 1 &&
                    methodSymbol.params().head.type.tsym == symbol.owner, Scope.LookupKind.NON_RECURSIVE).iterator();
            final @Nullable Symbol superSet = iterator.hasNext() ? iterator.next() : null;
            injectMember(env, maker.MethodDef(maker.Modifiers(PUBLIC), set, maker.TypeIdent(TypeTag.VOID), List.nil(), List.of(maker.VarDef(maker.Modifiers(FINAL | PARAMETER), source, type, null)),
                    List.nil(), maker.Block(0L, (superSet != null ? List.<JCTree.JCStatement>of(maker.Exec(maker.Apply(List.nil(), maker.Select(maker.Ident(names._super), set), List.of(maker.Ident(source))))) : List.<JCTree.JCStatement>nil())
                            .appendList(tree.defs.stream()
                                    .filter(JCTree.JCVariableDecl.class::isInstance)
                                    .map(JCTree.JCVariableDecl.class::cast)
                                    .filter(this::nonGenerating)
                                    .filter(field -> noneMatch(field.mods.flags, FINAL))
                                    .map(field -> maker.Exec(maker.Assign(maker.Select(maker.Ident(names._this), field.name), maker.Select(maker.Ident(source), field.name))))
                                    .collect(List.collector()))), null));
        }
        if (shouldInjectMethod(env, swap, tree.sym.getQualifiedName())) {
            final Symbol.TypeSymbol superSymbol = tree.sym.getSuperclass().tsym;
            final Iterator<Symbol> iterator = superSymbol.members().getSymbolsByName(swap, symbol ->
                    symbol instanceof Symbol.MethodSymbol methodSymbol && anyMatch(symbol.flags(), PROTECTED | PUBLIC) && methodSymbol.params().size() == 1 &&
                    methodSymbol.params().head.type.tsym == symbol.owner, Scope.LookupKind.NON_RECURSIVE).iterator();
            final @Nullable Symbol superSet = iterator.hasNext() ? iterator.next() : null;
            final Name $ = name("$");
            injectMember(env, maker.MethodDef(maker.Modifiers(PUBLIC), swap, maker.TypeIdent(TypeTag.VOID), List.nil(), List.of(maker.VarDef(maker.Modifiers(FINAL | PARAMETER), source, type, null)),
                    List.nil(), maker.Block(0L, (superSet != null ? List.<JCTree.JCStatement>of(maker.Exec(maker.Apply(List.nil(), maker.Select(maker.Ident(names._super), swap), List.of(maker.Ident(source))))) : List.<JCTree.JCStatement>nil())
                            .appendList(tree.defs.stream()
                                    .filter(JCTree.JCVariableDecl.class::isInstance)
                                    .map(JCTree.JCVariableDecl.class::cast)
                                    .filter(this::nonGenerating)
                                    .filter(field -> noneMatch(field.mods.flags, FINAL))
                                    .flatMap(field -> Stream.of(
                                            maker.VarDef(maker.Modifiers(FINAL), $.append(field.name), field.vartype, maker.Select(maker.Ident(names._this), field.name)),
                                            maker.Exec(maker.Assign(maker.Select(maker.Ident(names._this), field.name), maker.Select(maker.Ident(source), field.name))),
                                            maker.Exec(maker.Assign(maker.Select(maker.Ident(source), field.name), maker.Ident($.append(field.name))))
                                    ))
                                    .collect(List.collector()))), null));
        }
        if (shouldInjectMethod(env, names.clone))
            injectMember(env, maker.MethodDef(maker.Modifiers(PUBLIC), names.clone, type, List.nil(), List.nil(), List.nil(), maker.Block(0L, List.of(maker.Return(
                    maker.NewClass(null, List.nil(), maker.Ident(tree.sym), List.of(maker.Ident(names._this)), null)))), null));
    }
    
}
