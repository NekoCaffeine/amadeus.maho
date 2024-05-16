package amadeus.maho.lang.javac.handler;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Check;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.Resolve;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Delegate;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.javac.JavacContext;
import amadeus.maho.lang.javac.MahoJavac;
import amadeus.maho.lang.javac.handler.base.BaseHandler;
import amadeus.maho.lang.javac.handler.base.DelayedContext;
import amadeus.maho.lang.javac.handler.base.Handler;
import amadeus.maho.lang.javac.handler.base.HandlerSupport;
import amadeus.maho.lang.javac.multithreaded.SharedComponent;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.concurrent.ConcurrentWeakIdentityHashMap;

import static amadeus.maho.util.bytecode.Bytecodes.IRETURN;
import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Kinds.Kind.*;

@NoArgsConstructor
@TransformProvider
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Handler(Delegate.class)
public class DelegateHandler extends BaseHandler<Delegate> {
    
    public record SharedData(
            Set<String> objectMethodIdentities = Set.of("getClass()", "equals(java.lang.Object)", "hashCode()", "toString()", "clone()", "notify()", "notifyAll()", "wait()", "wait(long)", "wait(long,int)"),
            ConcurrentWeakIdentityHashMap<Symbol.MethodSymbol, String> identitiesCache = { }) implements SharedComponent {
        
        public static SharedData instance(final Context context) = context.get(SharedData.class) ?? new SharedData().let(it -> context.put(SharedData.class, it));
        
        public String identity(final Symbol.MethodSymbol symbol) = identitiesCache().computeIfAbsent(symbol, JavacContext::methodIdentity);
        
        public boolean skip(final Symbol.MethodSymbol symbol, final Set<String> context) {
            final String identity = identity(symbol);
            return objectMethodIdentities()[identity] || !context.add(identity);
        }
        
    }
    
    public static class DelegateScope extends MembersScope {
        
        public DelegateScope(final Scope scope) = super(scope, symbol -> noneMatch(symbol.flags(), STATIC) && symbol.name != symbol.name.table.names.init);
        
    }
    
    SharedData shared = SharedData.instance(context);
    
    @Override
    public void processVariable(final Env<AttrContext> env, final JCTree.JCVariableDecl tree, final JCTree owner, final Delegate annotation, final JCTree.JCAnnotation annotationTree, final boolean advance)
            = tryDelegate(env, tree, annotationTree);
    
    @Override
    public void processMethod(final Env<AttrContext> env, final JCTree.JCMethodDecl tree, final JCTree owner, final Delegate annotation, final JCTree.JCAnnotation annotationTree, final boolean advance)
            = tryDelegate(env, tree, annotationTree);
    
    public void delayProcess(final Env<AttrContext> env, final JCTree tree, final JCTree owner, final Delegate annotation, final JCTree.JCAnnotation annotationTree) {
        if (annotation.hard())
            instance(DelayedContext.class).todos() += context -> instance(context, DelegateHandler.class).tryDelegate(env, tree, annotationTree);
    }
    
    public void tryDelegate(final Env<AttrContext> env, final JCTree tree, final JCTree.JCAnnotation annotationTree) {
        final Symbol symbol = symbol(tree);
        final HashSet<String> context = { };
        delegateTypes(types, true, symbol, symbol.enclClass().type).forEach(delegateType -> delegateType.tsym.members()
                .getSymbols(member -> noneMatch(member.flags(), STATIC | BRIDGE) && anyMatch(member.flags(), PUBLIC) && member.name != member.name.table.names.init)
                .fromIterable()
                .cast(Symbol.MethodSymbol.class)
                .forEach(methodSymbol -> {
                    if (!shared.skip(methodSymbol, context) && shouldInjectMethod(env, methodSymbol.name, methodSymbol.params.map(parameter -> parameter.type.tsym.getQualifiedName()).toArray(Name[]::new))) {
                        final TreeMaker maker = this.maker.forToplevel(env.toplevel).at(annotationTree.pos);
                        final JCTree.JCExpression qualifier = tree instanceof JCTree.JCMethodDecl ? maker.Apply(List.nil(), maker.Ident(name(tree)), List.nil()) : maker.Ident(name(tree));
                        final JCTree.JCMethodInvocation apply = maker.Apply(List.nil(), maker.Select(qualifier, methodSymbol.name), methodSymbol.params.stream().map(maker::Ident).collect(List.collector()));
                        final JCTree.JCMethodDecl methodDecl = maker.MethodDef(methodSymbol, maker.Block(0L, List.of(methodSymbol.getReturnType() instanceof Type.JCVoidType ? maker.Exec(apply) : maker.Return(apply))));
                        // noinspection DataFlowIssue
                        methodDecl.sym = null;
                        methodDecl.mods.flags &= DefaultValueHandler.removeFlags;
                        followAnnotation(annotationTree, "on", methodDecl.mods);
                        injectMember(env, methodDecl);
                    }
                }));
    }
    
