package amadeus.maho.util.runtime;

import java.lang.invoke.StringConcatFactory;
import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;

import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.generator.MethodGenerator;
import amadeus.maho.util.control.FunctionChain;
import amadeus.maho.util.control.Switch;
import amadeus.maho.util.dynamic.ClassLocal;
import amadeus.maho.util.dynamic.DynamicMethod;

import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;

@Getter
public interface ToStringHelper {
    
    @Setter
    @Getter
    class Generator {
        
        /**
         * Tag used to demarcate an ordinary argument.
         */
        private static final char tagArg = '\u0001';
        
        private static final Handle makeConcatWithConstants = {
                H_INVOKESTATIC,
                ASMHelper.className(StringConcatFactory.class),
                "makeConcatWithConstants",
                Type.getMethodDescriptor(
                        ASMHelper.TYPE_CALL_SITE,
                        ASMHelper.TYPE_METHOD_HANDLES_LOOKUP,
                        ASMHelper.TYPE_STRING,
                        ASMHelper.TYPE_METHOD_TYPE,
                        ASMHelper.TYPE_STRING,
                        ASMHelper.TYPE_OBJECT_ARRAY),
                false
        };
        
        private static final Generator instance = { };
        
        String start = "{ ", end = " }", joiner = ": ", delimiter = ", ";
        
        public boolean filter(final Field field) = ReflectionHelper.noneMatch(field, ReflectionHelper.STATIC) && !field.isAnnotationPresent(ToString.Mark.class);
        
        protected void generate(final Class<?> target, final StringBuilder builder, final MethodGenerator generator, final LinkedList<Type> argsTypes) = builder.append(ReflectionHelper.allFields(target).stream()
                .filter(this::filter)
                .peek(field -> {
                    generator.loadArg(0);
                    generator.getField(Type.getType(field.getDeclaringClass()), field.getName(), Type.getType(field.getType()).let(argsTypes::addLast));
                })
                .map(field -> field.getName() + joiner() + tagArg())
                .collect(Collectors.joining(delimiter())));
        
        @SneakyThrows
        public Function<Object, String> generate(final Class<?> target) {
            final DynamicMethod.Lambda<Function<Object, String>> lambda = { target.getClassLoader(), STR."ToString$\{target.asDebugName()}", Function.class, Object.class, String.class };
            final MethodGenerator generator = lambda.generator();
            final StringBuilder builder = { 1 << 6 };
            final LinkedList<Type> argsTypes = { };
            builder.append(start());
            generate(target, builder, generator, argsTypes);
            generator.invokeDynamic("concat", Type.getMethodDescriptor(ASMHelper.TYPE_STRING, argsTypes.toArray(Type[]::new)), makeConcatWithConstants, builder.append(end()).toString());
            generator.returnValue();
            generator.endMethod();
            return lambda.allocateInstance();
        }
        
    }
    
    String renderToString = "toString()"; // must be const
    
    FunctionChain<Class<?>, Function<Object, String>> defaultSupplyChain = new FunctionChain<Class<?>, Function<Object, String>>().add(target -> target.map(Generator.instance()::generate));
    
    Switch<Class<?>, Function<Object, String>> initSwitch = new Switch<Class<?>, Function<Object, String>>().whenDefaultF(defaultSupplyChain()::applyNullable);
    
    ClassLocal<Function<Object, String>> toStringChainLocal = { initSwitch()::applyNullable, true };
    
    static String toString(final @Nullable Object object) = object == null ? "<null>" : toStringChainLocal()[object.getClass()].apply(object);
    
}
