package amadeus.maho.vm;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.misc.ConstantLookup;

public class IllegalVersionException extends IllegalArgumentException {
    
    public IllegalVersionException() { }
    
    public IllegalVersionException(final String message) = super(message);
    
    public IllegalVersionException(final String message, final Throwable cause) = super(message, cause);
    
    public IllegalVersionException(final Throwable cause) = super(cause);
    
    public static boolean checkNumberEquals(final Number n1, final Number n2) = n1.floatValue() == n2.floatValue();
    
    public static boolean invalid(final Number version, final ConstantLookup lookup) = lookup.constantMapping().values().stream()
            .cast(Number.class)
            .noneMatch(number -> checkNumberEquals(number, version));
    
    public static void checkVersion(final Number version, final ConstantLookup lookup, final @Nullable Predicate<String> predicate) {
        if (invalid(version, lookup)) {
            final StringBuilder supportedVersions = { "List of supported versions:" };
            Stream<Map.Entry<Field, Object>> stream = lookup.constantMapping().entrySet().stream()
                    .filter(e -> e.getValue() instanceof Number);
            if (predicate != null)
                stream = stream.filter(e -> predicate.test(e.getKey().getName()));
            stream.forEach(e -> {
                final String name = e.getKey().getName(), number = e.getValue().toString();
                supportedVersions.ensureCapacity(name.length() + number.length() + 1 + 4 + 2);
                supportedVersions
                        .append('\n')
                        .append("    ")
                        .append(name)
                        .append('(')
                        .append(number)
                        .append(')');
            });
            throw new IllegalArgumentException(STR."""
Unsupported version: \{version}
\{supportedVersions}""");
        }
    }
    
}
