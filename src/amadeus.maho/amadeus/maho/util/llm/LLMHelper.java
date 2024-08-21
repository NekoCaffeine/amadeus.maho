package amadeus.maho.util.llm;

public interface LLMHelper {
    
    @LLM
    static String ask(final String question);
    
}
