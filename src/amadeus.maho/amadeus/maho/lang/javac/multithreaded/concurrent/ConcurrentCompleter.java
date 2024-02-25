package amadeus.maho.lang.javac.multithreaded.concurrent;

import com.sun.tools.javac.code.Symbol;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.RequiredArgsConstructor;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
public class ConcurrentCompleter implements Symbol.Completer {
    
    Symbol.Completer source;
    
    @Override
    public synchronized void complete(final Symbol sym) throws Symbol.CompletionFailure = source.complete(sym);
    
    @Override
    public boolean isTerminal() = source.isTerminal();
    
    @Override
    public String toString() = STR."Sync-\{source.toString()}";
    
}
