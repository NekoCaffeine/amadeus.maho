package amadeus.maho.lang.javac.handler;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.javac.JavacContext;
import amadeus.maho.lang.javac.MahoJavac;
import amadeus.maho.lang.javac.handler.base.BaseHandler;
import amadeus.maho.lang.javac.handler.base.Handler;
import amadeus.maho.lang.javac.handler.base.HandlerSupport;
import amadeus.maho.lang.reference.Mutable;
import amadeus.maho.lang.reference.Observable;
import amadeus.maho.lang.reference.Overwritable;
import amadeus.maho.lang.reference.Puppet;
import amadeus.maho.lang.reference.Readable;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.dynamic.LookupHelper;
import amadeus.maho.util.reference.Reference;
import amadeus.maho.util.runtime.DebugHelper;
import amadeus.maho.util.tuple.Tuple2;

import static com.sun.tools.javac.code.Flags.*;

@NoArgsConstructor
@TransformProvider
public abstract class ReferenceHandler<A extends Annotation> extends BaseHandler<A> {
    
    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.SOURCE)
    public @interface Mark { }
    
    public static final int PRIORITY = FieldDefaultsHandler.PRIORITY >> 2;
    
    @NoArgsConstructor
    @Handler(value = Readable.class, ranges = Handler.Range.FIELD, priority = PRIORITY)
    public static class ReadableHandler extends ReferenceHandler<Readable> { }
    
    @NoArgsConstructor
    @Handler(value = Mutable.class, ranges = Handler.Range.FIELD, priority = PRIORITY)
    public static class MutableHandler extends ReferenceHandler<Mutable> { }
    
    @NoArgsConstructor
    @Handler(value = Observable.class, ranges = Handler.Range.FIELD, priority = PRIORITY)
    public static class ObservableHandler extends ReferenceHandler<Observable> { }
    
    @NoArgsConstructor
    @Handler(value = Overwritable.class, ranges = Handler.Range.FIELD, priority = PRIORITY)
    public static class OverwritableHandler extends ReferenceHandler<Overwritable> { }
    
    @NoArgsConstructor
    @Handler(value = Puppet.class, ranges = Handler.Range.FIELD, priority = PRIORITY)
    public static class PuppetHandler extends ReferenceHandler<Puppet> { }
    
    public static String
            provider[] = Reference.class.getCanonicalName().split("\\."),
            mark[]     = Mark.class.getCanonicalName().split("\\."),
            of         = LookupHelper.<Class<?>, Object>method1(Reference::of).getName(),
            ofHandle   = LookupHelper.<Class<?>, Object>method1(Reference::ofHandle).getName(),
            invoke     = "invoke";// LookupHelper.<MethodHandle, Object[], Object>method2(MethodHandle::invoke).getName();
    
    public static final java.util.List<Class<? extends Annotation>> references = Stream.of(ReferenceHandler.class.getDeclaredClasses())
            .map(clazz -> clazz.getAnnotation(Handler.class))
            .nonnull()
            .map(Handler::value)
            .collect(Collectors.toCollection(ArrayList::new));
    
    public final String referenceTypeName[] = handler().value().getCanonicalName().replace(Readable.class.getPackageName(), Reference.class.getPackageName()).split("\\.");
    
    @Override
    public void processVariable(final Env<AttrContext> env, final JCTree.JCVariableDecl tree, final JCTree owner, final A annotation, final JCTree.JCAnnotation annotationTree, final boolean advance) {
        checkMarkNesting(tree.mods, env);
        tree.mods = maker.at(tree.mods.pos).Modifiers(tree.mods.flags, tree.mods.annotations.stream().filter(it -> it != annotationTree).collect(List.collector()));
        maker.at(tree.pos);
        final JCTree.JCExpression varType = tree.vartype;
        tree.type = tree.sym.type = attr.attribType(tree.vartype = referenceType(tree.vartype, tree.pos(), env, tree.sym, referenceTypeName), env);
        final boolean isParameter;
        if (owner instanceof JCTree.JCMethodDecl method && anyMatch(tree.mods.flags, PARAMETER)) {
            final Type.MethodType methodType = (Type.MethodType) method.sym.type;
            final int index = method.params.indexOf(tree), p_index[] = { 0 };
            methodType.argtypes = methodType.argtypes.map(type -> p_index[0]++ == index ? tree.sym.type : type);
            isParameter = true;
        } else
            isParameter = false;
        tree.mods.flags |= FINAL;
        tree.sym.flags_field |= FINAL;
        if (!isParameter || tree.init != null)
            transformInit(env, tree, varType, false);
    }
    
    public void transformInit(final Env<AttrContext> env, final JCTree.JCVariableDecl tree, final JCTree.JCExpression varType, final boolean inMethod) {
        if (tree.init != null || !inMethod) {
            final JCTree.JCExpression classArg = maker.Select(tree.vartype instanceof JCTree.JCTypeApply typeApply ? typeApply.clazz : tree.vartype, names._class);
            final String add = anyMatch(tree.mods.flags, VOLATILE) ? "Volatile" : "";
            tree.mods.flags &= ~VOLATILE;
            tree.sym.flags_field &= ~VOLATILE;
            if (tree.init == null)
                tree.init = maker.Apply(List.nil(), maker.Select(IdentQualifiedName(provider), name(of + add)), List.of(classArg));
            else {
                final Type type = ((Type.ClassType) tree.type).typarams_field.head;
                if (tree.init instanceof JCTree.JCNewArray newArray && newArray.elemtype == null)
                    if (type instanceof Type.ArrayType arrayType)
                        newArray.elemtype = maker.Type(arrayType.elemtype);
                    else
                        tree.init = maker.NewClass(null, List.nil(), varType, newArray.elems, null);
                if (requiresTypeDeclaration(tree.init))
                    tree.init = maker.TypeCast(maker.Type(type), tree.init);
                tree.init = maker.TypeCast(tree.type, maker.Apply(List.nil(), maker.Select(maker.Apply(List.nil(), maker.Select(IdentQualifiedName(provider), name(ofHandle + add)), List.of(classArg)), name(invoke)), List.of(tree.init)));
            }
            tree.init.type = null;
            if (inMethod && noneMatch(tree.mods.flags, PARAMETER))
                attr.attribExpr(tree.init, env);
        }
    }
    
    @Override
    public void processMethod(final Env<AttrContext> env, final JCTree.JCMethodDecl tree, final JCTree owner, final A annotation, final JCTree.JCAnnotation annotationTree, final boolean advance) {
        checkMarkNesting(tree.mods, env);
        removeAnnotation(tree, annotationTree);
        final java.util.List<Tuple2<Getter, JCTree.JCAnnotation>> annotations = marker.getAnnotationsByTypeWithOuter(tree.mods, env, tree, Getter.class);
        if (!annotations.isEmpty()) {
            final Tuple2<Getter, JCTree.JCAnnotation> getter = annotations[0];
            boolean error = getter.v1.lazy();
            if (!error)
                error |= !tree.params.isEmpty();
            if (!error)
                error |= owner instanceof JCTree.JCClassDecl decl && noneMatch(decl.mods.flags, INTERFACE);
            if (!error)
                error |= anyMatch(tree.mods.flags, STATIC);
            if (!error) {
                final Name referenceGetterName = tree.sym.name.append(name(GetterHandler.REFERENCE_GETTER));
                if (shouldInjectMethod(env, referenceGetterName))
                    injectMember(env, maker.MethodDef(maker.Modifiers(0L), referenceGetterName, referenceType(tree.restype, tree.pos(), env, tree.sym, referenceTypeName), List.nil(), List.nil(), List.nil(), null, null).let(
                            it -> followAnnotation(getter.v2, GetterHandler.ON_REFERENCE_GETTER, it.mods)).let(it -> followAnnotationWithoutNullable(env, tree.mods, it.mods)));
            }
        } else
            ((Type.MethodType) tree.sym.type).restype = attr.attribType(tree.restype = referenceType(tree.restype, tree.pos(), env, tree.sym, referenceTypeName), env);
    }
    
    protected JCTree.JCExpression referenceType(final JCTree.JCExpression varType, final JCDiagnostic.DiagnosticPosition position, final Env<AttrContext> env, final Symbol symbol, final String referenceTypeName[])
            = maker.AnnotatedType(
                    List.of(new MarkAnnotation(JCTree.Tag.TYPE_ANNOTATION, IdentQualifiedName(mark), List.nil()).let(it -> it.pos = maker.pos)),
                    varType instanceof JCTree.JCPrimitiveTypeTree ?
                            maker.Select(IdentQualifiedName(referenceTypeName), name(varType.toString().upper(0))) :
                            maker.TypeApply(IdentQualifiedName(referenceTypeName), List.of(varType)))
            .let(it -> annotate.queueScanTreeAndTypeAnnotate(it, env, symbol, position));
    
    public void checkMarkNesting(final JCTree.JCModifiers owner, final Env<AttrContext> env) {
        if (findReferences(marker, owner, env) > 1)
            log.error(JCDiagnostic.DiagnosticFlag.RESOLVE_ERROR, owner, new JCDiagnostic.Error(MahoJavac.KEY, "reference.mark.nesting"));
    }
    
    public static long findReferences(final HandlerSupport marker, final JCTree.JCModifiers modifiers, final Env<AttrContext> env) = references.stream()
            .map(annotationType -> marker.getAnnotationsByTypeWithOuter(modifiers, env, modifiers, annotationType))
            .mapToInt(java.util.List::size)
            .sum();
    
    public static JCTree.JCExpression unpackedType(final JCTree.JCExpression varType, final JavacContext context) {
        if (varType instanceof JCTree.JCAnnotatedType annotatedType)
            for (final JCTree.JCAnnotation annotation : annotatedType.annotations)
                if (annotation.annotationType.toString().equals(Mark.class.getCanonicalName()))
                    return annotatedType.underlyingType instanceof JCTree.JCTypeApply typeApply ? typeApply.arguments.head :
                            annotatedType.underlyingType instanceof JCTree.JCFieldAccess access ? typeOf(access.name.toString(), context) :
                                    DebugHelper.breakpointThenError();
        return varType;
    }
    
    private static JCTree.JCExpression typeOf(final String name, final JavacContext context)
            = context.maker.TypeIdent(TypeTag.valueOf(name.toUpperCase(Locale.ENGLISH))).let(it -> it.type = context.symtab.typeOfTag[it.typetag.ordinal()]);
    
}
