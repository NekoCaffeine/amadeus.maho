package amadeus.maho.lang.javac.handler;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.sun.source.tree.MemberReferenceTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.LambdaToMethod;
import com.sun.tools.javac.jvm.PoolConstant;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.Name;

import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.javac.JavacContext;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.dynamic.LookupHelper;
import amadeus.maho.util.runtime.ArrayHelper;

@TransformProvider
@NoArgsConstructor
public class LookupHandler extends JavacContext {
    
    public static Name nextName(final Env<AttrContext> env, final String name) {
        final LetHandler instance = instance(LetHandler.class);
        return instance.names.fromString("$lookup$%s$%d".formatted(name, instance.nextId(env)));
    }
    
    private static final Map<String, Class<?>> name2Class = Stream.of(Field.class, Method.class, Constructor.class, VarHandle.class, MethodHandle.class).collect(Collectors.toMap(Class::getCanonicalName, Function.identity()));
    
    @Hook
    private static Hook.Result visitApply(final LambdaToMethod.LambdaAnalyzerPreprocessor $this, final JCTree.JCMethodInvocation invocation) {
        if (invocation.args.size() == 1 && invocation.meth.type instanceof Type.MethodType methodType && LookupHelper.class.getCanonicalName().equals(symbol(invocation.meth).owner.getQualifiedName().toString())) {
            final LookupHandler instance = instance(LookupHandler.class);
            final Type returnType = methodType.restype;
            final @Nullable Class<?> target = name2Class[returnType.tsym.getQualifiedName().toString()];
            if (target != null) {
                final LambdaToMethod lambdaToMethod = Privilege.Outer.access($this);
                if (target == Field.class || target == VarHandle.class) {
                    if (invocation.args.head instanceof JCTree.JCLambda lambda) {
                        final @Nullable Symbol.VarSymbol targetSymbol;
                        if (lambda.body instanceof JCTree.JCExpression expression && symbol(expression) instanceof Symbol.VarSymbol varSymbol)
                            targetSymbol = varSymbol;
                        else if (lambda.body instanceof JCTree.JCBlock block && block.stats.head instanceof JCTree.JCReturn statement && symbol(statement.expr) instanceof Symbol.VarSymbol varSymbol)
                            targetSymbol = varSymbol;
                        else
                            targetSymbol = null;
                        if (targetSymbol != null) {
                            (Privilege) ($this.result = instance.maker.at(invocation.pos).Ident(instance.constant(invocation, (Privilege) lambdaToMethod.attrEnv, target == Field.class ? "field" : "varHandle", target, targetSymbol)));
                            return Hook.Result.NULL;
                        }
                    }
                }
                if (target == Method.class || target == Constructor.class || target == MethodHandle.class) {
                    if (invocation.args.head instanceof JCTree.JCMemberReference reference) {
                        (Privilege) ($this.result = instance.maker.at(invocation.pos).Ident(instance.constant(invocation, (Privilege) lambdaToMethod.attrEnv, target == Method.class ? "method" : target == Constructor.class ? "constructor" :
                                reference.mode == MemberReferenceTree.ReferenceMode.NEW ? "constructorHandle" : "methodHandle", target, reference.sym)));
                        return Hook.Result.NULL;
                    }
                }
            }
        }
        return Hook.Result.VOID;
    }
    
    public JavacContext.DynamicVarSymbol constant(final JCDiagnostic.DiagnosticPosition position, final Env<AttrContext> env, final String name, final Class<?> targetType, final Symbol symbol) {
        final PoolConstant.LoadableConstant constants[];
        if (symbol.name == symbol.name.table.names.init)
            constants = { lookupDynamicLookupMethod(name(name)).asHandle(), (Type.ClassType) symbol.owner.type };
        else
            constants = { lookupDynamicLookupMethod(name(name)).asHandle(), (Type.ClassType) symbol.owner.type, PoolConstant.LoadableConstant.String(symbol.name.toString()) };
        return { nextName(env, STR."\{symbol.owner.getQualifiedName().toString().replace('.', '_')}#\{symbol.name}"), symtab.noSymbol, constantInvokeBSM(position, env).asHandle(), symtab.enterClass(symtab.java_base, name(targetType)).type,
                 symbol instanceof Symbol.MethodSymbol methodSymbol ? ArrayHelper.addAll(constants, methodSymbol.type.asMethodType()) : constants };
    }
    
}
