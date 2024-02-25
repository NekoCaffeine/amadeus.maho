package amadeus.maho.core;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import amadeus.maho.lang.Getter;
import amadeus.maho.util.dynamic.CallerContext;
import amadeus.maho.util.profile.Sampler;
import amadeus.maho.util.tuple.Tuple2;

public interface MahoProfile {

    @Getter
    ConcurrentHashMap<String, Sampler<?>> samplers = { };
    
    static <T> Sampler<T> sampler(final String name = CallerContext.caller().getSimpleName()) = (Sampler<T>) samplers().computeIfAbsent(name, key -> new Sampler<>());
    
    static void mark(final String name, final Sampler<?> sampler) = samplers()[name] = sampler;
    
    static void dump(final Map<String, Sampler<?>> samplers = samplers(), final List<String> list, final String subHead) {
        final String subHead2 = subHead.repeat(2), subHead3 = subHead.repeat(3);
        list += "Maho Profile:";
        list += STR."\{subHead}Samplers:";
        samplers.forEach((name, sampler) -> {
            final long total = ~sampler.total(), count = ~sampler.count();
            if (count == 0L)
                list += STR."\{subHead2}\{name}: <empty>";
            else {
                final Tuple2<?, Sampler.Frame> min = ~sampler.min(), max = ~sampler.max();
                list += subHead2 + name + " [ total: %d ms, count: %d, avg: %.3f ns ]".formatted(total / (int) 1e6, count, (double) total / count);
                list += subHead3 + STR."min: \{STR."\{min.v1} => \{min.v2}"}";
                list += subHead3 + STR."max: \{STR."\{max.v1} => \{max.v2}"}";
                sampler.data().forEach((key, frames) -> {
                    final long sum = frames.stream().mapToLong(Sampler.Frame::total).sum(), size = frames.size();
                    list += subHead3 + STR."\{key}" + " [ total: %d ms, count: %d, avg: %.3f ns ]".formatted(sum / (int) 1e6, size, (double) sum / size);
                });
            }
        });
    }
    
}
