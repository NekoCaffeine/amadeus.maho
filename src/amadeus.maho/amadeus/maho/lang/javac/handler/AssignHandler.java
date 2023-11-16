package amadeus.maho.lang.javac.handler;

import org.objectweb.asm.Label;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.parser.JavacParser;
import com.sun.tools.javac.parser.Tokens;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.List;

import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.javac.JavacContext;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.Redirect;
import amadeus.maho.transform.mark.TransformTarget;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.Slice;
import amadeus.maho.transform.mark.base.TransformMetadata;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.ComputeType;
import amadeus.maho.util.bytecode.context.TransformContext;
import amadeus.maho.util.bytecode.generator.MethodGenerator;
import amadeus.maho.util.control.LinkedIterator;
import amadeus.maho.util.runtime.StreamHelper;

import static amadeus.maho.lang.javac.JavacContext.*;
import static amadeus.maho.util.bytecode.Bytecodes.ASTORE;
import static com.sun.tools.javac.parser.Tokens.TokenKind.*;

@TransformProvider
public class AssignHandler {
    
    private static JCTree.JCExpression dropTypeArguments(final JCTree.JCExpression expression) {
        if (expression instanceof JCTree.JCTypeApply apply)
            apply.arguments = List.nil();
        return expression;
    }
    
    public static void lower(final JCTree.JCVariableDecl tree, final JCTree.JCNewArray newArray, final Type type) {
        if (!type.isPrimitiveOrVoid() && !type.isErroneous()) {
            final TreeMaker maker = instance().maker.at(newArray.pos);
            if (type instanceof Type.ClassType classType)
                tree.init = maker.NewClass(null, List.nil(), dropTypeArguments(maker.Type(classType)), newArray.elems, null);
        }
    }
    
    @Hook(metadata = @TransformMetadata(order = -1))
    private static void visitVarDef(final Attr $this, final JCTree.JCVariableDecl tree) {
        if (tree.vartype != null && tree.init instanceof JCTree.JCNewArray newArray && newArray.elemtype == null) {
            final Env<AttrContext> env = env($this);
            final Type type = $this.attribType(tree.vartype, env.dup(tree));
            lower(tree, newArray, type);
        }
    }
    
    @Hook(metadata = @TransformMetadata(order = 1 << 5))
    private static void visitAssign(final Attr $this, final JCTree.JCAssign tree) {
        if (tree.rhs instanceof JCTree.JCNewArray newArray && newArray.elemtype == null) {
            final Env<AttrContext> env = env($this);
            final Type type = $this.attribExpr(tree.lhs, env.dup(tree));
            if (!type.isPrimitiveOrVoid() && !type.isErroneous()) {
                final TreeMaker maker = instance().maker.at(tree.rhs.pos);
                if (type instanceof Type.ClassType classType)
                    tree.rhs = maker.NewClass(null, List.nil(), dropTypeArguments(maker.Type(classType)), newArray.elems, null);
            }
            tree.lhs.type = null;
        }
    }
    
    @Hook
    private static void visitReturn(final Attr $this, final JCTree.JCReturn tree) {
        if (tree.expr instanceof JCTree.JCNewArray newArray && newArray.elemtype == null) {
            final Env<AttrContext> env = env($this);
            if (env.enclMethod != null) {
                final @Nullable Type type = env.enclMethod.restype.type;
                assert type != null;
                if (!type.isPrimitiveOrVoid() && !type.isErroneous()) {
                    final JavacContext context = instance();
                    final TreeMaker maker = context.maker.at(tree.expr.pos);
                    if (type instanceof Type.ArrayType arrayType)
                        newArray.elemtype = maker.Type(arrayType.elemtype);
                    else if (type instanceof Type.ClassType classType)
                        tree.expr = maker.NewClass(null, List.nil(), dropTypeArguments(maker.Type(classType)), newArray.elems, null);
                }
            }
        }
    }
    
    @Hook
    private static Hook.Result termRest(final JavacParser $this, final JCTree.JCExpression t) {
        final Tokens.Token token = $this.token();
        switch (token.kind) {
            case EQ -> {
                final int pos = token.pos;
                $this.nextToken();
                selectExprMode($this);
                final JCTree.JCExpression rhs = $this.variableInitializer(); // !!! MUST NOT INLINE, otherwise it will CONTAMINATE the position of the tree !!!
                return { toP($this, F($this).at(pos).Assign(t, rhs)) };
            }
        }
        return Hook.Result.VOID;
    }
    
