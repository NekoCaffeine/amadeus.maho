package amadeus.maho.lang.javac.handler;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.Resolve;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Rearrange;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.javac.MahoJavac;
import amadeus.maho.lang.javac.handler.base.BaseHandler;
import amadeus.maho.lang.javac.handler.base.DelayedContext;
import amadeus.maho.lang.javac.handler.base.Handler;
import amadeus.maho.lang.javac.handler.base.HandlerSupport;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.runtime.ArrayHelper;

import static com.sun.tools.javac.code.Flags.*;

@TransformProvider
@NoArgsConstructor
@Handler(Rearrange.class)
public class RearrangeHandler extends BaseHandler<Rearrange> {
    
    public boolean fakeSymbol;
    
    @Override
    public void processClass(final Env<AttrContext> env, final JCTree.JCClassDecl tree, final JCTree owner, final Rearrange annotation, final JCTree.JCAnnotation annotationTree, final boolean advance) {
        if (noneMatch(tree.mods.flags, RECORD))
            log.error(JCDiagnostic.DiagnosticFlag.RESOLVE_ERROR, annotationTree, new JCDiagnostic.Error(MahoJavac.KEY, "rearrange.target.must.be.record"));
        final String alias[] = annotation.alias();
        final List<JCTree.JCVariableDecl> recordFields = TreeInfo.recordFields(tree);
        final int length = recordFields.size();
        if (length < 2)
            log.error(JCDiagnostic.DiagnosticFlag.RESOLVE_ERROR, annotationTree, new JCDiagnostic.Error(MahoJavac.KEY, "rearrange.target.invalid.fields.length"));
        if (alias.length == 0 || Stream.of(alias).mapToInt(String::length).anyMatch(it -> it != length))
            log.error(JCDiagnostic.DiagnosticFlag.RESOLVE_ERROR, annotationTree, new JCDiagnostic.Error(MahoJavac.KEY, "rearrange.target.invalid.fields.alisa"));
        final Type componentType = recordFields[0].sym.type;
        instance(DelayedContext.class).todos() += context -> instance(context, RearrangeHandler.class).process(annotation, annotationTree, recordFields, componentType);
    }
    
    private void process(final Rearrange annotation, final JCTree.JCAnnotation annotationTree, final List<JCTree.JCVariableDecl> recordFields, final Type componentType) {
        if (recordFields.stream().skip(1L).anyMatch(field -> !types.isSameType(componentType, field.sym.type)))
            log.error(JCDiagnostic.DiagnosticFlag.RESOLVE_ERROR, annotationTree, new JCDiagnostic.Error(MahoJavac.KEY, "rearrange.components.must.be.same"));
        if (annotation.accessJavacTypes(Rearrange::adapters)
                .cast(Type.ClassType.class)
                .anyMatch(classType -> classType.tsym.members().getSymbols(symbol -> symbol instanceof Symbol.VarSymbol varSymbol && anyMatch(varSymbol.flags_field, RECORD), Scope.LookupKind.NON_RECURSIVE)
                        .fromIterable()
                        .anyMatch(fieldSymbol -> !types.isSameType(componentType, fieldSymbol.type))))
            log.error(JCDiagnostic.DiagnosticFlag.RESOLVE_ERROR, annotationTree, new JCDiagnostic.Error(MahoJavac.KEY, "rearrange.adapters.components.must.be.same"));
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static Symbol findField(final Symbol capture, final Resolve $this, final Env<AttrContext> env, final Type site, final Name name, final Symbol.TypeSymbol symbol) {
        if (capture.kind == Kinds.Kind.VAR || capture.kind == Kinds.Kind.AMBIGUOUS)
            return capture;
        if (HandlerSupport.attrContext().peekLast() instanceof JCTree.JCFieldAccess access && symbol instanceof Symbol.ClassSymbol classSymbol) {
            final @Nullable Rearrange rearrange = symbol.getAnnotation(Rearrange.class);
            if (rearrange != null && rearrange.alias().length > 0) {
                final String string = name.toString();
                final long count = string.codePoints().count();
                return Stream.of(rearrange.alias()).map(alias -> {
                    final int codePoints[] = alias.codePoints().toArray(), indexes[] = string.codePoints()
                            .map(codePoint -> ArrayHelper.indexOf(codePoints, codePoint))
                            .takeWhile(index -> index > -1)
                            .toArray();
                    return count == indexes.length ? instance(RearrangeHandler.class).rearrange(capture, rearrange, indexes, env, access, classSymbol) : null;
                }).nonnull().findFirst().orElse(capture);
            }
        }
        return capture;
    }
    
    public Symbol rearrange(final Symbol capture, final Rearrange rearrange, final int indexes[], final Env<AttrContext> env, final JCTree.JCFieldAccess access, final Symbol.ClassSymbol symbol) {
        final @Nullable Symbol.ClassSymbol targetSymbol;
        if (indexes.length == symbol.getRecordComponents().size())
            targetSymbol = symbol;
        else {
            targetSymbol = rearrange.accessJavacTypes(Rearrange::adapters)
                    .cast(Type.class)
                    .map(type -> type.tsym)
                    .cast(Symbol.ClassSymbol.class)
                    .peek(Symbol.ClassSymbol::complete)
                    .filter(classSymbol -> classSymbol.getRecordComponents().size() == indexes.length)
                    .findFirst()
                    .orElse(null);
        }
        if (targetSymbol != null) {
            if (fakeSymbol)
                return new Symbol.VarSymbol(0L, name("$fake"), targetSymbol.type, symtab.noSymbol);
            let(indexes, env, access, symbol, targetSymbol);
        }
        return capture;
    }
    
    private void let(final int indexes[], final Env<AttrContext> env, final JCTree.JCFieldAccess access, final Symbol.ClassSymbol symbol, final Symbol.ClassSymbol targetSymbol) {
        final @Nullable List<? extends Symbol.RecordComponent> components = symbol.getRecordComponents();
        final TreeMaker maker = this.maker.forToplevel(env.toplevel).at(access.pos);
        final JCTree.JCExpression expression;
        if (access.selected instanceof JCTree.JCIdent ident) {
            final List<JCTree.JCExpression> args = IntStream.of(indexes).mapToObj(index -> components[index]).map(it -> maker.Apply(List.nil(), maker.Select(ident, it), List.nil())).collect(List.collector());
            expression = maker.NewClass(null, List.nil(), maker.Ident(targetSymbol), args, null);
        } else {
            final Name letName = LetHandler.nextName(names);
            final JCTree.JCVariableDecl let = maker.VarDef(maker.Modifiers(FINAL), letName, maker.Type(access.selected.type), access.selected);
            final List<JCTree.JCExpression> args = IntStream.of(indexes).mapToObj(index -> components[index]).map(it -> maker.Apply(List.nil(), maker.Select(maker.Ident(letName), it), List.nil())).collect(List.collector());
            expression = maker.LetExpr(let, maker.NewClass(null, List.nil(), maker.Ident(targetSymbol), args, null));
        }
        throw new ReAttrException(() -> access.type = expression.type, expression, access);
    }
    
}
