package amadeus.maho.util.llm.backend;

import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.util.link.http.HttpApi;
import amadeus.maho.util.llm.LLMApi;

public interface LLMHttpBackend extends LLMApi, HttpApi {
    
    @NoArgsConstructor
    class RequestFailedException extends RuntimeException { }
    
    @NoArgsConstructor
    class LengthException extends RequestFailedException { }
    
    @NoArgsConstructor
    class RefusalException extends RequestFailedException { }
    
    @NoArgsConstructor
    class ContentFilterException extends RequestFailedException { }
    
    Adapter.Fallback adapter = { Adapter.Default.instance(), Adapter.Json.instance() };
    
}
