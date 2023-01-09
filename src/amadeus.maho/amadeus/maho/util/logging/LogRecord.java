package amadeus.maho.util.logging;

import java.time.Instant;

import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.dynamic.CallerContext;

@ToString
@EqualsAndHashCode
public record LogRecord(String name, LogLevel level, String message, Instant instant = Instant.now(), @Nullable StackWalker.StackFrame frame = CallerContext.callerFrame(), Thread thread = Thread.currentThread()) { }
