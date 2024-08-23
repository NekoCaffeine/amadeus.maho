package amadeus.maho.util.llm;

public interface LLMHelper {
    
    @LLM
    static String ask(String question);
    
}
