package amadeus.maho.util.dynamic;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.inspection.Nullable;

public interface ModificationTracker {
    
    @FunctionalInterface
    interface Packaging extends ModificationTracker {
        
        List<ModificationTracker> trackers();
        
        default Stream<ModificationTracker> nonnullTrackers() = trackers().stream().nonnull();
    
        @Override
        default long modificationCount() = nonnullTrackers().mapToLong(ModificationTracker::modificationCount).sum();
    
        @Override
        default void onModification() = nonnullTrackers().forEach(ModificationTracker::onModification);
        
    }
    
    interface Base extends ModificationTracker {
        
        @Setter
        @Getter
        @Override
        long modificationCount();
        
        @Override
        default void onModification() = modificationCount(modificationCount() + 1);
    
    }
    
    interface Atomic extends Base {
        
        interface Ever extends Atomic {
    
            @Override
            default long modificationCount() = atomicModificationCount().getAndIncrement();
            
        }
        
        AtomicLong atomicModificationCount();
        
        @Override
        default long modificationCount() = atomicModificationCount().get();
    
        @Override
        default void modificationCount(final long value) = atomicModificationCount().set(value);
    
        @Override
        default void onModification() = atomicModificationCount().addAndGet(1L);
        
    }
    
    interface Indirect extends ModificationTracker {
        
        @Nullable ModificationTracker indirectTracker();
    
        @Override
        default long modificationCount() = indirectTracker()?.modificationCount() ?? -1L;
    
        @Override
        default void onModification() = indirectTracker()?.onModification();
        
    }
    
    long modificationCount();
    
    default void onModification() { }
    
    default <I> Supplier<I> dependsOn(final Function<? super self, I> maker) {
        final long modificationRecord[] = { -1L };
        final I p_index[] = (I[]) new Object[]{ null };
        return () -> {
            final long record = modificationRecord[0], count = modificationCount();
            if (record == -1L || record != count) {
                p_index[0] = maker.apply(this);
                modificationRecord[0] = count;
            }
            return p_index[0];
        };
    }
    
    default <I, A, R> Function<A, R> dependsOn(final Function<? super self, I> maker, final BiFunction<I, A, R> depend) {
        final Supplier<I> supplier = dependsOn(maker);
        return arg -> depend.apply(supplier.get(), arg);
    }
    
    static Atomic atomic(final AtomicLong count = { }) = () -> count;
    
    static Atomic.Ever ever(final AtomicLong count = { }) = () -> count;
    
    static ModificationTracker never() = () -> 0L;
    
}