    public static Stream<Type> delegateTypes(final Types types, final boolean hard, final Symbol symbol, final @Nullable Type site) = symbol.getAnnotationMirrors().stream()
            .filter(compound -> {
                if (compound.type.tsym.getQualifiedName().toString().equals(Delegate.class.getCanonicalName())) {
                    if (compound.values.stream().anyMatch(pair -> pair.fst.name.toString().equals("hard") && pair.snd.getValue() instanceof Boolean b && b != hard))
                        return false;
                    if (symbol instanceof Symbol.VarSymbol)
                        return true;
                    if (symbol instanceof Symbol.MethodSymbol methodSymbol) {
                        if (methodSymbol.params.isEmpty())
                            return true;
                        if (site != null) {
                            final DelegateHandler handler = instance(DelegateHandler.class);
                            final @Nullable Env<AttrContext> errorEnv = (Privilege) handler.typeEnvs.get(site.tsym);
                            final @Nullable JCTree def = errorEnv == null ? null : ~errorEnv.enclClass.defs.stream().lookup(it -> symbol(it) == methodSymbol);
                            final JCDiagnostic.Error error = { MahoJavac.KEY, "delegate.method.must.have.no.parameters", methodSymbol };
                            if (def != null)
                                handler.log.error(JCDiagnostic.DiagnosticFlag.RESOLVE_ERROR, def, error);
                            else
                                handler.log.error(error);
                        }
                    }
                }
                return false;
            })
            .flatMap(compound -> {
                final @Nullable Attribute only = compound.values.stream()
                        .filter(pair -> pair.fst.name.toString().equals("only"))
                        .map(pair -> pair.snd)
                        .findFirst()
                        .orElse(null);
                return only instanceof Attribute.Class clazz ? Stream.of(clazz.classType) : only instanceof Attribute.Array array ? Stream.of(array.values)
                        .filter(Attribute.Class.class::isInstance)
                        .map(Attribute.Class.class::cast)
                        .map(clazz -> clazz.classType)
                        .filter(type -> types.isAssignable(type, symbol.type))
                        .distinct() : Stream.of(symbol instanceof Symbol.MethodSymbol methodSymbol ? methodSymbol.getReturnType() : symbol.type);
            });
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static Symbol findFun(
            final Symbol capture,
            final Resolve $this,
            final Env<AttrContext> env,
            final Name name,
            final List<Type> argTypes,
            final List<Type> typeArgTypes,
            final boolean allowBoxing,
            final boolean useVarargs) {
        if (capture.kind == STATICERR) {
            final Type inType = env.enclClass.sym.type;
            final Symbol result = findMethod(capture, $this, env, inType, name, argTypes, typeArgTypes, inType.tsym.type, capture, allowBoxing, useVarargs);
            if (result.kind == MTH)
                return result;
        }
        final Symbol p_result[] = { capture };
        final @Nullable Scope scopes[] = ((Privilege) env.toplevel.namedImportScope.name2Scopes)[name];
        Stream.concat(scopes == null ? Stream.empty() : Stream.of(scopes), ((Privilege) env.toplevel.starImportScope.subScopes).stream())
                .cast(Scope.FilterImportScope.class)
                .filter(Scope.FilterImportScope::isStaticallyImported)
                .takeWhile(_ -> p_result[0].kind != MTH)
                .forEach(scope -> {
                    final Type inType = scope.owner.type;
                    p_result[0] = findMethod(p_result[0], $this, env, inType, name, argTypes, typeArgTypes, inType.tsym.type, p_result[0], allowBoxing, useVarargs);
                });
        return capture;
    }
    
