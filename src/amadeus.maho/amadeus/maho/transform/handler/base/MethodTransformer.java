package amadeus.maho.transform.handler.base;

import java.lang.annotation.Annotation;

import org.objectweb.asm.tree.MethodNode;

import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.transform.AOTTransformer;
import amadeus.maho.transform.mark.base.Experimental;
import amadeus.maho.util.annotation.mark.Freeze;
import amadeus.maho.util.bytecode.ASMHelper;

@RequiredArgsConstructor
public abstract class MethodTransformer<A extends Annotation> extends BaseTransformer<A> {
    
    @Freeze
    protected final MethodNode sourceMethod;
    
    {
        if (ASMHelper.hasAnnotation(sourceMethod, Experimental.class))
            markExperimental();
    }
    
    @Override
    public void onAOT(final AOTTransformer aotTransformer) = aotTransformer.addMethodAnnotation(sourceMethod.name, sourceMethod.desc, annotation.annotationType());
    
    @Override
    public String toString() = STR."\{super.toString()}#\{sourceMethod.name}\{sourceMethod.desc}";
    
}
