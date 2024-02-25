package amadeus.maho.transform;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import amadeus.maho.lang.Getter;
import amadeus.maho.lang.inspection.Nullable;

public interface TransformRange {
    
    class ObservationSet implements TransformRange {
        
        @Getter
        private final Set<TransformRange> ranges = ConcurrentHashMap.newKeySet();
        
        public void addTransformInterceptor(final TransformRange range) {
            final Set<TransformRange> ranges = ranges();
            synchronized (ranges) {
                if (ranges.add(range))
                    TransformerManager.Patcher.patch(range);
            }
        }
        
        public void removeTransformInterceptor(final TransformRange range) {
            final Set<TransformRange> ranges = ranges();
            synchronized (ranges) {
                if (ranges.remove(range))
                    TransformerManager.Patcher.patch(range);
            }
        }
        
        public void addTransformInterceptors(final Collection<TransformRange> collection) {
            final Set<TransformRange> ranges = ranges();
            synchronized (ranges) {
                final Collection<TransformRange> changeset = collection.stream().filterNot(ranges::contains).toList();
                ranges *= changeset;
                TransformerManager.Patcher.patch(changeset);
            }
        }
        
        public void removeTransformInterceptors(final Collection<TransformRange> collection) {
            final Set<TransformRange> ranges = ranges();
            synchronized (ranges) {
                final Collection<TransformRange> changeset = collection.stream().filter(ranges::contains).toList();
                ranges /= changeset;
                TransformerManager.Patcher.patch(changeset);
            }
        }
        
        @Override
        public boolean isTarget(final @Nullable ClassLoader loader, final String name) = ranges.stream().anyMatch(range -> range.isTarget(loader, name));
        
        @Override
        public boolean isTarget(final Class<?> clazz) = ranges.stream().anyMatch(range -> range.isTarget(clazz));
        
    }
    
    default boolean isTarget(final @Nullable ClassLoader loader, final String name) = false;
    
    default boolean isTarget(final Class<?> clazz) = isTarget(clazz.getClassLoader(), clazz.getName());
    
}
