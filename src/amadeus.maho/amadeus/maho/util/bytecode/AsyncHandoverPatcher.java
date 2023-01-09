package amadeus.maho.util.bytecode;

import java.lang.reflect.Method;
import java.util.function.Supplier;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

import amadeus.maho.util.bytecode.context.TransformContext;
import amadeus.maho.util.bytecode.generator.MethodGenerator;
import amadeus.maho.util.dynamic.LookupHelper;
import amadeus.maho.util.math.MathHelper;
import amadeus.maho.util.runtime.ArrayHelper;

import static org.objectweb.asm.Opcodes.*;

public interface AsyncHandoverPatcher {
    
    Method
            run = LookupHelper.<Runnable>methodV1(Runnable::run),
            get = LookupHelper.<Supplier, Object>method1(Supplier::get);
    
    String
            voidHandoverName   = run.getName(),
            getterHandoverName = get.getName();
    Type
            voidLambdaType           = Type.getType(run.getDeclaringClass()),
            getterLambdaType         = Type.getType(get.getDeclaringClass()),
            voidHandoverMethodType   = Type.getType(run),
            getterHandoverMethodType = Type.getType(get);
    
    static void patch(final TransformContext context, final ClassNode owner, final MethodNode target, final Handle guard, final Handle voidHandover, final Handle getterHandover) {
        assert voidHandover.getTag() == H_INVOKESTATIC;
        assert getterHandover.getTag() == H_INVOKESTATIC;
        final InsnList list = { };
        final MethodGenerator generator = MethodGenerator.fromShadowMethodNode(target, list);
        final Label label = generator.newLabel();
        generator.invokeHandle(guard);
        generator.ifZCmp(MethodGenerator.NE, label);
        generator.loadThisIfNeed();
        generator.loadArgs();
        final boolean isStatic = MathHelper.anyMatch(target.access, ACC_STATIC), isVoid = generator.returnType.getSort() == Type.VOID;
        final Handle targetHandle = { isStatic ? H_INVOKESTATIC : H_INVOKEVIRTUAL, owner.name, target.name, target.desc, MathHelper.anyMatch(owner.access, ACC_INTERFACE) };
        final Type argumentTypes[] = Type.getArgumentTypes(target.desc), closureTypes[] = isStatic ? argumentTypes : ArrayHelper.insert(argumentTypes, Type.getType(owner.name));
        if (isVoid) {
            generator.invokeLambda(voidHandoverName, Type.getMethodDescriptor(voidLambdaType, closureTypes), voidHandoverMethodType, targetHandle);
            generator.invokeHandle(voidHandover);
        } else {
            final Type boxedType = ASMHelper.boxType(generator.returnType);
            generator.invokeLambda(getterHandoverName, Type.getMethodDescriptor(getterLambdaType, closureTypes), getterHandoverMethodType, targetHandle, Type.getMethodType(boxedType));
            generator.invokeHandle(getterHandover);
            if (boxedType != generator.returnType)
                generator.unbox(generator.returnType);
            else
                generator.checkCast(generator.returnType);
        }
        generator.returnValue();
        generator.mark(label);
        target.instructions.insert(list);
        context.markModified();
        context.compute(target, ComputeType.MAX, ComputeType.FRAME);
    }
    
}
