package amadeus.maho.util.dynamic;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Extension;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;

@Getter
@Extension
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InvokeContext {
    
    @Nullable Runnable before, after;
    
    public InvokeContext(final Lock lock, final @Nullable Runnable before, final @Nullable Runnable after) = this(before >> lock::lock, after << lock::unlock);
    
    public InvokeContext(final AtomicInteger counter) = this(counter::incrementAndGet, counter::decrementAndGet);
    
    public InvokeContext(final AtomicLong counter) = this(counter::incrementAndGet, counter::decrementAndGet);
    
    @Extension.Operator("^")
    public void run(final Runnable runnable) {
        ~before();
        try { runnable.run(); } finally { ~after(); }
    }
    
    @Extension.Operator("^")
    public <T> T run(final Supplier<T> supplier) {
        ~before();
        try { return supplier.get(); } finally { ~after(); }
    }
    
    @Extension.Operator("^")
    public boolean run(final BooleanSupplier supplier) {
        ~before();
        try { return supplier.getAsBoolean(); } finally { ~after(); }
    }
    
    @Extension.Operator("^")
    public int run(final IntSupplier supplier) {
        ~before();
        try { return supplier.getAsInt(); } finally { ~after(); }
    }
    
    @Extension.Operator("^")
    public long run(final LongSupplier supplier) {
        ~before();
        try { return supplier.getAsLong(); } finally { ~after(); }
    }
    
    @Extension.Operator("^")
    public double run(final DoubleSupplier supplier) {
        ~before();
        try { return supplier.getAsDouble(); } finally { ~after(); }
    }
    
    public static <T> InvokeContext overlayInvokeContext(final ThreadLocal<T> local, final @Nullable T overlay, final ThreadLocal<T> prev = { }) = {
            () -> {
                prev.set(local.get());
                local.set(overlay);
            },
            () -> {
                local.set(prev.get());
                prev.remove();
            }
    };
    
    public static <T> InvokeContext fixedInvokeContext(final ThreadLocal<T> local, final @Nullable T before, final @Nullable T after) = { () -> local.set(before), () -> local.set(after) };
    
}