    private static JCTree.JCMethodInvocation apply(final TreeMaker maker, final JCTree.JCExpression expression, final Symbol.MethodSymbol symbol)
            = maker.Apply(List.nil(), expression, List.nil()).let(it -> it.type = symbol.getReturnType());
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    public static Symbol findMethod(
            final Symbol capture,
            final Resolve $this,
            final Env<AttrContext> env,
            final Type site,
            final Name name,
            final List<Type> argTypes,
            final List<Type> typeArgTypes,
            final Type inType,
            final Symbol bestSoFar,
            final boolean allowBoxing,
            final boolean useVarargs) {
        if (name == name.table.names.init || capture.kind == AMBIGUOUS)
            return capture;
        final @Nullable JCTree tree = HandlerSupport.attrContext().peekLast();
        if (tree == null)
            return capture;
        final DelegateHandler handler = instance(DelegateHandler.class);
        final TreeMaker maker = handler.maker;
        // try fix same method sig static error, see com.sun.tools.javac.comp.Attr#visitSelect rs.accessBase(rs.new StaticError(sym)...)
        final Symbol methodNotFound = (Privilege) $this.methodNotFound,
                p_result[] = { tree instanceof JCTree.JCFieldAccess access && symbol(access.selected)?.kind ?? MTH == TYP && noneMatch(capture.flags(), STATIC) ? methodNotFound : capture };
        if (p_result[0].kind == MTH)
            return p_result[0];
        final Symbol.TypeSymbol objectTypeSymbol = handler.symtab.objectType.tsym;
        allSupers(inType.tsym)
                .takeWhile(symbol -> p_result[0].kind != MTH && symbol != objectTypeSymbol)
                .filter(symbol -> symbol.members() != null)
                .forEach(target -> {
                    target.getEnclosedElements().stream().takeWhile(_ -> p_result[0].kind != MTH).forEach(symbol -> delegateTypes((Privilege) $this.types, false, symbol, site).takeWhile(_ -> p_result[0].kind != MTH).forEach(type -> {
                        if ((p_result[0] = handler.findMethod(env, type, name, argTypes, typeArgTypes, type.tsym.type, p_result[0], allowBoxing, useVarargs, DelegateScope::new)).kind == MTH) {
                            switch (tree) {
                                case JCTree.JCIdent meth                -> {
                                    final JCTree.JCExpression
                                            accessor = anyMatch(symbol.flags_field, STATIC) ? maker.QualIdent(symbol) : maker.Ident(symbol),
                                            selected = symbol instanceof Symbol.MethodSymbol methodSymbol ? maker.Select(apply(maker, accessor, methodSymbol), p_result[0]) : maker.Select(accessor, p_result[0]);
                                    throw new ReAttrException(selected, tree);
                                }
                                case JCTree.JCMemberReference reference -> {
                                    final JCTree.JCExpression meth = reference.expr;
                                    final JCTree.JCExpression selected = symbol instanceof Symbol.MethodSymbol methodSymbol ? apply(maker, maker.Select(meth, symbol), methodSymbol) : maker.Select(meth, symbol);
                                    throw new ReAttrException(selected, tree);
                                }
                                case JCTree.JCFieldAccess access        -> {
                                    access.selected = symbol instanceof Symbol.MethodSymbol methodSymbol ? apply(maker, maker.Select(access.selected, symbol), methodSymbol) : maker.Select(access.selected, symbol);
                                    throw new ReAttrException(access, access);
                                }
                                default                                 -> throw new UnsupportedOperationException(STR."\{tree.getClass()} - \{tree}");
                            }
                        }
                    }));
                    if (p_result[0].kind != MTH)
                        IncludeHandler.includeTypes(target)
                                .takeWhile(_ -> p_result[0].kind != MTH)
                                .forEach(type -> p_result[0] = findMethodInScope($this, env, type, name, argTypes, typeArgTypes, new IncludeHandler.StaticMethodScope(type.tsym.members()), p_result[0], allowBoxing, useVarargs, false));
                });
        return p_result[0] == methodNotFound && p_result[0] != capture ? capture : p_result[0];
    }
    
