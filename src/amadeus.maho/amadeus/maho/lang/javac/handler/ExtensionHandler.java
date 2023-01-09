package amadeus.maho.lang.javac.handler;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.Resolve;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Extension;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.javac.handler.base.BaseHandler;
import amadeus.maho.lang.javac.handler.base.DynamicAnnotationHandler;
import amadeus.maho.lang.javac.handler.base.Handler;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformMetadata;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.function.FunctionHelper;

import static amadeus.maho.lang.javac.handler.ExtensionHandler.PRIORITY;

@TransformProvider
@Getter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Handler(value = Extension.class, priority = PRIORITY)
public class ExtensionHandler extends BaseHandler<Extension> implements DynamicAnnotationHandler {
    
    public static final int PRIORITY = -1 << 24;
    
    @NoArgsConstructor
    @Handler(value = Extension.Import.class, priority = PRIORITY)
    public static class ImportHandler extends BaseHandler<Extension.Import> { // TODO Support incremental compilation, loading import information from class files.
        
        @Override
        public void processClass(final Env<AttrContext> env, final JCTree.JCClassDecl tree, final JCTree owner, final Extension.Import annotation, final JCTree.JCAnnotation annotationTree, final boolean advance) {
            if (tree.name == names.module_info) {
                final ExtensionHandler instance = instance(ExtensionHandler.class);
                final LinkedList<Symbol.ModuleSymbol> modules = { };
                final Set<Symbol.ModuleSymbol> providerModules = instance.extensionProviders().keySet();
                process(providerModules, modules::add, annotation.includes());
                process(providerModules, modules::remove, annotation.exclusions());
                instance.importInfos()[tree.sym.packge().modle] = modules;
            }
        }
        
        protected void process(final Collection<Pattern> patterns = Stream.of(regexes)
                // The Handler for @RegularExpression will prompt the user for exceptions from regular expressions.
                .map(regex -> { try { return Pattern.compile(regex); } catch (PatternSyntaxException e) { return null; } })
                .nonnull()
                .toList(), final Collection<Symbol.ModuleSymbol> modules, final Consumer<Symbol.ModuleSymbol> consumer, final String... regexes) = modules.stream()
                .filter(module -> patterns.stream().anyMatch(pattern -> pattern.matcher(module.name.toString()).matches()))
                .forEach(consumer);
        
    }
    
    Map<Symbol.ModuleSymbol, Collection<Symbol.ClassSymbol>> extensionProviders = new ConcurrentHashMap<>();
    
    Map<Symbol.ModuleSymbol, Collection<Symbol.ModuleSymbol>> importInfos = new ConcurrentHashMap<>();
    
    @Override
    public void initModules(final Set<Symbol.ModuleSymbol> modules) = extensionProviders().clear();
    
    @Override
    public Class<? extends Annotation> providerType() = Extension.Provider.class;
    
    @Override
    public Class<? extends Annotation> annotationType() = Extension.class;
    
    @Override
    public void addSymbol(final Symbol.ClassSymbol symbol) = extensionProviders().computeIfAbsent(symbol.packge().modle, FunctionHelper.abandon(ArrayList::new)) += symbol;
    
    public Collection<Symbol.ClassSymbol> lookupExtensionProvider(final Env<AttrContext> env) {
        final Symbol.ModuleSymbol module = env.toplevel.modle;
        final @Nullable Collection<Symbol.ModuleSymbol> moduleSymbols = importInfos()[module];
        return moduleSymbols == null ?
                extensionProviders().values().stream().flatMap(Collection::stream).toList() :
                moduleSymbols.stream().map(symbol -> extensionProviders().computeIfAbsent(symbol, FunctionHelper.abandon(ArrayList::new))).flatMap(Collection::stream).toList();
    }
    
