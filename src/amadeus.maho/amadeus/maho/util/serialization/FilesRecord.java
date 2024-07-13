package amadeus.maho.util.serialization;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.stream.Stream;

import amadeus.maho.lang.CallChain;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.util.dynamic.ClassLocal;
import amadeus.maho.util.runtime.MethodHandleHelper;

@SneakyThrows
public interface FilesRecord {
    
    ClassLocal<MethodHandle> constructorLocal = { type -> MethodHandleHelper.lookup().findConstructor(type, MethodType.methodType(void.class, Stream.of(type.getRecordComponents()).map(RecordComponent::getType).toArray(Class[]::new))) };
    
    default Stream<RecordComponent> components() = Stream.of(getClass().getRecordComponents());
    
    default Stream<Path> paths() = components().map(component -> component.getAccessor().invoke(this)).map(Path.class::cast);
    
    default boolean exists() = paths().allMatch(Files::exists);
    
    default void delete() = paths().forEach(Files::deleteIfExists);
    
    default boolean corrupt() {
        if (!exists()) {
            delete();
            return true;
        }
        return false;
    }
    
    default boolean valid() = !corrupt();
    
    @CallChain
    default FilesRecord map(final Function<Path, Path> mapper) = (FilesRecord) constructorLocal[getClass()].invokeWithArguments(paths().map(mapper).toArray());
    
    @CallChain
    default FilesRecord resolve(final Path dir) = map(dir::resolve);
    
    static <R extends Record & FilesRecord> R of(final Class<R> type) = (R) constructorLocal[type].invokeWithArguments(Stream.of(type.getRecordComponents()).map(RecordComponent::getName).map(Path::of).toArray());
    
}
