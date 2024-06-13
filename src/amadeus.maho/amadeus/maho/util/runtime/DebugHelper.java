package amadeus.maho.util.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;
import java.util.function.LongConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import amadeus.maho.core.MahoExport;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.misc.Environment;

import static amadeus.maho.util.throwable.BreakException.BREAK;

@Getter
public interface DebugHelper {
    
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.CLASS) @interface Renderer {
        
        String value() default "";
        
        String childrenArray() default "";
        
        String hasChildren() default "";
        
    }
    
    interface CodePathPerception {
        
        String renderCodePath = "codePathString()"; // must be const
        
        @Setter
        @Nullable
        Field codePath();
        
        default String codePathString() {
            final @Nullable Field field = codePath();
            return field != null ? STR."\{field.getDeclaringClass().getCanonicalName()}#\{field.getName()}" : "";
        }
        
    }
    
    @NoArgsConstructor
    class NotImplementedError extends Error { }
    
    boolean showBreakpoint = Environment.local().lookup("amadeus.maho.debug.show.breakpoint", MahoExport.debug());
    
    Map<Object, Object> globalContext = new ConcurrentHashMap<>();
    
    ThreadLocal<Map<Object, Object>> contextLocal = ThreadLocal.withInitial(ConcurrentHashMap::new);
    
    static Map<Object, Object> localContext() = contextLocal().get();
    
    static void breakpoint(final Throwable throwable = null) {
        if (showBreakpoint())
            (throwable ?? new Exception("Breakpoint stack trace")).printStackTrace();
    }
    
    @SneakyThrows
    static <T extends Throwable> T breakpointBeforeThrow(final T throwable) {
        breakpoint(throwable);
        throw throwable;
    }
    
    @SneakyThrows
    static <T extends Throwable, R> R breakpointBeforeReturn(final T throwable) {
        breakpoint(throwable);
        throw throwable;
    }
    
    static <T> T breakpointThenBreak() { throw breakpointBeforeThrow(BREAK); }
    
    static <T> T breakpointThenError(final String message) = breakpointThenError(new AssertionError(message));
    
    @SuppressWarnings("ThrowableNotThrown")
    @SneakyThrows
    static <T> T breakpointThenError(final Throwable throwable = new AssertionError()) {
        breakpoint();
        throw throwable;
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
        if (MahoExport.debug())
            breakpoint();
        return value;
    }
    
    @SneakyThrows
    static <T> @Nullable T throwableBarrier(final Supplier<T> supplier, final Predicate<? super Throwable> filter = throwable -> throwable != BREAK) {
        try {
            return supplier.get();
        } catch (final Throwable throwable) {
            if (filter.test(throwable))
                throw breakpointBeforeThrow(throwable);
            throw throwable;
        }
    }
    
    @SneakyThrows
    static void throwableBarrier(final Runnable runnable, final Predicate<? super Throwable> filter = throwable -> throwable != BREAK) {
        try {
            runnable.run();
        } catch (final Throwable throwable) {
            if (filter.test(throwable))
                throw breakpointBeforeThrow(throwable);
            throw throwable;
        }
    }
    
    static <K> boolean checkCount(final Map<Object, Object> context = globalContext(), final K contextKey, final IntPredicate valueChecker = it -> it > 0, final @Nullable IntUnaryOperator orElse = it -> it + 1) {
        final AtomicInteger atomic = (AtomicInteger) context.computeIfAbsent(contextKey, _ -> new AtomicInteger());
        if (orElse == null)
            return valueChecker.test(atomic.get());
        int value;
        do {
            if (valueChecker.test(value = atomic.get()))
                return true;
        } while (!atomic.compareAndSet(value, orElse.applyAsInt(value)));
        return false;
    }
    
    ThreadLocal<List<String>> consumingLocal = ThreadLocal.withInitial(ArrayList::new);
    
    static List<String> consuming() = consumingLocal.get();
    
    static void push(final String name) = consuming() << name;

    static void pop() = consuming()--;

    private static String consumingPrefix() = "  ".repeat(consuming().size());
    
    static void logTimeConsuming(final String name, final Runnable task) {
        if (MahoExport.debug()) {
            push(name);
            final long time = System.currentTimeMillis();
            try {
                task.run();
            } finally {
                pop();
                System.out.println(STR."\{consumingPrefix()}Task [\{name}] took \{System.currentTimeMillis() - time} ms");
            }
        } else
            task.run();
    }
    
    static <T> T logTimeConsuming(final String name, final Supplier<T> task) {
        if (MahoExport.debug()) {
            push(name);
            final long time = System.currentTimeMillis();
            try {
                return task.get();
            } finally {
                pop();
                System.out.println(STR."\{consumingPrefix()}Task [\{name}] took \{System.currentTimeMillis() - time} ms");
            }
        } else
            return task.get();
    }
    
    static void recordTimeConsuming(final Runnable task, final LongConsumer consumer) {
        final long time = System.currentTimeMillis();
        try {
            task.run();
        } finally { consumer.accept(System.currentTimeMillis() - time); }
    }
    
    static <T> T recordTimeConsuming(final Supplier<T> task, final LongConsumer consumer) {
        final long time = System.currentTimeMillis();
        try {
            return task.get();
        } finally { consumer.accept(System.currentTimeMillis() - time); }
    }
    
}
