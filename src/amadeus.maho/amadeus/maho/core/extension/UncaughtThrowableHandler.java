package amadeus.maho.core.extension;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;

import amadeus.maho.lang.Getter;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.Init;
import amadeus.maho.transform.mark.base.TransformProvider;

@Init(initialized = true)
@TransformProvider
public interface UncaughtThrowableHandler {
    
    @Getter
    List<BiPredicate<Thread, Throwable>> handlers = new CopyOnWriteArrayList<>();
    
    @Hook
    private static Hook.Result uncaughtException(final ThreadGroup $this, final Thread thread, final Throwable throwable) = Hook.Result.falseToVoid(handlers().stream().anyMatch(predicate -> predicate.test(thread, throwable)));
    
}
