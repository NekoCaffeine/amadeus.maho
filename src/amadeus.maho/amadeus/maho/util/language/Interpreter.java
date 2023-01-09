package amadeus.maho.util.language;

import java.util.concurrent.CompletableFuture;

import amadeus.maho.lang.inspection.Nullable;

public interface Interpreter {
    
    interface Owner {
        
        Interpreter interpreter();
        
    }
    
    Language owner();
    
    CompletableFuture<?> interpret(String source);
    
    // just-in-time compiler
    default @Nullable Compiler<Runnable> jitCompiler() = null;
    
}
