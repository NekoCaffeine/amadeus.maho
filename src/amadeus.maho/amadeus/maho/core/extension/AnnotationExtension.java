package amadeus.maho.core.extension;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import jdk.internal.reflect.ConstantPool;

import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.Redirect;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.Slice;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.bytecode.ASMHelper;

import sun.reflect.annotation.AnnotationInvocationHandler;
import sun.reflect.annotation.AnnotationParser;

import static amadeus.maho.util.bytecode.Bytecodes.ATHROW;

@TransformProvider
public interface AnnotationExtension {
    
    @Hook
    private static Hook.Result validateAnnotationMethods(final AnnotationInvocationHandler $this, final Method memberMethods[]) = Hook.Result.NULL;
    
    @Redirect(targetClass = AnnotationInvocationHandler.class, selector = ASMHelper._INIT_, slice = @Slice(@At(insn = @At.Insn(opcode = ATHROW))))
    private static void extendedAnnotationType(final Throwable throwable) { }
    
    @Hook(value = AnnotationParser.class, isStatic = true, at = @At(method = @At.MethodInsn(name = "isAnnotation")), before = false, capture = true)
    private static boolean parseArray(final boolean capture, final Class<?> arrayType, final ByteBuffer buf, final ConstantPool constPool, final Class<?> container) = capture || arrayType == Annotation[].class;
    
}