    @Override
    public void processClass(final Env<AttrContext> env, final JCTree.JCClassDecl tree, final JCTree owner, final Extension annotation, final JCTree.JCAnnotation annotationTree, final boolean advance) = addSymbol(tree.sym);
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true, metadata = @TransformMetadata(order = 1 << 5))
    private static Symbol findMethod(
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
        if (capture.kind == Kinds.Kind.MTH || capture.kind == Kinds.Kind.AMBIGUOUS || site.isErroneous() || argTypes.stream().anyMatch(Type::isErroneous) || OperatorOverloadingHandler.context.get() != null)
            return capture;
        final ExtensionHandler instance = instance(ExtensionHandler.class);
        if (instance.extensionProviders.isEmpty())
            return capture;
        final Object resultInfo = resultInfo(instance.attr);
        if (resultInfo == null)
            return capture;
        final List<Type> extArgTypes = env.tree instanceof JCTree.JCMemberReference ? argTypes : argTypes == null ? List.of(site) : argTypes.prepend(site);
        final Type pt = pt(resultInfo);
        @Nullable Runnable rollback = null;
        if (pt instanceof Type.ForAll forAll) {
            final Type.MethodType methodType = (Type.MethodType) forAll.qtype;
            final List<Type> rollbackArgTypes = methodType.argtypes, rollbackTvars = forAll.tvars;
            rollback = () -> { // Keep a copy for rollbacks that may be required.
                methodType.argtypes = rollbackArgTypes;
                forAll.tvars = rollbackTvars;
            };
            methodType.argtypes = env.tree instanceof JCTree.JCMemberReference ? methodType.argtypes : methodType.argtypes == null ? List.of(site) : methodType.argtypes.prepend(site);
        }
        Symbol result = capture;
        // Lookup available method from classes marked by @Extension.
        final Collection<Symbol.ClassSymbol> extensionProviders = instance.lookupExtensionProvider(env);
        for (final Symbol.ClassSymbol extensionProvider : extensionProviders)
            result = findMethodInScope($this, env, extensionProvider.type, name, extArgTypes, typeArgTypes, new StaticMembersScope(extensionProvider.members()), result, allowBoxing, useVarargs, false);
        if (instanceofInapplicableSymbolError(result) || instanceofInapplicableSymbolsError(result)) {
            if (pt instanceof Type.ForAll forAll) { // If there is a generic parameter list, try to fix the missing parameters.
                if (forAll.tvars.size() != 0 && typeArgTypes != null && typeArgTypes.size() != 0) {
                    forAll.tvars = forAll.tvars.prepend(site);
                    final List<Type> extTypeArgTypes = typeArgTypes.prepend(site);
                    for (final Symbol.ClassSymbol extensionProvider : extensionProviders)
                        result = findMethodInScope($this, env, extensionProvider.type, name, extArgTypes, extTypeArgTypes, new StaticMembersScope(extensionProvider.members()), result, allowBoxing, useVarargs, false);
                }
            }
        }
        if (result.kind == Kinds.Kind.MTH) {
            if (env.tree instanceof JCTree.JCMethodInvocation invocation) {
                invocation.meth.type = result.type;
                TreeInfo.setSymbol(invocation.meth, result);
                final List<JCTree.JCExpression> args = invocation.args.prepend(invocation.meth instanceof JCTree.JCFieldAccess access ? access.selected : instance.maker.at(invocation.pos).Ident(instance.names._this));
                final JCTree.JCMethodInvocation resolved = instance.maker.at(invocation.pos).Apply(invocation.typeargs, instance.maker.at(invocation.meth.pos).QualIdent(result), args);
                throw new ReAttrException(() -> invocation.type = resolved.type, resolved, invocation);
            } else if (env.tree instanceof JCTree.JCMemberReference reference) {
                reference.sym = result;
                reference.referentType = result.type;
            }
            return result;
        } else if (rollback != null) // Failed to look up a suitable result from the extension method.
            rollback.run(); // If the generic parameter list is modified, roll back to the previous state.
        return capture;
    }
    
    // <T> => Consumer<T> c;
    // <T> => T t;
    // t.let(c)
    // ^ t => Object, c => Consumer<T>
    // Object and T are not the same type, which will cause extension method `let` parsing to fail.
    @Hook
    private static Hook.Result selectSym(final Attr $this, final JCTree.JCFieldAccess tree, final Symbol location, final Type site, final Env<AttrContext> env, final Attr.ResultInfo resultInfo) {
        final Type type = (Privilege) resultInfo.pt;
        if (site.getTag() == TypeTag.TYPEVAR && type instanceof Type.ForAll)
            return { (Privilege) ((Privilege) $this.rs).resolveQualifiedMethod(tree.pos(), env, location, site, tree.name, type.getParameterTypes(), type.getTypeArguments()) };
        return Hook.Result.VOID;
    }
    
    @Hook
    private static Hook.Result isSubClass(final Symbol $this, final Symbol base, final Types types) = switch ($this) {
        case Symbol.TypeVariableSymbol variableSymbol -> types.capture($this.type.getUpperBound()).tsym.isSubClass(base, types) ? Hook.Result.TRUE : Hook.Result.FALSE;
        default                                       -> Hook.Result.VOID;
    };
    
    @Hook(store = 1)
    private static Symbol.TypeSymbol isMemberOf(final Symbol $this, final Symbol.TypeSymbol clazz, final Types types) = clazz instanceof Symbol.TypeVariableSymbol variableSymbol ? variableSymbol.type.getUpperBound().tsym : clazz;
    
}
