// package amadeus.maho.lang.javac.handler;
//
// import com.sun.source.tree.TreeVisitor;
// import com.sun.tools.javac.parser.JavacParser;
// import com.sun.tools.javac.tree.JCTree;
// import com.sun.tools.javac.util.List;
//
// import amadeus.maho.lang.AccessLevel;
// import amadeus.maho.lang.AllArgsConstructor;
// import amadeus.maho.lang.FieldDefaults;
// import amadeus.maho.lang.Privilege;
// import amadeus.maho.lang.RequiredArgsConstructor;
// import amadeus.maho.lang.inspection.Nullable;
// import amadeus.maho.transform.mark.Hook;
// import amadeus.maho.transform.mark.base.At;
// import amadeus.maho.transform.mark.base.MethodDescriptor;
// import amadeus.maho.transform.mark.base.TransformProvider;
//
// import static com.sun.source.tree.Tree.Kind.OTHER;
//
// @TransformProvider
// public interface LocalTypeVarHandler {
//
//     @AllArgsConstructor
//     @RequiredArgsConstructor
//     @FieldDefaults(level = AccessLevel.PUBLIC)
//     class LocalTypeVarAttachExpression extends JCTree.JCExpression {
//
//         JCExpression expression;
//
//         List<JCTypeParameter> parameters;
//
//         @Override
//         public Tag getTag() = Tag.NO_TAG;
//
//         @Override
//         public void accept(final Visitor v) = expression?.accept(v);
//
//         @Override
//         public Kind getKind() = OTHER;
//
//         @Override
//         public <R, D> @Nullable R accept(final TreeVisitor<R, D> v, final D d) = expression?.accept(v, d) ?? null;
//
//     }
//
//     @Hook(at = @At(method = @At.MethodInsn(name = "typeArgumentsOpt", descriptor = @MethodDescriptor(value = JCTree.JCExpression.class, parameters = JCTree.JCExpression.class)), ordinal = 1), before = false, capture = true)
//     private static JCTree.JCExpression term3(final JCTree.JCExpression capture, final JavacParser $this) {
//         final List<JCTree.JCTypeParameter> typeParameters = (Privilege) $this.typeParametersOpt();
//         if (typeParameters != null && !typeParameters.isEmpty()) {
//             final LocalTypeVarAttachExpression expression = { capture, typeParameters };
//             expression.pos = capture.pos;
//             return (Privilege) $this.<JCTree.JCExpression>toP(expression);
//         }
//         return capture;
//     }
//
// }
