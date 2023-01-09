package amadeus.maho.util.logging;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class LoggerFormatter {
    
    public static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());
    
    public static String format(final LogRecord record) = "[" + LocalDateTime.ofInstant(record.instant(), ZoneId.systemDefault()).format(formatter) + "] " +
                                                      "[" + record.thread().getName() + "] " +
                                                      "<" + record.level().name() + "> " +
                                                      "[" + record.name() + "]" +
                                                      " : " + record.message() + '\n';
    
}
