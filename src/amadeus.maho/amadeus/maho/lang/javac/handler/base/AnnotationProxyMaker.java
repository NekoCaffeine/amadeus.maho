package amadeus.maho.lang.javac.handler.base;

import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.MirroredTypesException;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.resources.CompilerProperties;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;

import amadeus.maho.lang.Extension;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.javac.JavacContext;
import amadeus.maho.lang.javac.MahoJavac;
import amadeus.maho.util.annotation.AnnotationHandler;
import amadeus.maho.util.runtime.TypeHelper;
import amadeus.maho.util.tuple.Tuple;
import amadeus.maho.util.tuple.Tuple2;

import static amadeus.maho.util.runtime.ReflectionHelper.*;

public class AnnotationProxyMaker {
    
    @Extension
    public interface Ext {
        
        static <A extends Annotation> @Nullable Type accessJavacType(final A annotation, final Function<A, Class<?>> accessor) = tryAccessJavacTypes(annotation, accessor).findFirst().orElse(null);
        
        static <A extends Annotation> Stream<? extends Type> accessJavacTypes(final A annotation, final Function<A, Class<?>[]> accessor) = tryAccessJavacTypes(annotation, accessor);
        
        private static <A extends Annotation> Stream<? extends Type> tryAccessJavacTypes(final A annotation, final Function<A, ?> accessor) {
            try {
                accessor.apply(annotation);
                return Stream.empty();
            } catch (final MirroredTypeException e) {
                return Stream.of(e.getTypeMirror()).cast(Type.class);
            } catch (final MirroredTypesException e) {
                return e.getTypeMirrors().stream().cast(Type.class);
            }
        }
        
    }
    
    @RequiredArgsConstructor
    public static class Evaluator extends JCTree.Visitor {
        
        final JavacContext context;
        
        final Env<AttrContext> env;
        
        Class<?> expectedType;
        
        boolean error;
        
        @Nullable Object result;
        
        @Override
        public void visitTree(final JCTree that) = error(that, CompilerProperties.Errors.ExpressionNotAllowableAsAnnotationValue);
        
        @Override
        public void visitAnnotation(final JCTree.JCAnnotation that) {
            final Type type = context.attr.attribType(that.annotationType, env);
            if (!type.isErroneous())
                try {
                    result(that, make(that, (Class<? extends Annotation>) Class.forName(type.tsym.getQualifiedName().toString()), this));
                } catch (final ClassNotFoundException e) { error(that.annotationType, new JCDiagnostic.Error(MahoJavac.KEY, "runtime.missing.type", type)); }
        }
        
        @Override
        public void visitNewArray(final JCTree.JCNewArray that) {
            if (that.type != null)
                error(that.elemtype, CompilerProperties.Errors.NewNotAllowedInAnnotation);
            else {
                final Class<?> expectedType = this.expectedType;
                final Class<?> componentType = expectedType.getComponentType();
                try {
                    expectedType(componentType);
                    final Object array[] = that.elems.stream()
                            .map(element -> let(element::accept).result())
                            .toArray(TypeHelper.arrayConstructor(componentType));
                    expectedType(expectedType).result(that, array);
                } finally { expectedType(expectedType); }
            }
        }
        
        @Override
        public void visitIdent(final JCTree.JCIdent that) {
            if (expectedType.isEnum() || expectedType.getComponentType()?.isEnum() ?? false)
                try {
                    result(that, Enum.valueOf((Class<? extends Enum>) (expectedType.isEnum() ? expectedType : expectedType.getComponentType()), that.name.toString()));
                } catch (final IllegalArgumentException ignored) { context.log.error(that, new JCDiagnostic.Error(MahoJavac.KEY, "doesnt.exist", that)); }
            else
                error(that, CompilerProperties.Errors.AttributeValueMustBeConstant);
        }
        
        @Override
        public void visitSelect(final JCTree.JCFieldAccess that) {
            if (expectedType == Type.class || expectedType.getComponentType() == Type.class) {
                final Type type = context.attr.attribType(that.selected, env);
                if (!type.isErroneous())
                    if (TreeInfo.name(that) != context.names._class)
                        error(that, CompilerProperties.Errors.AnnotationValueMustBeClassLiteral);
                    else
                        result(that, type);
            } else if (expectedType.isEnum() || expectedType.getComponentType().isEnum()) {
                final Type type = context.attr.attribType(that.selected, env);
                if (!type.tsym.getQualifiedName().toString().equals(expectedType.getCanonicalName()))
                    error(that.selected, new JCDiagnostic.Error("compiler", "type.found.req", expectedType.getCanonicalName(), type.tsym.getQualifiedName()));
                else
                    try {
                        result(that, Enum.valueOf((Class<? extends Enum>) (expectedType.isEnum() ? expectedType : expectedType.getComponentType()), that.name.toString()));
                    } catch (final IllegalArgumentException ignored) { context.log.error(that.selected, new JCDiagnostic.Error(MahoJavac.KEY, "doesnt.exist", that)); }
            } else
                error(that, CompilerProperties.Errors.AttributeValueMustBeConstant);
        }
        
