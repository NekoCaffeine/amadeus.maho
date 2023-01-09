package amadeus.maho.lang.javac.handler;

import java.util.Arrays;
import java.util.function.Consumer;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;

import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.javac.handler.base.BaseHandler;
import amadeus.maho.lang.javac.handler.base.Handler;
import amadeus.maho.util.runtime.ArrayHelper;

import static com.sun.tools.javac.code.Flags.*;

public class EqualsAndHashCodeHandler {
    
    public static final int PRIORITY = ConstructorHandler.PRIORITY;
    
    public static class IdentityCompareBinary extends JCTree.JCBinary {
    
        public IdentityCompareBinary(final JCExpression lhs, final JCExpression rhs) = super(Tag.EQ, lhs, rhs, null);
        
    }
    
    @NoArgsConstructor
    @Handler(value = EqualsAndHashCode.class, priority = PRIORITY)
    public static class EqualsHandler extends BaseHandler<EqualsAndHashCode> {
        
        @Override
        public void processClass(final Env<AttrContext> env, final JCTree.JCClassDecl tree, final JCTree owner, final EqualsAndHashCode annotation, final JCTree.JCAnnotation annotationTree, final boolean advance) {
            if (shouldInjectMethod(env, names.equals, names.java_lang_Object)) {
                final Name target = name("target");
                injectMember(env, maker.MethodDef(maker.Modifiers(PUBLIC), names.equals, maker.Type(symtab.booleanType), List.nil(),
                                List.of(maker.VarDef(maker.Modifiers(FINAL | PARAMETER), target, maker.Type(symtab.objectType), null)), List.nil(), maker.Block(0L, statements(env, target, annotation)), null)
                        .let(it -> followAnnotation(annotationTree, "on", it.mods)));
            }
        }
        
        protected List<JCTree.JCStatement> statements(final Env<AttrContext> env, final Name target, final EqualsAndHashCode annotation) {
            final ListBuffer<JCTree.JCStatement> result = { };
            // if (this == target) return true;
            final IdentityCompareBinary binary = { maker.Ident(names._this), maker.Ident(target) };
            binary.pos = maker.pos;
            result.append(maker.If(binary, maker.Return(maker.Literal(true)), null));
            // if (!(o instanceof SelfType)) return false;
            result.append(maker.If(maker.Unary(JCTree.Tag.NOT, maker.Parens(maker.TypeTest(maker.Ident(target), maker.Ident(env.enclClass.sym)))), maker.Return(maker.Literal(false)), null));
            // if (!super.equals(target)) return false;
            if (annotation.callSuper())
                result.append(maker.If(maker.Unary(JCTree.Tag.NOT, maker.Apply(List.nil(), maker.Select(maker.Ident(names._super), names.equals), List.of(maker.Ident(target)))), maker.Return(maker.Literal(false)), null));
            final Name other = name("other");
            // SelfType other = (SelfType) target;
            result.append(maker.VarDef(maker.Modifiers(FINAL), other, maker.Ident(env.enclClass.sym), maker.TypeCast(env.enclClass.sym.type, maker.Ident(target))));
            final Consumer<JCTree.JCExpression> consumer = expr -> result.append(maker.If(expr, maker.Return(maker.Literal(false)), null));
            mapGetter(env, collectMember(env, JCTree.JCVariableDecl.class, EqualsAndHashCode.Mark.class, annotation.reverse(), decl -> noneMatch(decl.mods.flags, STATIC))).forEach(member -> {
                JCTree.JCExpression a = maker.Select(maker.Ident(names._this), name(member)), b = maker.Select(maker.Ident(other), name(member));
                if (member instanceof JCTree.JCMethodDecl) {
                    a = maker.Apply(List.nil(), a, List.nil()); // this.member()
                    b = maker.Apply(List.nil(), b, List.nil()); // target.member()
                }
                final Type type = symbol(member).type;
                if (type.isPrimitive())
                    consumer.accept(switch (type.getTag()) {
                        case FLOAT, DOUBLE -> maker.Binary(JCTree.Tag.NE, maker.Apply(List.nil(), maker.Select(maker.Ident(types.boxedClass(type)), name("compare")), List.of(a, b)), maker.Literal(0));
                        default            -> maker.Binary(JCTree.Tag.NE, a, b);
                    });
                else if (types.isArray(type))
                    consumer.accept(maker.Unary(JCTree.Tag.NOT, maker.Apply(List.nil(), maker.Select(IdentQualifiedName(Arrays.class), name(types.elemtype(type).isPrimitive() ? "equals" : "deepEquals")), List.of(a, b))));
                else
                    consumer.accept(maker.Unary(JCTree.Tag.NOT, maker.Apply(List.nil(), maker.Select(IdentQualifiedName(ArrayHelper.class), name("deepEquals")), List.of(a, b))));
            });
            return result.append(maker.Return(maker.Literal(true))).toList();
        }
        
    }
    
