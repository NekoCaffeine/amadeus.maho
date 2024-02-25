package amadeus.maho.lang.javac.handler;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Map;

import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.ArgumentAttr;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Check;
import com.sun.tools.javac.comp.DeferredAttr;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.LambdaToMethod;
import com.sun.tools.javac.comp.Lower;
import com.sun.tools.javac.comp.Resolve;
import com.sun.tools.javac.comp.TransTypes;
import com.sun.tools.javac.jvm.Gen;
import com.sun.tools.javac.jvm.PoolConstant;
import com.sun.tools.javac.resources.CompilerProperties;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Constant;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.Special;
import amadeus.maho.lang.WeakLinking;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.javac.JavacContext;
import amadeus.maho.lang.javac.handler.base.HandlerSupport;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformMetadata;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.runtime.ArrayHelper;
import amadeus.maho.util.runtime.DebugHelper;

import static amadeus.maho.core.extension.DynamicLookup.*;
import static amadeus.maho.util.bytecode.Bytecodes.*;
import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Kinds.Kind.*;
import static com.sun.tools.javac.code.TypeTag.*;

@TransformProvider
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PrivilegeHandler extends JavacContext {
    
    @Getter
    @NoArgsConstructor
    public static class PrivilegeMethodSymbol extends DynamicMethodSymbol {
        
        JavacContext context = instance();
        
        Type.MethodType methodType = type.asMethodType(), sourceType = { methodType.argtypes, methodType.restype, methodType.thrown, methodType.tsym };
        
        @Override
        public Type getReturnType() = sourceType().restype;
        
        { staticArgs = ArrayHelper.add(staticArgs, PoolConstant.LoadableConstant.String(descriptor())); }
        
        public String descriptor() {
            final StringBuilder builder = { 1 << 6 };
            final Type.MethodType methodType = type.asMethodType();
            methodType.argtypes.stream().map(this::descriptor).forEach(builder.append('(')::append);
            builder.append(')').append(descriptor(methodType.restype));
            return builder.toString();
        }
        
        private String descriptor(final Type type) = switch (type.getTag()) {
            case BYTE    -> "B";
            case SHORT   -> "S";
            case CHAR    -> "C";
            case INT     -> "I";
            case LONG    -> "J";
            case FLOAT   -> "F";
            case DOUBLE  -> "D";
            case BOOLEAN -> "Z";
            case VOID    -> "V";
            default      -> {
                final Type erasure = context.types.erasure(type);
                if (erasure instanceof Type.ArrayType arrayType) {
                    final StringBuilder builder = { 1 << 4 };
                    Type elementType = arrayType;
                    while (elementType instanceof Type.ArrayType next) {
                        builder.append('[');
                        elementType = next.elemtype;
                    }
                    yield builder + descriptor(elementType);
                }
                yield objectDescriptor(erasure);
            }
        };
        
        private static String objectDescriptor(final Type erasure) = STR."L\{erasure.tsym.flatName().toString().replace('.', '/')};";
        
        {
            final Type.MethodType methodType = type.asMethodType();
            methodType.argtypes = methodType.argtypes.stream().map(this::mapType).collect(List.collector());
            // methodType.restype = mapType(methodType.restype); // not necessary
        }
        
        private Type mapType(final Type type) = switch (type.getTag()) {
            case BYTE,
                 SHORT,
                 CHAR,
                 INT,
                 LONG,
                 FLOAT,
                 DOUBLE,
                 BOOLEAN,
                 VOID -> type;
            default   -> context.symtab.objectType;
        };
        
    }
    
    public static class TypeCastParensType extends ArgumentAttr.ArgumentType<JCTree.JCTypeCast> {
        
        ArgumentAttr attr;
        
        public TypeCastParensType(final ArgumentAttr argumentAttr, final JCTree.JCExpression tree, final Env<AttrContext> env, final JCTree.JCTypeCast speculativeTree, final Map<Attr.ResultInfo, Type> speculativeTypes = new HashMap<>()) {
            argumentAttr.super(tree, env, speculativeTree, speculativeTypes);
            attr = argumentAttr;
        }
        
        @Override
        protected Type overloadCheck(final Attr.ResultInfo resultInfo, final DeferredAttr.DeferredAttrContext deferredAttrContext) = (Privilege) attr.checkSpeculative(speculativeTree.expr, resultInfo);
        
        @Override
        protected TypeCastParensType dup(final JCTree.JCTypeCast tree, final Env<AttrContext> env) = { attr, tree, env, speculativeTree, speculativeTypes };
        
    }
    
    private static final int
            SPECIAL = 1 << 8;
    
    Name
            Privilege          = name("amadeus.maho.lang.Privilege"),
            PrivilegeMark      = name("amadeus.maho.lang.Privilege$Mark"),
            makePrivilegeSite  = name("makePrivilegeSite"),
            makePrivilegeProxy = name("makePrivilegeProxy"),
            _new               = name("new");
    
    LinkedList<JCTree.JCAnnotation> dequeLocal = { };
    
    public static boolean inPrivilegeContext(final java.util.List<JCTree> context, final @Nullable Env<AttrContext> env = null)
            = inPrivilegeMarkDomains(context) || canAccessByTypeCast(context, env);
    
    private static boolean inPrivilegeMarkDomains(final java.util.List<JCTree> context)
            = context.stream().map(JavacContext::symbol).nonnull().anyMatch(symbol -> hasAnnotation(symbol, Privilege.class));
    
    private static boolean canAccessByTypeCast(final java.util.List<JCTree> context, final @Nullable Env<AttrContext> env) {
        @Nullable JCTree prev = null;
        for (final ListIterator<JCTree> iterator = context.listIterator(context.size()); iterator.hasPrevious(); ) {
            final JCTree next = iterator.previous();
            if (env != null && next instanceof JCTree.JCPolyExpression polyExpression)
                if (env.tree == polyExpression && env.next?.tree ?? null instanceof JCTree.JCTypeCast cast && cast.expr == polyExpression && cast.clazz.type.tsym.flatName() == instance(PrivilegeHandler.class).Privilege)
                    return true;
            if (prev == null && (next instanceof JCTree.JCIdent || next instanceof JCTree.JCFieldAccess || next instanceof JCTree.JCNewClass) ||
                next instanceof JCTree.JCMethodInvocation invocation && invocation.meth == prev ||
                next instanceof JCTree.JCAssign assign && assign.lhs == prev ||
                next instanceof JCTree.JCAssignOp assignOp && assignOp.lhs == prev ||
                next instanceof JCTree.JCParens ||
                HandlerSupport.speculativeContext()[next] == prev) {
                prev = next;
                continue;
            }
            return next instanceof JCTree.JCTypeCast cast && cast.expr == prev && cast.clazz.type.tsym.flatName() == instance(PrivilegeHandler.class).Privilege;
        }
        return false;
    }
    
    @Hook(forceReturn = true)
    private static boolean importAccessible(final Check $this, final Symbol symbol, final Symbol.PackageSymbol packageSymbol) = true;
    
    @Hook
    private static Hook.Result selectBest(
            final Resolve $this,
            final Env<AttrContext> env,
            final Type site,
            final List<Type> argTypes,
            final List<Type> typeArgTypes,
            final Symbol sym,
            final Symbol bestSoFar,
            final boolean allowBoxing,
            final boolean useVarargs) = Hook.Result.falseToVoid(sym instanceof Resolve.AccessError, bestSoFar); // NPE: sym.owner == null
    
    @Hook
    private static <T extends JCTree> void translate_$Enter(final LambdaToMethod $this, final T tree) {
        if (tree instanceof JCTree.JCMethodDecl decl)
            instance(PrivilegeHandler.class).dequeLocal.addFirst(instance(PrivilegeHandler.class).marker.getAnnotationTreesByType(decl.mods, (Privilege) $this.attrEnv, Privilege.class).stream().findFirst().orElse(null));
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.FINALLY)))
    private static <T extends JCTree> void translate_$Exit(final LambdaToMethod $this, final T tree) {
        if (tree instanceof JCTree.JCMethodDecl decl)
            instance(PrivilegeHandler.class).dequeLocal.removeFirst();
    }
    
    @Hook
    private static void makeLambdaBody(final LambdaToMethod $this, final JCTree.JCLambda tree, final JCTree.JCMethodDecl lambdaMethodDecl)
            = instance(PrivilegeHandler.class).dequeLocal.stream().nonnull().findFirst().ifPresent(annotation -> {
        lambdaMethodDecl.mods.annotations = lambdaMethodDecl.mods.annotations.append(annotation);
        final PrivilegeHandler handler = instance(PrivilegeHandler.class);
        lambdaMethodDecl.sym.appendAttributes(List.of(new Attribute.Compound(handler.symtab.enterClass(handler.mahoModule, handler.Privilege).type, List.nil())));
    });
    
    @Hook
    private static void visitMethodDef(final Gen $this, final JCTree.JCMethodDecl tree) = instance(PrivilegeHandler.class).checkPrivilege($this, tree);
    
    private void checkPrivilege(final Gen $this, final JCTree.JCMethodDecl tree) {
        if (hasAnnotation(tree.sym, Privilege.class) && tree.body != null && tree.body.stats.nonEmpty()) {
            List<JCTree.JCStatement> statements = tree.body.stats;
            if (tree.name == names.init) {
                final @Nullable JCTree.JCMethodInvocation firstConstructorCall = TreeInfo.firstConstructorCall(tree);
                if (firstConstructorCall != null) {
                    while (statements.nonEmpty())
                        if (statements.head instanceof JCTree.JCExpressionStatement statement && statement.expr == firstConstructorCall)
                            break;
                        else
                            statements = statements.tail;
                    if (statements.nonEmpty())
                        statements = statements.tail;
                    else
                        return;
                }
            }
            if (statements.nonEmpty()) {
                final Symbol.MethodSymbol symbol = tree.sym;
                final boolean isStatic = anyMatch(symbol.flags_field, STATIC);
                final Type.MethodType sourceType = symbol.type.asMethodType(), accessType = isStatic ? sourceType : new Type.MethodType(sourceType.argtypes.prepend(symbol.owner.type), sourceType.restype, sourceType.thrown, sourceType.tsym);
                final long flags = symbol.flags_field & ~(PUBLIC | PROTECTED) | PRIVATE | STATIC | SYNTHETIC;
                final Symbol.MethodSymbol accessSymbol = { flags, name(mapMethodName(symbol.name.toString())), symbol.type instanceof Type.ForAll forAll ? new Type.ForAll(forAll.tvars, accessType) : accessType, symbol.owner };
                accessSymbol.params = symbol.params.map(var -> {
                    final Symbol.VarSymbol copy = { var.flags_field, var.name, var.type, accessSymbol };
                    copy.pos = var.pos;
                    copy.adr = isStatic ? var.adr : var.adr + 1;
                    return copy;
                });
                if (!isStatic) {
                    final Symbol.VarSymbol thisSym = { FINAL | SYNTHETIC | PARAMETER, names.dollarThis, symbol.owner.type, symbol.owner };
                    thisSym.adr = 0;
                    accessSymbol.params = accessSymbol.params.prepend(thisSym);
                }
                accessSymbol.appendAttributes(List.of(new Attribute.Compound(symtab.enterClass(mahoModule, PrivilegeMark).type, List.nil())));
                final JCTree.JCMethodDecl accessTree = maker.at(tree.pos).MethodDef(accessSymbol, maker.at(tree.body.pos).Block(0L, List.from(statements)));
                accessTree.accept(new TreeScanner() {
                    
                    @Override
                    public void visitIdent(final JCTree.JCIdent that) {
                        final int index = symbol.params.indexOf(that.sym);
                        if (index > -1)
                            that.sym = accessSymbol.params[isStatic ? index : index + 1];
                    }
                    
                });
                accessSymbol.owner.members().enter(accessSymbol);
                accessTree.accept($this);
                final DynamicMethodSymbol invokeAccess = { accessSymbol.name, symtab.noSymbol, lookupDynamicLookupMethod(makePrivilegeProxy).asHandle(), accessSymbol.type.asMethodType(), new PoolConstant.LoadableConstant[0] };
                final List<JCTree.JCExpression> args = symbol.params.map(maker::Ident);
                final JCTree.JCMethodInvocation invocation = maker.Apply(List.nil(), maker.Ident(invokeAccess), isStatic ? args : args.prepend(maker.Ident(new Symbol.VarSymbol(FINAL | SYNTHETIC, names._this, symbol.owner.type, symbol.owner))));
                final Type returnType = accessType.restype;
                statements.head = returnType instanceof Type.JCVoidType ? maker.Exec(invocation) : maker.Return(invocation);
                statements.head.type = invocation.type = returnType;
                statements.tail = List.nil();
            }
        }
    }
    
    public static String mapMethodName(final String name) = STR."$access$\{name.replace("<", "$_").replace(">", "_$")}";
    
    @Hook(at = @At(var = @At.VarInsn(opcode = ILOAD, var = 4), ordinal = 0))
    private static Hook.Result access(final Lower $this, final Symbol sym, final JCTree.JCExpression expression, final JCTree.JCExpression enclOp, final boolean refSuper)
            = inPrivilegeContext(HandlerSupport.lowerContext()) ? new Hook.Result(expression) : Hook.Result.VOID;
    
    @Hook
    private static Hook.Result accessConstructor(final Lower $this, final JCDiagnostic.DiagnosticPosition pos, final Symbol constructor) {
        final PrivilegeHandler instance = instance(PrivilegeHandler.class);
        if (pos instanceof JCTree.JCMethodInvocation && !instance.isAccessible((Privilege) $this.currentClass, constructor.owner.type, constructor) || constructor.baseSymbol().name == instance._new)
            instance.makeIndyQualifier((JCTree.JCExpression) HandlerSupport.lowerContext()[-1], constructor);
        return Hook.Result.VOID;
    }
    
    // # AccessibleHandler#isAccessible
    public boolean isAccessible(final Symbol.ClassSymbol context, final Type site, final Symbol target, final boolean checkInner = false)
    = target.name == names._this || target instanceof Symbol.ClassSymbol || (target.name != names.init || target.owner == site.tsym) && switch ((short) (target.flags() & AccessFlags)) {
        case PRIVATE   -> (context == target.owner || context.outermostClass() == target.owner.outermostClass()) && target.isInheritedIn(site.tsym, types);
        case PROTECTED -> context.packge() == target.packge() || isProtectedAccessible(target, context, site) || notOverriddenIn(site, target);
        case 0         -> context.packge() == target.packge() && target.isInheritedIn(site.tsym, types) && notOverriddenIn(site, target);
        default        -> notOverriddenIn(site, target);
    };
    
    protected boolean notOverriddenIn(final Type site, final Symbol symbol) {
        if (symbol.kind != MTH || symbol.isConstructor() || symbol.isStatic())
            return true;
        else {
            final @Nullable Symbol implementation = ((Symbol.MethodSymbol) symbol).implementation(site.tsym, types, true);
            return implementation == null || implementation.owner == symbol.owner || !types.isSubSignature(types.memberType(site, implementation), types.memberType(site, symbol));
        }
    }
    
    private boolean isProtectedAccessible(final Symbol target, final Symbol.ClassSymbol context, final Type site) {
        final Type upperSite = site.hasTag(TYPEVAR) ? site.getUpperBound() : site;
        @Nullable Symbol.ClassSymbol c = context;
        while (c != null && !(c.isSubClass(target.owner, types) && (c.flags() & INTERFACE) == 0 && ((target.flags() & STATIC) != 0 || target.kind == TYP || upperSite.tsym.isSubClass(c, types))))
            c = c.owner.enclClass();
        return c != null;
    }
    
    private JCTree.JCExpression makeIndyQualifier(final JCTree.JCExpression expression, final Symbol target, final int modifiers = 0,
            final boolean outerStatement = HandlerSupport.lowerContext().size() > 2 && HandlerSupport.lowerContext()[-3] instanceof JCTree.JCExpressionStatement) {
        final Symbol.MethodSymbol bsm = lookupDynamicLookupMethod(makePrivilegeSite);
        final PoolConstant.LoadableConstant className = PoolConstant.LoadableConstant.String(target.enclClass().flatName().toString());
        final boolean isStatic = anyMatch(target.flags(), STATIC);
        if (target instanceof Symbol.MethodSymbol methodSymbol) {
            List<Type> argTypes = methodSymbol.type.asMethodType().argtypes;
            if (expression instanceof JCTree.JCNewClass && methodSymbol.name == names.init && methodSymbol.owner.hasOuterInstance())
                argTypes = argTypes.prepend(methodSymbol.owner.type.getEnclosingType());
            if (!isStatic && !(expression instanceof JCTree.JCNewClass))
                argTypes = argTypes.prepend(methodSymbol.owner.type);
            final Type returnType = expression instanceof JCTree.JCNewClass ? methodSymbol.owner.type : methodSymbol.type.asMethodType().restype;
            final PoolConstant.LoadableConstant opcode = PoolConstant.LoadableConstant.Int(modifiers & ~0xFF | ((modifiers & SPECIAL) != 0 && target instanceof Symbol.MethodSymbol ?
                    INVOKESPECIAL : expression instanceof JCTree.JCNewClass ? NEW : opcode(target)));
            final PoolConstant.LoadableConstant constants[] = { opcode, className };
            final PrivilegeMethodSymbol dynamicMethodSymbol = {
                    methodSymbol.name == names.init ? _new : methodSymbol.name, symtab.noSymbol, bsm.asHandle(), new Type.MethodType(
                    types.erasure(argTypes), types.erasure(returnType), List.nil(), symtab.methodClass), constants
            };
            if (noneMatch(target.flags(), STATIC))
                if (expression instanceof JCTree.JCMethodInvocation invocation)
                    if (invocation.meth instanceof JCTree.JCFieldAccess access)
                        invocation.args = invocation.args.prepend(access.selected);
                    else
                        invocation.args = invocation.args.prepend(maker.Ident(names._this).let(ident -> ident.sym = target.owner).let(ident -> ident.type = target.owner.type));
                else if (expression instanceof JCTree.JCNewClass newClass && newClass.encl != null)
                    newClass.args = newClass.args.prepend(newClass.encl);
            if (expression instanceof JCTree.JCNewClass newClass) {
                dynamicMethodSymbol.owner = newClass.constructor.owner;
                final JCTree.JCMethodInvocation invocation = maker.Apply(List.nil(), maker.Ident(dynamicMethodSymbol), newClass.args);
                invocation.type = dynamicMethodSymbol.getReturnType();
                throw new ReLowException(invocation, newClass);
            } else if (expression instanceof JCTree.JCMethodInvocation invocation)
                if (target.name == names.init || target.name == _new) {
                    dynamicMethodSymbol.owner = target.owner;
                    final JCTree.JCMethodInvocation dynamicInvocation = maker.Apply(List.nil(), maker.Ident(dynamicMethodSymbol), invocation.args);
                    dynamicInvocation.type = dynamicMethodSymbol.type.asMethodType().restype = symtab.voidType;
                    final @Nullable JCTree.JCMethodDecl method = HandlerSupport.lowerContext().descendingStream().cast(JCTree.JCMethodDecl.class).findFirst().orElse(null);
                    if (method != null && method.name == names.init)
                        method.name = method.sym.name = _new;
                    throw new ReLowException(dynamicInvocation, invocation);
                } else {
                    dynamicMethodSymbol.owner = target.owner;
                    invocation.meth = maker.Ident(dynamicMethodSymbol);
                    return expression;
                }
        } else if (target instanceof Symbol.VarSymbol varSymbol) {
            final boolean setter = expression instanceof JCTree.JCAssign || expression instanceof JCTree.JCAssignOp;
            final PrivilegeMethodSymbol dynamicMethodSymbol = {
                    varSymbol.name, symtab.noSymbol, bsm.asHandle(), new Type.MethodType(
                    types.erasure(isStatic ? setter ? List.of(varSymbol.type) : List.nil() : setter ? List.of(varSymbol.owner.type, varSymbol.type) : List.of(varSymbol.owner.type)),
                    setter && outerStatement ? symtab.voidType : types.erasure(varSymbol.type), List.nil(), symtab.methodClass), new PoolConstant.LoadableConstant[]{ PoolConstant.LoadableConstant.Int(opcode(target, setter)), className }
            };
            if (setter) {
                if (expression instanceof JCTree.JCAssign assign) {
                    final JCTree.JCMethodInvocation apply = maker.Apply(List.nil(), maker.Ident(dynamicMethodSymbol), isStatic ? List.of(assign.rhs) : List.of(fieldOwner(assign.lhs, varSymbol.owner), assign.rhs));
                    apply.type = dynamicMethodSymbol.getReturnType();
                    throw new ReLowException(apply, assign);
                } else if (expression instanceof JCTree.JCAssignOp assignOp) {
                    final PrivilegeMethodSymbol getterDynamicMethodSymbol = {
                            target.name, symtab.noSymbol, bsm.asHandle(), new Type.MethodType(types.erasure(isStatic ? List.nil() : List.of(varSymbol.owner.type)), types.erasure(varSymbol.type),
                            List.nil(), symtab.methodClass), new PoolConstant.LoadableConstant[]{ PoolConstant.LoadableConstant.Int(opcode(target)), className }
                    };
                    final JCTree.JCMethodInvocation getApply = maker.Apply(List.nil(), maker.Ident(getterDynamicMethodSymbol), isStatic ? List.nil() : List.of(fieldOwner(assignOp.lhs, varSymbol.owner)));
                    getApply.type = getterDynamicMethodSymbol.getReturnType();
                    final JCTree.JCBinary rhs = maker.Binary(assignOp.getTag(), getApply, assignOp.rhs);
                    rhs.type = assignOp.type;
                    rhs.operator = assignOp.operator;
                    final JCTree.JCMethodInvocation apply = maker.Apply(List.nil(), maker.Ident(dynamicMethodSymbol), isStatic ? List.of(rhs) : List.of(fieldOwner(assignOp.lhs, varSymbol.owner), rhs));
                    apply.type = dynamicMethodSymbol.getReturnType();
                    throw new ReLowException(apply, assignOp);
                }
            } else {
                final JCTree.JCMethodInvocation apply = maker.Apply(List.nil(), maker.Ident(dynamicMethodSymbol), isStatic ? List.nil() : List.of(fieldOwner(expression, varSymbol.owner)));
                apply.type = dynamicMethodSymbol.getReturnType();
                throw new ReLowException(apply, expression);
            }
        }
        throw new AssertionError();
    }
    
    protected JCTree.JCExpression fieldOwner(final @Nullable JCTree tree, final Symbol ownerSymbol)
            = tree instanceof JCTree.JCFieldAccess access ? access.selected : maker.Ident(names._this).let(it -> it.sym = ownerSymbol).let(it -> it.type = ownerSymbol.type);
    
    public static int opcode(final Symbol symbol, final boolean getter = false)
            = symbol instanceof Symbol.MethodSymbol ?
            anyMatch(symbol.flags(), STATIC) ? INVOKESTATIC : anyMatch(symbol.owner.flags(), INTERFACE) ? INVOKEINTERFACE : anyMatch(symbol.flags(), PRIVATE) ? INVOKESPECIAL : INVOKEVIRTUAL :
            symbol instanceof Symbol.VarSymbol ? anyMatch(symbol.flags(), STATIC) ? getter ? PUTSTATIC : GETSTATIC : getter ? PUTFIELD : GETFIELD : -1;
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static Symbol findDiamond(final Symbol capture, final Resolve $this, final Env<AttrContext> env, final Type site, final List<Type> argTypes, final List<Type> typeArgTypes,
            final boolean allowBoxing, final boolean useVarargs) = findNew(capture, $this, env, site, argTypes, typeArgTypes, allowBoxing, useVarargs);
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true, metadata = @TransformMetadata(order = 1 << 10))
    private static Symbol findMethod(final Symbol capture, final Resolve $this, final Env<AttrContext> env, final Type site, final Name name, final List<Type> argTypes, final List<Type> typeArgTypes,
            final boolean allowBoxing, final boolean useVarargs) = name.table.names.init == name ? findNew(capture, $this, env, site, argTypes, typeArgTypes, allowBoxing, useVarargs) : capture;
    
    private static Symbol findNew(final Symbol capture, final Resolve $this, final Env<AttrContext> env, final Type site, final List<Type> argTypes, final List<Type> typeArgTypes, final boolean allowBoxing, final boolean useVarargs) {
        if (capture.kind == MTH)
            return capture;
        final PrivilegeHandler handler = instance(PrivilegeHandler.class);
        Symbol bestSoFar = capture;
        final Symbol.TypeSymbol target = site.tsym.isInterface() ? handler.symtab.objectType.tsym : site.tsym;
        for (final Symbol sym : target.members().getSymbolsByName(handler._new, Scope.LookupKind.NON_RECURSIVE))
            if (sym.kind == MTH && (sym.flags_field & SYNTHETIC) == 0)
                bestSoFar = (Privilege) $this.selectBest(env, site, argTypes, typeArgTypes,
                        new Symbol.MethodSymbol(sym.flags(), handler._new, new Type.ForAll(site.tsym.type.getTypeArguments().appendList(sym.type instanceof Type.ForAll forAll ? forAll.tvars : List.nil()),
                                handler.types.createMethodTypeWithReturn(sym.type.asMethodType(), site)), site.tsym) {
                            @Override
                            public Symbol baseSymbol() = sym;
                        }, bestSoFar, allowBoxing, useVarargs);
        return bestSoFar;
    }
    
    @Hook(at = @At(method = @At.MethodInsn(name = "isAccessible")), before = false, capture = true, branchReversal = true)
    private static boolean cast(final boolean capture, final TransTypes $this, final JCTree.JCExpression tree, final Type target) = true;
    
    @Hook(value = TreeInfo.class, isStatic = true, at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static boolean isExpressionStatement(final boolean capture, final JCTree.JCExpression tree) = capture || tree instanceof JCTree.JCTypeCast;
    
    @Hook
    private static Hook.Result visitTree(final ArgumentAttr $this, final JCTree that) {
        if (that instanceof JCTree.JCTypeCast cast) {
            final PrivilegeHandler handler = instance(PrivilegeHandler.class);
            final Env<AttrContext> env = (Privilege) $this.env;
            if (((Privilege) $this.attr).attribType(cast.clazz, env).tsym.flatName() == handler.Privilege) {
                (Privilege) $this.processArg(cast, speculativeTree -> new TypeCastParensType($this, cast, env, speculativeTree));
                return Hook.Result.NULL;
            } else if (cast.clazz.toString().contains("Privilege"))
                DebugHelper.breakpoint();
        }
        return Hook.Result.VOID;
    }
    
    @Hook(at = @At(method = @At.MethodInsn(name = "attribType"), ordinal = 0), before = false, capture = true)
    private static Hook.Result visitTypeCast(final Type capture, final Attr $this, final JCTree.JCTypeCast tree) {
        final PrivilegeHandler handler = instance(PrivilegeHandler.class);
        if (capture.tsym.flatName() == handler.Privilege) {
            (Privilege) ($this.result = (Privilege) $this.check(tree, (Privilege) $this.attribTree(tree.expr, ((Privilege) $this.env).dup(tree), (Privilege) $this.resultInfo), Kinds.KindSelector.VAL, (Privilege) $this.resultInfo));
            return Hook.Result.NULL;
        }else if (tree.clazz.toString().contains("Privilege"))
            DebugHelper.breakpoint();
        return Hook.Result.VOID;
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)))
    private static void visitExec(final Attr $this, final JCTree.JCExpressionStatement tree) {
        if (tree.expr instanceof JCTree.JCTypeCast cast && cast.clazz.type.tsym.flatName() != instance(PrivilegeHandler.class).Privilege)
            instance(PrivilegeHandler.class).log.error(JCDiagnostic.DiagnosticFlag.SYNTAX, tree, CompilerProperties.Errors.NotStmt);
    }
    
    @Hook
    private static void visitTypeCast(final Lower $this, final JCTree.JCTypeCast tree) {
        final PrivilegeHandler handler = instance(PrivilegeHandler.class);
        final Type type = tree.clazz.type;
        if (type.tsym.flatName() == handler.Privilege) {
            final JCTree.JCExpression result;
            try {
                final JCTree.JCExpression parent = TreeInfo.skipParens($this.translate(tree.expr));
                final LinkedList<JCTree> trees = HandlerSupport.lowerContext();
                int modifiers = 0;
                if (hasAnnotation(type, WeakLinking.class))
                    modifiers |= WEAK_LINKING;
                if (hasAnnotation(type, Constant.class))
                    modifiers |= CONSTANT;
                if (hasAnnotation(type, Special.class))
                    modifiers |= SPECIAL;
                result = handler.makeIndyQualifier(parent, symbol(switch (parent) {
                    case JCTree.JCAssign it           -> it.lhs;
                    case JCTree.JCAssignOp it         -> it.lhs;
                    case JCTree.JCMethodInvocation it -> it.meth;
                    default                           -> parent;
                }), modifiers, trees.size() > 1 && trees[-2] instanceof JCTree.JCExpressionStatement);
            } catch (final ReLowException e) {
                e.breakTree = tree;
                throw e;
            }
            throw new ReLowException(result, tree);
        }
    }
    
}
