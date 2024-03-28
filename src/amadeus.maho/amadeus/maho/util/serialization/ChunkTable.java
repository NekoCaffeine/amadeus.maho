package amadeus.maho.util.serialization;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.TableSwitchGenerator;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Extension;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.bytecode.generator.MethodGenerator;
import amadeus.maho.util.control.LinkedIterator;
import amadeus.maho.util.dynamic.ClassLocal;
import amadeus.maho.util.dynamic.DynamicMethod;
import amadeus.maho.util.dynamic.LookupHelper;
import amadeus.maho.util.runtime.DebugHelper;
import amadeus.maho.util.runtime.TypeHelper;
import amadeus.maho.vm.JVMTI;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChunkTable<H extends BinaryMapper, V extends BinaryMapper> implements BinaryMapper {
    
    public interface EndMark {
        
        default boolean endMark() = true;
        
    }
    
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface CloneBase { }
    
    public sealed interface AutomaticMapperType extends EOFMark {
        
        non-sealed interface Int extends AutomaticMapperType {
            
            int chunkType();
            
        }
        
    }
    
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface IntMark {
        
        int value() default -1;
        
        boolean unknown() default false;
        
    }
    
    public static class AutomaticMapper<H extends BinaryMapper & AutomaticMapperType> extends ChunkTable<H, H> {
        
        @Getter
        private static final ClassLocal<Function> mapperLocal = {
                headerType -> {
                    if (AutomaticMapperType.Int.class.isAssignableFrom(headerType)) {
                        final Map<Integer, Class<?>> mapper = new HashMap<>();
                        final boolean p_flag[] = { true };
                        new LinkedIterator<Class<?>>(Class::getSuperclass, headerType).stream(true)
                                .takeWhile(clazz -> clazz != Object.class)
                                .takeWhile(clazz -> {
                                    final boolean flag = p_flag[0];
                                    if (clazz.isAnnotationPresent(IntMark.class))
                                        p_flag[0] = false;
                                    return flag;
                                })
                                .flatMap(clazz -> Stream.of(clazz.getDeclaredClasses()))
                                .forEach(inner -> {
                                    final @Nullable IntMark mark = inner.getAnnotation(IntMark.class);
                                    if (mark != null)
                                        mapper.compute(mark.unknown() ? null : mark.value(), (key, value) -> {
                                            if (value != null)
                                                throw DebugHelper.breakpointBeforeThrow(new IllegalStateException(STR."Duplicate keys: \{(Object) key ?? "unknown"}, in outer class: \{headerType.getCanonicalName()}"));
                                            return inner;
                                        });
                                });
                        final Class<?> cloneBaseType = cloneBaseType(headerType);
                        final DynamicMethod.Lambda<Function> lambda = { headerType.getClassLoader(), STR."ChunkTable.AutomaticMapper$\{headerType.asDebugName()}", Function.class, cloneBaseType, BinaryMapper.class };
                        lambda.sourceFile(JVMTI.env().getSourceFileName(headerType));
                        final MethodGenerator generator = lambda.generator();
                        generator.loadArg(0);
                        generator.invokeTarget(LookupHelper.method1(AutomaticMapperType.Int::chunkType));
                        generator.tableSwitch(mapper.keySet().stream().nonnull().mapToInt(Integer::intValue).sorted().toArray(), new TableSwitchGenerator() {
                            
                            @Override
                            public void generateCase(final int key, final Label end) {
                                newInstance(mapper[key]);
                                generator.goTo(end);
                            }
                            
                            @Override
                            public void generateDefault() {
                                final @Nullable Class chunkType = mapper[null];
                                if (chunkType == null)
                                    generator.throwException(Type.getType(UnsupportedOperationException.class), "There are no marker for unknown chunk type, for details see: ChunkTable.IntMark#unknown");
                                else
                                    newInstance(chunkType);
                            }
                            
                            @SneakyThrows
                            private void newInstance(final Class<?> chunkType) {
                                final Type type = Type.getType(chunkType);
                                generator.newInstance(type);
                                generator.dup();
                                Constructor<?> constructor;
                                try {
                                    constructor = chunkType.getDeclaredConstructor(cloneBaseType);
                                    generator.loadArg(0);
                                } catch (final NoSuchMethodException noSuchOneArg) {
                                    try {
                                        constructor = chunkType.getDeclaredConstructor();
                                    } catch (final NoSuchMethodException noSuchZeroArg) {
                                        noSuchOneArg.addSuppressed(noSuchZeroArg);
                                        throw DebugHelper.breakpointBeforeThrow(new UnsupportedOperationException(noSuchOneArg));
                                    }
                                }
                                generator.invokeTarget(constructor);
                            }
                            
                        });
                        generator.returnValue();
                        generator.endMethod();
                        return lambda.allocateInstance();
                    }
                    throw DebugHelper.breakpointBeforeThrow(new UnsupportedOperationException(STR."auto mapper: \{headerType}"));
                }, true
        };
        
        @SneakyThrows
        public AutomaticMapper(final Class<? extends H> outerType, final BiPredicate<? super Input, ? super H> endChecker = defaultEndChecker(),
                final Supplier<H> supplier = TypeHelper.<H>noArgConstructor((Class<H>) cloneBaseType(outerType)), final Function<? super H, ? extends H> mapper = mapperLocal()[outerType]) = super(input -> {
            final H header = supplier.get().deserialization(input);
            return header.eofMark() ? null : header;
        }, endChecker, mapper);
        
        public static <T> Class<? super T> cloneBaseType(final Class<T> subType) = new LinkedIterator<Class<? super T>>(Class::getSuperclass, subType).stream(true)
                .filter(clazz -> clazz != Object.class)
                .filter(clazz -> clazz.isAnnotationPresent(CloneBase.class))
                .findFirst()
                .orElse(subType);
    
    }
    
    Function<? super Input, ? extends H> reader;
    
    BiPredicate<? super Input, ? super V> endChecker;
    
    Function<? super H, ? extends V> mapper;
    
    ArrayList<V> table = { };
    
    @Override
    @SneakyThrows
    public void write(final Output output) throws IOException = table().forEach(value -> value.serialization(output));
    
    @Override
    public void read(final Input input) throws IOException {
        @Nullable H header;
        @Nullable V value;
        final ArrayList<V> table = table();
        final Function<? super Input, ? extends H> reader = reader();
        final Function<? super H, ? extends V> mapper = mapper();
        while ((header = reader.apply(input)) != null && (value = mapper.apply(header)) != null) {
            value.deserialization(input);
            table += value;
            if (endChecker.test(input, value))
                break;
        }
    }
    
    @Extension.Operator("GET")
    public <T extends V> Stream<T> lookup(final Class<T> type) = table().stream().cast(type);
    
    public static <V> BiPredicate<Input, V> defaultEndChecker() = (input, value) -> value instanceof EndMark endMark && endMark.endMark();
    
    
}
