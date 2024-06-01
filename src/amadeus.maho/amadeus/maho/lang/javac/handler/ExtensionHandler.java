package amadeus.maho.lang.javac.handler;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Extension;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.javac.handler.base.BaseHandler;
import amadeus.maho.lang.javac.handler.base.DynamicAnnotationHandler;
import amadeus.maho.lang.javac.handler.base.Handler;
import amadeus.maho.lang.javac.handler.base.HandlerSupport;
import amadeus.maho.lang.javac.multithreaded.SharedComponent;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformMetadata;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.runtime.DebugHelper;

import static amadeus.maho.lang.javac.handler.ExtensionHandler.PRIORITY;
import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Scope.LookupKind.NON_RECURSIVE;

@TransformProvider
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
@Handler(value = Extension.class, priority = PRIORITY)
public class ExtensionHandler extends BaseHandler<Extension> implements DynamicAnnotationHandler {
    
    public static final int PRIORITY = -1 << 24;
    
    public record SymbolTable(ConcurrentHashMap<Symbol.ModuleSymbol, CopyOnWriteArrayList<Symbol.ClassSymbol>> extensionProviders = { },
                              ConcurrentHashMap<Symbol.ModuleSymbol, CopyOnWriteArrayList<Symbol.ModuleSymbol>> importInfos = { },
                              ConcurrentHashMap<Symbol.ModuleSymbol, Map<Name, Collection<Symbol.MethodSymbol>>> extensionMethodsWithImport = { }) implements SharedComponent {
        
        public static SymbolTable instance(final Context context) = context.get(SymbolTable.class) ?? new SymbolTable().let(it -> context.put(SymbolTable.class, it));
        
        public Map<Name, Collection<Symbol.MethodSymbol>> lookupExtensionProvider(final Types types, final Env<AttrContext> env) = extensionMethodsWithImport().computeIfAbsent(env.toplevel.modle, module -> {
            final @Nullable Collection<Symbol.ModuleSymbol> moduleSymbols = importInfos()[module];
            final HashMap<Name, Collection<Symbol.MethodSymbol>> methods = { };
            (moduleSymbols == null ?
                    extensionProviders().values().stream().flatMap(Collection::stream) :
                    moduleSymbols.stream().map(symbol -> extensionProviders()[symbol]).nonnull().flatMap(Collection::stream))
                    .flatMap(owner -> owner.members().getSymbols(filter(), NON_RECURSIVE).fromIterable())
                    .cast(Symbol.MethodSymbol.class)
                    .forEach(methodSymbol -> {
                        final List<Symbol.VarSymbol> params = methodSymbol.params();
                        if (params.tail != null && !(params.head.type instanceof Type.JCPrimitiveType))
                            methods.computeIfAbsent(methodSymbol.name, _ -> new ArrayList<>()) += methodSymbol;
                    });
            return methods;
        });
        
        public Collection<Symbol.MethodSymbol> lookupExtensionProvider(final Types types, final Env<AttrContext> env, final Name name) = lookupExtensionProvider(types, env)[name];
        
        public static Predicate<Symbol> filter() = symbol -> {
            final long flags = symbol.flags();
            return symbol.kind == Kinds.Kind.MTH && (flags & (PUBLIC | STATIC)) == (PUBLIC | STATIC) && (flags & SYNTHETIC) == 0;
        };
        
    }
    
    @NoArgsConstructor
    @Handler(value = Extension.Import.class, priority = PRIORITY)
    public static class ImportHandler extends BaseHandler<Extension.Import> { // TODO Support incremental compilation, loading import information from class files.
        
