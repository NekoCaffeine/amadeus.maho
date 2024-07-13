package amadeus.maho.transform.access;

import java.security.ProtectionDomain;

import org.objectweb.asm.tree.ClassNode;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.ClassTransformer;
import amadeus.maho.transform.mark.base.Transformer;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.context.TransformContext;
import amadeus.maho.util.container.MapTable;

@Transformer
public enum AccessTransformer implements ClassTransformer {
    
    INSTANCE;
    
    private final MapTable<String, String, ATRule> atRuleTable = MapTable.ofConcurrentHashMapTable();
    
    public void addRules(final MapTable<String, String, ATRule> rules) = rules.forEach((raw, column, value) -> {
        final @Nullable ATRule rule = atRuleTable.get(raw, column);
        if (rule == null)
            atRuleTable.put(raw, column, value);
        else
            rule.merge(value);
    });
    
    @Override
    public @Nullable ClassNode transform(final TransformContext context, final @Nullable ClassNode node, final @Nullable ClassLoader loader, final @Nullable Class<?> clazz, final @Nullable ProtectionDomain domain) {
        if (node == null)
            return node;
        final String name = ASMHelper.sourceName(node.name);
        atRuleTable.get(name, null)?.transform(context, node);
        node.fields.forEach(fieldNode -> atRuleTable.get(name, fieldNode.name)?.transform(context, fieldNode));
        node.methods.forEach(methodNode -> atRuleTable.get(name, methodNode.name + methodNode.desc)?.transform(context, methodNode));
        return node;
    }
    
    @Override
    public boolean isTarget(final ClassLoader loader, final String name) = atRuleTable.containsRow(name);
    
    @Override
    public boolean isTarget(final Class<?> clazz) = false;
    
}
