package amadeus.maho.util.llm;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import amadeus.maho.core.MahoExport;
import amadeus.maho.core.extension.ShutdownHook;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.Init;
import amadeus.maho.transform.mark.base.TransformMetadata;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.runtime.DebugHelper;
import amadeus.maho.util.runtime.MethodHandleHelper;

@Init(initialized = true)
@SneakyThrows
@TransformProvider
public interface LLMThrowableAssistant {
    
    record AnalyzedResult(String reason, String suggestions[]) {
        
        @Override
        public String toString() = STR."""
                Reason:
                    \{reason}
                
                Suggestions:
                \{IntStream.range(0, suggestions.length).mapToObj(index -> STR."    \{index + 1}. \{suggestions[index]}").collect(Collectors.joining("\n"))}
                """;
        
    }
    
    @Nullable
    Class<?>
            WrappedPrintStream  = "java.lang.Throwable$WrappedPrintStream".tryLoad(),
            PrintStreamOrWriter = "java.lang.Throwable$PrintStreamOrWriter".tryLoad();
    
    @Nullable
    MethodHandle
            newWrappedPrintStream = WrappedPrintStream != null ? safeLookup(() -> MethodHandleHelper.lookup().findConstructor(WrappedPrintStream, MethodType.methodType(void.class, PrintStream.class))) : null,
            printStackTrace       = PrintStreamOrWriter != null ? safeLookup(() -> MethodHandleHelper.lookup().findVirtual(Throwable.class, "printStackTrace", MethodType.methodType(void.class, PrintStreamOrWriter))) : null;
    
    private static @Nullable MethodHandle safeLookup(final Supplier<MethodHandle> supplier) {
        try {
            return supplier.get();
        } catch (final Throwable throwable) { DebugHelper.breakpoint(throwable); }
        return null;
    }
    
    @Hook(metadata = @TransformMetadata(enable = MahoExport.MAHO_LLM_THROWABLE_ASSISTANT, order = 1 << 4))
    private static Hook.Result printStackTrace(final Throwable $this, final PrintStream stream) {
        if (newWrappedPrintStream != null && printStackTrace != null && !ShutdownHook.shuttingDown() && LLMApi.hasDefaultInstance() && MahoExport.debug())
            try {
                final ByteArrayOutputStream buffer = { };
                final PrintStream wrapped = { buffer, false, StandardCharsets.UTF_8 };
                printStackTrace.invoke($this, newWrappedPrintStream.invoke(wrapped));
                wrapped.flush();
                final String stackTrace = buffer.toString(StandardCharsets.UTF_8);
                final AnalyzedResult reasonsAndSuggestions = analyze(stackTrace);
                stream.println(STR."""
                \{stackTrace}
                \{reasonsAndSuggestions}""");
                return Hook.Result.NULL;
            } catch (final Throwable throwable) { DebugHelper.breakpoint(throwable); }
        return Hook.Result.VOID;
    }
    
    @LLM
    static AnalyzedResult analyze(final String stackTrace);
    
}
