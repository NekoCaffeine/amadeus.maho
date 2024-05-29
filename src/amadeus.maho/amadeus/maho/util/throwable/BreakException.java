package amadeus.maho.util.throwable;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.NoArgsConstructor;

@NoArgsConstructor(AccessLevel.PRIVATE)
public final class BreakException extends RuntimeException {
    
    public static final BreakException BREAK = { };
    
    @Override
    public Throwable fillInStackTrace() = this;
    
    public static void doBreakable(final Runnable runnable) { try { runnable.run(); } catch (final BreakException ignored) { } }

}
