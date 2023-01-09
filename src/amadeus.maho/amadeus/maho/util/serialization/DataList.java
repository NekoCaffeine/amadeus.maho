package amadeus.maho.util.serialization;

import java.io.IOException;
import java.util.ArrayList;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.util.runtime.DebugHelper;

@Getter
@SneakyThrows
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DataList<T extends BinaryMapper> implements BinaryMapper {
    
    Supplier<? extends T> maker;
    
    LongSupplier limitGetter;
    
    ArrayList<T> list = { };
    
    @Override
    public void write(final Output output) throws IOException = list().forEach(value -> value.serialization(output));
    
    @Override
    public void read(final Input input) throws IOException {
        final Input subInput = { input };
        final ArrayList<T> list = list();
        final Supplier<? extends T> maker = maker();
        final long limit = limitGetter.getAsLong();
        while (subInput.offset() < limit)
            list += maker.get().let(value -> value.deserialization(subInput));
        if (subInput.offset() > limit)
            DebugHelper.breakpointBeforeThrow(new OverflowException("sub input offset: %d, limit: %d".formatted(subInput.offset(), limit)));
    }
    
}