    @NoArgsConstructor
    @Handler(value = EqualsAndHashCode.class, priority = PRIORITY)
    public static class HashCodeHandler extends BaseHandler<EqualsAndHashCode> {
        
        @Override
        public void processClass(final Env<AttrContext> env, final JCTree.JCClassDecl tree, final JCTree owner, final EqualsAndHashCode annotation, final JCTree.JCAnnotation annotationTree, final boolean advance) {
            if (shouldInjectMethod(env, names.hashCode))
                injectMember(env, maker.MethodDef(maker.Modifiers(PUBLIC), names.hashCode, maker.Type(symtab.intType), List.nil(), List.nil(), List.nil(), maker.Block(0L, statements(env, annotation)), null)
                        .let(it -> followAnnotation(annotationTree, "on", it.mods)));
        }
        
        protected List<JCTree.JCStatement> statements(final Env<AttrContext> env, final EqualsAndHashCode annotation) {
            final Name hash = name("result");
            final ListBuffer<JCTree.JCStatement> result = { };
            result.append(maker.VarDef(maker.Modifiers(0L), hash, maker.Type(symtab.intType), annotation.callSuper() ? maker.Apply(List.nil(), maker.Select(maker.Ident(names._super), names.hashCode), List.nil()) : maker.Literal(1)));
            final Consumer<JCTree.JCExpression> consumer = expr -> result.append(maker.Exec(maker.Assign(maker.Ident(hash), maker.Binary(JCTree.Tag.PLUS, maker.Binary(JCTree.Tag.MUL, maker.Ident(hash), maker.Literal(31)), expr))));
            mapGetter(env, collectMember(env, JCTree.JCVariableDecl.class, EqualsAndHashCode.Mark.class, annotation.reverse(), decl -> noneMatch(decl.mods.flags, STATIC))).forEach(member -> {
                JCTree.JCExpression expr = maker.Select(maker.Ident(names._this), name(member));
                if (member instanceof JCTree.JCMethodDecl)
                    expr = maker.Apply(List.nil(), expr, List.nil()); // this.member()
                final Type type = symbol(member).type;
                if (type.isPrimitive())
                    consumer.accept(maker.Apply(List.nil(), maker.Select(maker.Ident(types.boxedClass(type)), names.hashCode), List.of(expr)));
                else if (types.isArray(type))
                    consumer.accept(maker.Apply(List.nil(), maker.Select(IdentQualifiedName(Arrays.class), name(types.elemtype(type).isPrimitive() ? "hashCode" : "deepHashCode")), List.of(expr)));
                else
                    consumer.accept(maker.Apply(List.nil(), maker.Select(IdentQualifiedName(ArrayHelper.class), name("deepHashCode")), List.of(expr)));
            });
            return result.append(maker.Return(maker.Ident(hash))).toList();
        }
        
    }
    
}
