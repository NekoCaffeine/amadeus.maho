package amadeus.maho.util.logging;

import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.ToString;

import static java.lang.Integer.*;

@ToString
@EqualsAndHashCode
public record LogLevel(String name, int value) implements Comparable<LogLevel> {
    
    public static LogLevel
            // @formatter:off
            OFF     = { "OFF"    , MAX_VALUE },
            FATAL   = { "FATAL"  , 1100      },
            ALERT   = { "ALERT"  , 1000      },
            ERROR   = { "ERROR"  , 950       },
            WARNING = { "WARNING", 900       },
            NOTICE  = { "NOTICE" , 850       },
            INFO    = { "INFO"   , 800       },
            DEBUG   = { "DEBUG"  , 600       },
            TRACE   = { "TRACE"  , 300       },
            ALL     = { "ALL"    , MIN_VALUE };
            // @formatter:on
    
    public static LogLevel findLogLevel(final String name) {
        try { return (LogLevel) LogLevel.class.getField(name.toUpperCase()).get(null); } catch (final NoSuchFieldException | IllegalAccessException e) { return DEBUG; }
    }
    
    @Override
    public int compareTo(final LogLevel target) = value() - target.value();
    
}