    @Hook
    private static Hook.Result parseSimpleStatement(final JavacParser $this) {
        if ($this.token().kind != RETURN)
            return Hook.Result.VOID;
        $this.nextToken();
        final Tokens.Token token = $this.token();
        final JCTree.JCExpression result = token.kind == SEMI ? null : $this.variableInitializer();
        $this.accept(SEMI);
        return { toP($this, F($this).at(token.pos).Return(result)) };
    }
    
    private static final String $$$BLOCK$$$ = "$$$BLOCK$$$";
    
    @TransformProvider
    interface Layer {
        
        @TransformTarget(targetClass = JavacParser.class, selector = "methodDeclaratorRest", metadata = @TransformMetadata(order = -1))
        private static void methodDeclaratorRest(final TransformContext context, final ClassNode node, final MethodNode methodNode) {
            final AbstractInsnNode lbrace = StreamHelper.fromIterator(methodNode.instructions.iterator())
                    .filter(insn -> insn instanceof FieldInsnNode fieldInsn && fieldInsn.name.equals("LBRACE"))
                    .findFirst()
                    .orElseThrow();
            final LabelNode jump = new LinkedIterator<>(AbstractInsnNode::getNext, StreamHelper.fromIterator(methodNode.instructions.iterator())
                    .filter(insn -> insn instanceof MethodInsnNode methodInsn && methodInsn.name.equals("block"))
                    .skip(1L)
                    .findFirst().orElseThrow())
                    .stream()
                    .cast(LabelNode.class)
                    .findFirst()
                    .orElseThrow();
            final InsnList list = { };
            final MethodGenerator generator = MethodGenerator.fromShadowMethodNode(methodNode, list);
            generator.dup();
            final org.objectweb.asm.Type tokenKind = org.objectweb.asm.Type.getType(Tokens.TokenKind.class);
            generator.getStatic(tokenKind, "EQ", tokenKind);
            final Label label = generator.newLabel();
            generator.ifCmp(tokenKind, MethodGenerator.NE, label);
            generator.loadThis();
            generator.loadArg(2);
            generator.invokeStatic(ASMHelper.TYPE_OBJECT, new Method($$$BLOCK$$$, org.objectweb.asm.Type.getMethodDescriptor(ASMHelper.TYPE_OBJECT, ASMHelper.TYPE_OBJECT, ASMHelper.TYPE_OBJECT)));
            generator.visitVarInsn(ASTORE, 13);
            generator.pushNull();
            generator.visitVarInsn(ASTORE, 14);
            generator.pop(tokenKind);
            generator.goTo(jump.markLabel());
            generator.mark(label);
            methodNode.instructions.insertBefore(lbrace, list);
            context.markModified().markCompute(methodNode, ComputeType.MAX, ComputeType.FRAME);
        }
        
    }
    
    @Redirect(targetClass = JavacParser.class, selector = "methodDeclaratorRest", slice = @Slice(@At(method = @At.MethodInsn(name = $$$BLOCK$$$))))
    private static JCTree.JCBlock block(final JavacParser parser, final JCTree.JCExpression type) {
        parser.nextToken();
        final JCTree.JCExpression expression = parser.variableInitializer();
        parser.accept(SEMI);
        final TreeMaker maker = F(parser);
        return toP(parser, maker.Block(0L, List.of(switch (type == null ? "void" : type.toString()) {
            case "void", "self" -> {
                if (expression instanceof JCTree.JCSwitchExpression switchExpression) {
                    final JCTree.JCSwitch jcSwitch = (Privilege) new JCTree.JCSwitch(switchExpression.selector, switchExpression.cases);
                    jcSwitch.pos = switchExpression.pos;
                    jcSwitch.endpos = switchExpression.endpos;
                    jcSwitch.hasUnconditionalPattern = switchExpression.hasUnconditionalPattern;
                    jcSwitch.patternSwitch = switchExpression.patternSwitch;
                    jcSwitch.cases.forEach(it -> {
                        if (it.body instanceof JCTree.JCExpression expr) {
                            final JCTree.JCExpressionStatement statement = (Privilege) new JCTree.JCExpressionStatement(expr);
                            statement.pos = expr.pos;
                            it.stats = List.of(statement);
                        }
                    });
                    yield jcSwitch;
                }
                yield maker.Exec(expression);
            }
            default     -> maker.Return(expression);
        })));
    }
    
}