        @Override
        public void processClass(final Env<AttrContext> env, final JCTree.JCClassDecl tree, final JCTree owner, final Extension.Import annotation, final JCTree.JCAnnotation annotationTree, final boolean advance) {
            if (tree.name == names.module_info) {
                final LinkedList<Symbol.ModuleSymbol> modules = { };
                final SymbolTable table = instance(SymbolTable.class);
                final Set<Symbol.ModuleSymbol> providerModules = table.extensionProviders().keySet();
                process(providerModules, modules::add, annotation.includes());
                process(providerModules, modules::remove, annotation.exclusions());
                table.importInfos()[tree.sym.packge().modle] = { modules };
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
    
    interface PolymorphicSignatureHolder {
        
        Set<String> methodNames = Stream.of(MethodHandle.class, VarHandle.class)
                .flatMap(clazz -> Stream.of(clazz.getDeclaredMethods()))
                .filter(method -> method.isAnnotationPresent(MethodHandle.PolymorphicSignature.class))
                .map(Method::getName)
                .collect(Collectors.toSet());
        
    }
    
    Set<Name> polymorphicSignatureMethodNames = PolymorphicSignatureHolder.methodNames.stream().map(names::fromString).collect(Collectors.toSet());
    
    Set<Symbol.TypeSymbol> handleSymbols = Set.of(symtab.methodHandleType.tsym, symtab.varHandleType.tsym);
    
    SymbolTable table = SymbolTable.instance(context);
    
    @Override
    public void initModules(final Set<Symbol.ModuleSymbol> modules) = table.extensionProviders().clear();
    
    @Override
    public Class<? extends Annotation> providerType() = Extension.Provider.class;
    
    @Override
    public Class<? extends Annotation> annotationType() = Extension.class;
    
    @Override
    public void addSymbol(final Symbol.ClassSymbol symbol) = table.extensionProviders().computeIfAbsent(symbol.packge().modle, _ -> new CopyOnWriteArrayList<>()) += symbol;
    
    @Override
    public void processClass(final Env<AttrContext> env, final JCTree.JCClassDecl tree, final JCTree owner, final Extension annotation, final JCTree.JCAnnotation annotationTree, final boolean advance) = addSymbol(tree.sym);
    
    private static Symbol findMethodInScope(
            final Resolve resolve,
            final Env<AttrContext> env,
            final List<Type> argTypes,
            final List<Type> typeArgTypes,
            final Collection<Symbol.MethodSymbol> methodSymbols,
            final Symbol bestSoFar,
            final boolean allowBoxing,
            final boolean useVarargs) {
        Symbol result = bestSoFar;
        for (final Symbol.MethodSymbol methodSymbol : methodSymbols)
            result = (Privilege) resolve.selectBest(env, methodSymbol.owner.type, argTypes, typeArgTypes, methodSymbol, result, allowBoxing, useVarargs);
        return result;
    }
    
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
        if (capture.kind == Kinds.Kind.MTH || capture.kind == Kinds.Kind.AMBIGUOUS || site.isErroneous() || argTypes.stream().anyMatch(Type::isErroneous) || !HandlerSupport.attrContext().contains(env.tree))
            return capture;
        final ExtensionHandler instance = instance(ExtensionHandler.class);
        if (instance.handleSymbols[site.tsym] && instance.polymorphicSignatureMethodNames[name] || instance.table.extensionProviders.isEmpty())
            return capture;
        final Attr.ResultInfo resultInfo = (Privilege) instance.attr.resultInfo;
        if (resultInfo == null)
            return capture;
        final @Nullable Collection<Symbol.MethodSymbol> extensionMethods = instance.table.lookupExtensionProvider((Privilege) $this.types, env, name);
        if (extensionMethods != null) {
            final List<Type> extArgTypes = argTypes.prepend(site);
            final Type pt = (Privilege) resultInfo.pt;
            @Nullable Runnable rollback = null;
            if (pt instanceof Type.ForAll forAll) {
                final Type.MethodType methodType = (Type.MethodType) forAll.qtype;
                final List<Type> rollbackArgTypes = methodType.argtypes, rollbackTvars = forAll.tvars;
                rollback = () -> { // Keep a copy for rollbacks that may be required.
                    methodType.argtypes = rollbackArgTypes;
                    forAll.tvars = rollbackTvars;
                };
                methodType.argtypes = methodType.argtypes.prepend(site);
            }
            Symbol result = capture;
            result = findMethodInScope($this, env, extArgTypes, typeArgTypes, extensionMethods, result, allowBoxing, useVarargs);
            if (result instanceof Resolve.InapplicableSymbolError || result instanceof Resolve.InapplicableSymbolsError) {
                if (pt instanceof Type.ForAll forAll) { // If there is a generic parameter list, try to fix the missing parameters.
                    if (!forAll.tvars.isEmpty() && typeArgTypes != null && !typeArgTypes.isEmpty()) {
                        forAll.tvars = forAll.tvars.prepend(site);
                        final List<Type> extTypeArgTypes = typeArgTypes.prepend(site);
                        result = findMethodInScope($this, env, extArgTypes, extTypeArgTypes, extensionMethods, result, allowBoxing, useVarargs);
                    }
                }
            }
            if (result instanceof Resolve.AmbiguityError ambiguityError)
                result = ((Privilege) ambiguityError.ambiguousSyms).getLast();
            if (result.kind == Kinds.Kind.MTH && result instanceof Symbol.MethodSymbol methodSymbol) {
                final TreeMaker maker = instance.maker.at(env.tree.pos);
                if (env.tree instanceof JCTree.JCMethodInvocation invocation) {
                    invocation.meth.type = result.type;
                    TreeInfo.setSymbol(invocation.meth, result);
                    final List<JCTree.JCExpression> args = invocation.args.prepend(invocation.meth instanceof JCTree.JCFieldAccess access ? access.selected : maker.Ident(instance.names._this));
                    final JCTree.JCMethodInvocation resolved = maker.Apply(invocation.typeargs, maker.QualIdent(result), args);
                    throw new ReAttrException(() -> invocation.type = resolved.type, resolved, invocation);
                } else if (env.tree instanceof JCTree.JCMemberReference reference) {
                    List<JCTree.JCVariableDecl> decls = IntStream.range(0, argTypes.size()).mapToObj(i -> maker.VarDef(maker.Modifiers(0L), instance.name(STR."$arg\{i}"), null, null, true)).collect(List.collector());
                    List<JCTree.JCExpression> args = decls.map(decl -> maker.Ident(decl.name));
                    final @Nullable Type type = ((Privilege) instance.deferredAttr.attribSpeculative(reference.expr, env.dup(reference.expr), (Privilege) instance.attr.unknownExprInfo)).type;
                    if (type != null) {
                        maker.at(env.tree.pos + 1);
                        if (!type.isErroneous())
                            args = args.prepend(reference.expr);
                        else {
                            final Name $ref = instance.name("$ref");
                            decls = decls.prepend(maker.VarDef(maker.Modifiers(0L), $ref, maker.Type(site), null));
                            args = args.prepend(maker.Ident($ref));
                        }
                        final JCTree.JCLambda lambda = maker.Lambda(decls, maker.Apply(List.nil(), maker.QualIdent(result), args));
                        throw new ReAttrException(() -> reference.type = lambda.type, lambda, reference);
                    } else
                        DebugHelper.breakpoint();
                }
            } else if (rollback != null) // Failed to look up a suitable result from the extension method.
                rollback.run(); // If the generic parameter list is modified, roll back to the previous state.
        }
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
