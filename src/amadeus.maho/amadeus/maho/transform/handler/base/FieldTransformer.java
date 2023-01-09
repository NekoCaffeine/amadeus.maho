package amadeus.maho.transform.handler.base;

import java.lang.annotation.Annotation;

import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.transform.AOTTransformer;
import amadeus.maho.transform.mark.base.Experimental;
import amadeus.maho.util.annotation.mark.Freeze;
import amadeus.maho.util.bytecode.ASMHelper;

import org.objectweb.asm.tree.FieldNode;

@RequiredArgsConstructor
public abstract class FieldTransformer<A extends Annotation> extends BaseTransformer<A> {
    
    @Freeze
    protected final FieldNode sourceField;
    
    {
        if (ASMHelper.hasAnnotation(sourceField, Experimental.class))
            markExperimental();
    }
    
    @Override
    public void onAOT(final AOTTransformer aotTransformer) = aotTransformer.addFieldAnnotation(sourceField.name, sourceField.desc, annotation.annotationType());
    
    @Override
    public String toString() = super.toString() + "#" + sourceField.name + ":" + sourceField.desc;
    
}
