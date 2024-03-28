package amadeus.maho.util.dynamic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldInsnNode;

import amadeus.maho.core.Maho;
import amadeus.maho.lang.Extension;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Proxy;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.runtime.StreamHelper;
import amadeus.maho.vm.tools.hotspot.WhiteBox;

import static org.objectweb.asm.Opcodes.*;

@Extension
@SneakyThrows
public interface EnumHelper {
    
    @TransformProvider
    class EnumCacheAgent {
        
        @Proxy(PUTFIELD)
        private static native <T> void enumConstants(final Class<T> $this, final @Nullable T value[]);
        
        @Proxy(PUTFIELD)
        private static native <T> void enumConstantDirectory(final Class<T> $this, final @Nullable Map<String, T> value);
        
        public static void cleanEnumClassCache(final Class<? extends Enum> enumClass) {
            enumConstants(enumClass, null);
            enumConstantDirectory(enumClass, null);
        }
        
    }
    
    @Getter
    ClassLocal<Field> valuesFieldLocator = {
            target -> {
                try {
                    final Field values = target.getDeclaredField("$VALUES"); // Try to get the special fields generated by javac first to avoid the high cost of retransform class
                    final Class<?> valuesType = values.getType();
                    if (valuesType.isArray() && valuesType.getComponentType() == target)
                        return values;
                } catch (NoSuchFieldException ignored) { }
                return ASMHelper.lookupMethodNode(Maho.getClassNodeFromClassNonNull(target), "values", Type.getMethodDescriptor(ASMHelper.arrayType(Type.getType(target)))) // Rollback to JVMTI RetransformClasses
                        .stream()
                        .flatMap(methodNode -> StreamHelper.fromIterable(methodNode.instructions))
                        .filter(insn -> insn.getOpcode() == GETSTATIC) // Lookup instructions for the reading field
                        .map(insn -> target.getDeclaredField(((FieldInsnNode) insn).name))
                        .findFirst()
                        .orElseThrow(() -> new UnsupportedOperationException(STR."find enum class values field: \{target}"));
            }
    };
    
    @Getter
    ClassLocal<Method> valuesMethodLocator = { target -> target.getDeclaredMethod("values", Enum[].class) };
    
    @Getter
    ClassLocal<Object> syncMappingLocal = { target -> new byte[0] };
    
    static void cleanCache(final Class<? extends Enum> enumClass) {
        EnumCacheAgent.cleanEnumClassCache(enumClass); // Clean reflection cache
        // Deoptimization enumClass.values(), because JITC will inline references to the "values" field
        // Deleting this code may still be correct because the valuesFieldLocator may be triggered to cause deoptimization
        try {
            WhiteBox.instance().deoptimizeMethod(valuesMethodLocator()[enumClass]); // Use HotSpot WhiteBox API first to avoid the high cost of retransform class
        } catch (final Throwable throwable) { Maho.instrumentation().retransformClasses(enumClass); } // Rollback to JVMTI RetransformClasses
    }
    
    @SafeVarargs
    static <T extends Enum> void addEnum(final T... instances) {
        if (instances.length != 0) {
            final Class<? extends Enum> enumClass = instances[0].getClass();
            synchronized (syncMappingLocal()[enumClass]) { // Avoid some operations failing due to thread insecurity when adding concurrently
                final Field valuesField = valuesFieldLocator()[enumClass];
                final Enum srcValues[] = FinalFieldHelper.getStatic(valuesField), newValues[] = Arrays.copyOf(srcValues, srcValues.length + instances.length, srcValues.getClass());
                System.arraycopy(instances, 0, newValues, srcValues.length, instances.length);
                FinalFieldHelper.setStatic(valuesField, newValues);
                cleanCache(enumClass); // Clean reflection cache and remove JIT inline optimization
            }
        }
    }
    
}
