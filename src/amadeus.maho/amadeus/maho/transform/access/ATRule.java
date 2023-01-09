package amadeus.maho.transform.access;

import java.util.LinkedList;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.ToString;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.context.TransformContext;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import static amadeus.maho.util.math.MathHelper.max;
import static amadeus.maho.util.runtime.ArrayHelper.indexOf;
import static org.objectweb.asm.Opcodes.*;

@ToString
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PUBLIC)
public class ATRule {
    
    private static final int ACCESS[] = { ACC_PRIVATE, 0/* PACKAGE_DEFAULT */, ACC_PROTECTED, ACC_PUBLIC };
    
    int accessModifier, addModifier, delModifier;
    
    @Getter
    private final LinkedList<String> debugSources = { };
    
    public void merge(final ATRule rule) {
        accessModifier = ACCESS[max(indexOf(ACCESS, accessModifier), indexOf(ACCESS, rule.accessModifier))];
        addModifier |= rule.addModifier;
        delModifier |= rule.delModifier;
        debugSources *= rule.debugSources;
    }
    
    public int transform(final TransformContext context, final int access) {
        final int result = ASMHelper.changeAccess(access, accessModifier) | addModifier & ~delModifier;
        if (access != result)
            context.markModified();
        return result;
    }
    
    public void transform(final TransformContext context, final ClassNode node) = node.access = transform(context, node.access);
    
    public void transform(final TransformContext context, final MethodNode node) = node.access = transform(context, node.access);
    
    public void transform(final TransformContext context, final FieldNode node) = node.access = transform(context, node.access);
    
}
