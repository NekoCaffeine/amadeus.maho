package amadeus.maho.util.throwable;

import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.util.runtime.UnsafeHelper;

@NoArgsConstructor
public final class BreakException extends RuntimeException {
    
    @Getter
    private static final BreakException instance = UnsafeHelper.allocateInstanceOfType(BreakException.class);
    
    @Override
    public Throwable fillInStackTrace() = this;
    
    public static void doBreakable(final Runnable runnable) { try { runnable.run(); } catch (final BreakException ignored) { } }

}
