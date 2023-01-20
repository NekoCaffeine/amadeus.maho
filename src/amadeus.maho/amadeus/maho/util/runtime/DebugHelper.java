package amadeus.maho.util.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.misc.Environment;
import amadeus.maho.util.throwable.BreakException;
import amadeus.maho.vm.JDWP;

@Getter
public interface DebugHelper {
    
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS)
    @interface Renderer {
        
        String value() default "";
        
        String childrenArray() default "";
        
        String hasChildren() default "";
        
    }
    
    interface CodePathPerception {
        
        String renderCodePath = "codePathString()"; // must be const
        
        @Setter
        @Nullable CodePath codePath();
        
        default String codePathString() = codePath()?.toString() ?? "";
        
    }
    
    @EqualsAndHashCode
    record CodePath(Class<?> clazz, String fieldName, @Nullable Object debugInfo) {
    
        @Override
        public String toString() = clazz.getCanonicalName() + "#" + fieldName;
        
    }
    
    @NoArgsConstructor
    class NotImplementedError extends Error { }
    
    boolean
            inDebug              = Environment.local().lookup("maho.debug.helper", JDWP.isJDWPEnable()),
            showBreakpoint       = Environment.local().lookup("maho.debug.show.breakpoint", inDebug());
    
    Map<Object, Object> globalContext = new ConcurrentHashMap<>();
    
    ThreadLocal<Map<Object, Object>> contextLocal = ThreadLocal.withInitial(ConcurrentHashMap::new);
    
    static Map<Object, Object> localContext() = contextLocal().get();
    
    static void breakpoint() {
        if (showBreakpoint)
            new Exception("Breakpoint stack trace").printStackTrace();
    }
    
    @SneakyThrows
    static <T extends Throwable> T breakpointBeforeThrow(final T throwable) {
        breakpoint();
        throw throwable;
    }
    
    static <T> T breakpointThenBreak() {
        breakpoint();
        throw BreakException.instance();
    }
    
    static <T> T breakpointThenError() {
        breakpoint();
        throw new AssertionError();
    }
    
    static <T> T notYetImplemented() {
        breakpoint();
        throw new NotImplementedError("Not yet implemented");
    }
    
    static void breakpointWhen(final boolean flag = Environment.assertState()) {
        if (flag)
            breakpoint();
    }
    
    static <T> @Nullable T breakpointWhenDebug(final @Nullable T value) {
        if (inDebug())
            breakpoint();
        return value;
    }
    
    static <K> boolean checkCount(final Map<Object, Object> context = globalContext(), final K contextKey, final IntPredicate valueChecker = it -> it > 0, final @Nullable IntUnaryOperator orElse = it -> it + 1) {
        final AtomicInteger atomic = (AtomicInteger) context.computeIfAbsent(contextKey, it -> new AtomicInteger());
        if (orElse == null)
            return valueChecker.test(atomic.get());
        int value;
        do {
            if (valueChecker.test(value = atomic.get()))
                return true;
        } while (!atomic.compareAndSet(value, orElse.applyAsInt(value)));
        return false;
    }
    
    static void logTimeConsuming(final String name, final Runnable task) {
        final long time = System.currentTimeMillis();
        task.run();
        System.out.println("Task %s is completed, it takes %d ms.".formatted(name, System.currentTimeMillis() - time));
    }
    
}