    @Privilege
    private Symbol findMethod(
            final Env<AttrContext> env,
            final Type site,
            final Name name,
            final List<Type> argTypes,
            final List<Type> typeArgTypes,
            final Type inType,
            Symbol bestSoFar,
            final boolean allowBoxing,
            final boolean useVarargs,
            final UnaryOperator<Scope> mapper) {
        final List<Type> iTypes[] = (List<Type>[]) new List[]{ List.nil(), List.nil() };
        {
            @Nullable Resolve.InterfaceLookupPhase phase = Resolve.InterfaceLookupPhase.ABSTRACT_OK;
            for (final Symbol.TypeSymbol superSymbol : resolve.superclasses(inType)) {
                if (superSymbol == symtab.objectType.tsym)
                    break;
                bestSoFar = resolve.findMethodInScope(env, site, name, argTypes, typeArgTypes, mapper.apply(superSymbol.members()), bestSoFar, allowBoxing, useVarargs, true);
                if (name == names.init)
                    return bestSoFar;
                phase = phase == null ? null : phase.update(superSymbol, resolve);
                if (phase != null)
                    for (final Type iType : types.interfaces(superSymbol.type))
                        iTypes[phase.ordinal()] = types.union(types.closure(iType), iTypes[phase.ordinal()]);
            }
        }
        final Symbol concrete = bestSoFar.kind.isValid() && (bestSoFar.flags() & ABSTRACT) == 0 ? bestSoFar : resolve.methodNotFound;
        for (final Resolve.InterfaceLookupPhase phase : Resolve.InterfaceLookupPhase.values())
            for (final Type iType : iTypes[phase.ordinal()]) {
                if (!iType.isInterface() || phase == Resolve.InterfaceLookupPhase.DEFAULT_OK && (iType.tsym.flags() & DEFAULT) == 0)
                    continue;
                bestSoFar = resolve.findMethodInScope(env, site, name, argTypes, typeArgTypes, iType.tsym.members(), bestSoFar, allowBoxing, useVarargs, true);
                if (concrete != bestSoFar && concrete.kind.isValid() && bestSoFar.kind.isValid() && types.isSubSignature(concrete.type, bestSoFar.type))
                    bestSoFar = concrete;
            }
        return bestSoFar;
    }
    
    @Hook(at = @At(insn = @At.Insn(opcode = IRETURN), offset = 1, ordinal = 0), before = false)
    private static Hook.Result checkTypeContainsImportableElement(final Check $this, final Symbol.TypeSymbol symbol, final Symbol.TypeSymbol origin, final Symbol.PackageSymbol pkg, final Name name, final Set<Symbol> processed)
            = Hook.Result.falseToVoid(symbol != null && symbol == origin && checkContainsImportableDelegateElements($this, symbol, pkg, name));
    
    private static boolean checkContainsImportableDelegateElements(final Check $this, final Symbol.TypeSymbol symbol, final Symbol.PackageSymbol pkg, final Name name) = allSupers(symbol)
            .flatMap(target -> target.getEnclosedElements().stream())
            .filter(member -> anyMatch(member.flags_field, STATIC))
            .anyMatch(member -> delegateTypes((Privilege) $this.types, false, member, member.type)
                    .map(type -> type.tsym)
                    .anyMatch(context -> checkNonStaticImportableElement($this, (Privilege) $this.types, context, context, pkg, name)));
    
    private static boolean checkNonStaticImportableElement(final Check check, final Types types, final @Nullable Symbol.TypeSymbol symbol, final Symbol.TypeSymbol origin,
            final Symbol.PackageSymbol packageSymbol, final Name name, final Set<Symbol> processed = new HashSet<>()) {
        if (symbol == null || !processed.add(symbol))
            return false;
        if (checkNonStaticImportableElement(check, types, types.supertype(symbol.type).tsym, origin, packageSymbol, name, processed))
            return true;
        for (final Type interfaceType : types.interfaces(symbol.type))
            if (checkNonStaticImportableElement(check, types, interfaceType.tsym, origin, packageSymbol, name, processed))
                return true;
        for (final Symbol member : symbol.members().getSymbolsByName(name))
            if (check.importAccessible(member, packageSymbol) && !member.isStatic() && member.isMemberOf(origin, types))
                return true;
        return false;
    }
    
}