        @Override
        public void visitLiteral(final JCTree.JCLiteral that) = result(that, switch (that.typetag) {
            case BOOLEAN -> ((Number) that.value).intValue() != 0;
            case CHAR    -> (char) ((Number) that.value).intValue();
            default      -> that.value;
        });
        
        @Override
        public void visitUnary(final JCTree.JCUnary that) {
            that.arg.accept(this);
            final @Nullable Object arg = result();
            if (arg != null)
                result = context.fold(that.operator.opcode, context.symtab.objectType.constType(arg)).constValue();
        }
        
        @Override
        public void visitBinary(final JCTree.JCBinary that) {
            that.lhs.accept(this);
            final @Nullable Object lhs = result();
            that.rhs.accept(this);
            final @Nullable Object rhs = result();
            if (lhs != null && rhs != null)
                result = context.fold(that.operator.opcode, context.symtab.objectType.constType(lhs), context.symtab.objectType.constType(rhs)).constValue();
        }
        
        public void error(final JCTree tree, final JCDiagnostic.Error errorKey) {
            error = true;
            context.log.error(JCDiagnostic.DiagnosticFlag.RESOLVE_ERROR, tree, errorKey);
        }
        
        public self checkError() {
            if (error) {
                error = false;
                throw new IllegalStateException();
            }
        }
        
        public self expectedType(final Class<?> expectedType) = this.expectedType = expectedType == Class.class ? Type.class : expectedType == Class[].class ? Type[].class : expectedType;
        
        public void result(final JCTree tree, final @Nullable Object result) {
            if (result == null)
                error(tree, new JCDiagnostic.Error(MahoJavac.KEY, "inconvertible.types", "<null>", expectedType.getCanonicalName()));
            else {
                final Class<?> resultClass = TypeHelper.unboxType(result.getClass());
                if (!expectedType.isAssignableFrom(resultClass))
                    if (expectedType.isArray() && expectedType.getComponentType().isAssignableFrom(resultClass))
                        Array.set(this.result = Array.newInstance(expectedType.getComponentType(), 1), 0, result);
                    else
                        error(tree, new JCDiagnostic.Error(MahoJavac.KEY, "inconvertible.types", resultClass.getCanonicalName(), expectedType.getCanonicalName()));
                else
                    this.result = result;
            }
        }
        
        public @Nullable Object result() { try { return result; } finally { result = null; } }
        
    }
    
    public static <A extends Annotation> @Nullable A make(final JCTree.JCAnnotation annotation, final Class<A> annotationType, final Evaluator evaluator) {
        try {
            final boolean p_error[] = { false };
            final Map<String, Object> memberValues = Stream.of(annotationType.getMethods())
                    .filter(method -> !(method.getDeclaringClass() == Object.class || method.getDeclaringClass() == Annotation.class))
                    .filter(anyMatch(ABSTRACT))
                    .filter(method -> method.getParameterCount() == 0 && !(method.getReturnType() == Annotation.class || method.getReturnType() == Annotation[].class))
                    .map(method -> lookupValue(annotation, method, evaluator, p_error))
                    .nonnull()
                    .collect(Collectors.toMap(Tuple2::v1, Tuple2::v2));
            return p_error[0] ? null : AnnotationHandler.make(annotationType, AnnotationProxyMaker.class.getClassLoader(), memberValues);
        } catch (final IllegalStateException e) { return null; }
    }
    
    public static @Nullable JCTree.JCExpression lookupValue(final JCTree.JCAnnotation annotation, final String name) = annotation.args.stream()
            .filter(value -> value instanceof JCTree.JCAssign assign ? assign.lhs instanceof JCTree.JCIdent ident && ident.name.toString().equals(name) : name.equals("value"))
            .findFirst()
            .map(value -> value instanceof JCTree.JCAssign assign ? assign.rhs : value)
            .orElse(null);
    
    public static @Nullable Tuple2<String, Object> lookupValue(final JCTree.JCAnnotation annotation, final Method method, final Evaluator evaluator, final boolean[] p_error) {
        final @Nullable JCTree.JCExpression expression = lookupValue(annotation, method.getName());
        if (expression == null) {
            if (method.getDefaultValue() == null && method.getDeclaringClass() != Repeatable.class) {
                final JavacContext instance = JavacContext.instance();
                instance.log.error(JCDiagnostic.DiagnosticFlag.RESOLVE_ERROR, annotation, CompilerProperties.Errors.AnnotationMissingDefaultValue1(annotation.type, List.of(instance.name(method.getName()))));
                p_error[0] = true;
            }
            return null;
        }
        final Class<?> returnType = method.getReturnType();
        expression.accept(evaluator.expectedType(returnType));
        final Object result = evaluator.checkError().result();
        return Tuple.tuple(method.getName(), returnType == Class.class ? (Supplier<Object>) () -> { throw new MirroredTypeException((Type) result); } :
                returnType == Class[].class ? (Supplier<Object>) () -> { throw new MirroredTypesException(java.util.List.of((Type[]) result)); } : result);
    }
    
}
