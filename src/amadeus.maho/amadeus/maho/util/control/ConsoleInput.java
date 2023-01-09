package amadeus.maho.util.control;

import java.io.InputStream;
import java.util.Scanner;
import java.util.function.Consumer;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;

@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConsoleInput {
    
    Scanner scanner;
    
    public void poll(final Consumer<String> consumer) {
        final Scanner scanner = scanner();
        if (scanner.hasNextLine())
            consumer.accept(scanner.next());
    }
    
    public void pollAll(final Consumer<String> consumer) {
        final Scanner scanner = scanner();
        while (scanner.hasNextLine())
            consumer.accept(scanner.next());
    }
    
    public static ConsoleInput ofInputStream(final InputStream source) = { new Scanner(source) };
    
}
