package amadeus.maho.util.llm.backend;

import java.lang.reflect.Method;
import java.util.Set;

import amadeus.maho.core.MahoExport;
import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.MagicValue;
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.data.JSON;
import amadeus.maho.util.dynamic.DynamicObject;
import amadeus.maho.util.link.http.HttpApi;
import amadeus.maho.util.link.http.HttpHelper;
import amadeus.maho.util.link.http.HttpSetting;
import amadeus.maho.util.llm.LLMJSONSchema;
import amadeus.maho.util.misc.Environment;
import amadeus.maho.util.runtime.DebugHelper;

import static amadeus.maho.util.link.http.HttpHelper.RequestType.*;

@HttpApi.Endpoint(value = "https://openrouter.ai/api/v1", headers = { HttpHelper.Header.Accept, "application/json", "X-Title", "amadeus.maho" })
public interface OpenRouter extends LLMHttpBackend {
    
    interface Models {
        
        String
                OPENAI_CHATGPT_4O_MINI_LATEST    = "openai/gpt-4o-mini-2024-07-18",
                OPENAI_CHATGPT_4O_LATEST         = "openai/chatgpt-4o-latest",
                OPENAI_CHATGPT_4O_08_06          = "openai/gpt-4o-2024-08-06",
                ANTHROPIC_CLAUDE_3_5_SONNET_BETA = "anthropic/claude-3.5-sonnet:beta";
        
        Set<String> supportStructuredOutputs = Set.of(OPENAI_CHATGPT_4O_MINI_LATEST, OPENAI_CHATGPT_4O_LATEST, OPENAI_CHATGPT_4O_08_06);
        
    }
    
    interface FunctionTypes {
        
        String
                function = "function";
        
    }
    
    interface CompletionTypes {
        
        String
                chat_completion       = "chat.completion",
                chat_completion_chunk = "chat.completion.chunk";
        
    }
    
    @ToString
    @EqualsAndHashCode
    record Error(int code, String message) { }
    
    @ToString
    @EqualsAndHashCode
    record FunctionCall(String name, String arguments) {
        
        public DynamicObject argumentsInstance() = JSON.parse(arguments());
        
        public FunctionCall(final String name, final DynamicObject arguments) = this(name, JSON.stringify(arguments));
        
    }
    
    @ToString
    @EqualsAndHashCode
    record ToolCall(String id, @MagicValue(FunctionTypes.class) String type, FunctionCall function) { }
    
    @ToString
    @EqualsAndHashCode
    record Message(String role, String content, @Nullable ToolCall tool_calls[]) { }
    
    @Setter
    interface Parameters {
        
        // Optional, float, 0.0 to 2.0, Default: 1.0
        // This setting influences the variety in the model's responses. Lower values lead to more predictable and typical responses, while higher values encourage more diverse and less common responses.
        // At 0, the model always gives the same response for a given input.
        default float temperature() = 1.0F;
        
        // Optional, float, 0.0 to 1.0, Default: 1.0
        // This setting limits the model's choices to a percentage of likely tokens: only the top tokens whose probabilities add up to P.
        // A lower value makes the model's responses more predictable, while the default setting allows for a full range of token choices.
        // Think of it like a dynamic Top-K.
        default float top_p() = 1.0F;
        
        // Optional, integer, 0 or above, Default: 0
        // This limits the model's choice of tokens at each step, making it choose from a smaller set.
        // A value of 1 means the model will always pick the most likely next token, leading to predictable results.
        // By default this setting is disabled, making the model to consider all choices.
        default int top_k() = 0;
        
        // Optional, float, -2.0 to 2.0, Default: 0.0
        // This setting aims to control the repetition of tokens based on how often they appear in the input.
        // It tries to use less frequently those tokens that appear more in the input, proportional to how frequently they occur.
        // Token penalty scales with the number of occurrences. Negative values will encourage token reuse.
        default float frequency_penalty() = 0.0F;
        
        // Optional, float, -2.0 to 2.0, Default: 0.0
        // Adjusts how often the model repeats specific tokens already used in the input.
        // Higher values make such repetition less likely, while negative values do the opposite.
        // Token penalty does not scale with the number of occurrences. Negative values will encourage token reuse.
        default float presence_penalty() = 0.0F;
        
        // Optional, float, 0.0 to 2.0, Default: 1.0
        // Helps to reduce the repetition of tokens from the input. A higher value makes the model less likely to repeat tokens,
        // but too high a value can make the output less coherent (often with run-on sentences that lack small words).
        // Token penalty scales based on original token's probability.
        default float repetition_penalty() = 1.0F;
        
        // Optional, float, 0.0 to 1.0, Default: 0.0
        // Represents the minimum probability for a token to be considered, relative to the probability of the most likely token.
        // (The value changes depending on the confidence level of the most probable token.)
        // If your Min-P is set to 0.1, that means it will only allow for tokens that are at least 1/10th as probable as the best possible option.
        default float min_p() = 0.0F;
        
        // Optional, float, 0.0 to 1.0, Default: 0.0
        // Consider only the top tokens with "sufficiently high" probabilities based on the probability of the most likely token.
        // Think of it like a dynamic Top-P. A lower Top-A value focuses the choices based on the highest probability token but with a narrower scope.
        // A higher Top-A value does not necessarily affect the creativity of the output, but rather refines the filtering process based on the maximum probability.
        default float top_a() = 0.0F;
        
        // Optional, integer
        // If specified, the inferencing will sample deterministically, such that repeated requests with the same seed and parameters should return the same result.
        // Determinism is not guaranteed for some models.
        int seed();
        
        // Optional, integer, 1 or above
        // This sets the upper limit for the number of tokens the model can generate in response. It won't produce more than this limit.
        // The maximum value is the context length minus the prompt length.
        int max_tokens();
        
        // Optional, map
        // Accepts a JSON object that maps tokens (specified by their token ID in the tokenizer) to an associated bias value from -100 to 100.
        // Mathematically, the bias is added to the logits generated by the model prior to sampling. The exact effect will vary per model,
        // but values between -1 and 1 should decrease or increase likelihood of selection; values like -100 or 100 should result in a ban or exclusive selection of the relevant token.
        DynamicObject.MapUnit logit_bias();
        
        // Optional, boolean
        // Whether to return log probabilities of the output tokens or not. If true, returns the log probabilities of each output token returned.
        boolean logprobs();
        
        // Optional, integer
        // An integer between 0 and 20 specifying the number of most likely tokens to return at each token position, each with an associated log probability.
        // logprobs must be set to true if this parameter is used.
        int top_logprobs();
        
        // Optional, map
        // Forces the model to produce specific output format. Setting to { "type": "json_object" } enables JSON mode, which guarantees the message the model generates is valid JSON.
        // Note: when using JSON mode, you should also instruct the model to produce JSON yourself via a system or user message.
        DynamicObject.MapUnit response_format();
        
        // Optional, array
        // Stop generation immediately if the model encounter any token specified in the stop array.
        DynamicObject.ArrayUnit stop();
        
        // Optional, array
        // Tool calling parameter. Will be passed down as-is for providers implementing OpenAI's interface.
        // For providers with custom interfaces, we transform and map the properties. Otherwise, we transform the tools into a YAML template.
        // The model responds with an assistant message.
        DynamicObject.ArrayUnit tools();
        
        // Optional, array
        // Tool choice parameter. Controls which (if any) tool is called by the model.
        // 'none' means the model will not call any tool and instead generates a message.
        // 'auto' means the model can pick between generating a message or calling one or more tools.
        // 'required' means the model must call one or more tools.
        // Specifying a particular tool via {"type": "function", "function": {"name": "my_function"}} forces the model to call that tool.
        DynamicObject.ArrayUnit tool_choice();
        
    }
    
    @Setter
    interface CompletionsRequest extends Parameters {
        
        // Either "messages" or "prompt" is required
        @Nullable
        Message[] messages();
        
        @Nullable
        String prompt();
        
        // If "model" is unspecified, uses the user's default
        @Nullable
        String model();
        
        // SSE
        boolean stream();
        
        // OpenRouter-only parameters
        
        // See "Prompt Transforms" section: openrouter.ai/docs/transforms
        DynamicObject.ArrayUnit transforms();
        
        // See "Model Routing" section: openrouter.ai/docs/model-routing
        DynamicObject.ArrayUnit models();
        
        // See "Provider Routing" section: openrouter.ai/docs/provider-routing
        @Nullable
        String route();
        
    }
    
    interface Choice {
        
        @ToString
        @EqualsAndHashCode
        record NonChatChoice(@Nullable String finish_reason, String text, @Nullable Error error) implements Choice { }
        
        @ToString
        @EqualsAndHashCode
        record NonStreamingChoice(@Nullable String finish_reason, Message message, @Nullable Error error) implements Choice { }
        
        @ToString
        @EqualsAndHashCode
        record StreamingChoice(@Nullable String finish_reason, Message delta, @Nullable Error error) implements Choice { }
        
        @Nullable
        String finish_reason();
        
        @Nullable
        Error error();
        
    }
    
    @ToString
    @EqualsAndHashCode
    record ResponseUsage(int prompt_tokens, int completion_tokens, int total_tokens) { }
    
    @ToString
    @EqualsAndHashCode
    record Response(String id, Choice choices[], int created, String model, @MagicValue(CompletionTypes.class) String object, @Nullable String system_fingerprint) { }
    
    String defaultModelKey = MahoExport.subKey("model");
    
    String defaultModel = Environment.local().lookup(defaultModelKey, Models.OPENAI_CHATGPT_4O_MINI_LATEST);
    
    @Override
    default String defaultModel() = defaultModel;
    
    @Override
    default boolean supportStructuredOutputs(final String model) = Models.supportStructuredOutputs.contains(model);
    
    @Override
    default DynamicObject beforeInvoke(final DynamicObject request, final Method method, final String model, final boolean structuredOutputs) {
        if (structuredOutputs)
            request["response_format"] = LLMJSONSchema.responseFormatLocal[method.getReturnType()];
        return request;
    }
    
    @Override
    default DynamicObject defaultRequestParameters() = withModel(new DynamicObject.MapUnit(), defaultModel);
    
    @Override
    default DynamicObject withModel(final DynamicObject parameters, final String model) = parameters.let(it -> it["model"] = model);
    
    @Override
    default DynamicObject withContent(final DynamicObject parameters, final String content) = parameters.let(it -> it["prompt"] = content.replace("\"", "\\\""));
    
    @Override
    @Request(method = POST, path = "/chat/completions")
    DynamicObject send(@Body DynamicObject body);
    
    @Request(method = GET, path = "/models")
    DynamicObject models();
    
    @Request(method = GET, path = "/auth/key")
    DynamicObject key();
    
    default DynamicObject checkError(final DynamicObject response) {
        if (response["error"].undefinedToNull().as(Error.class) instanceof Error error)
            throw DebugHelper.breakpointBeforeThrow(new RequestFailedException(STR."Error \{error.code}: \{error.message}"));
        final DynamicObject first = response["choices"].undefinedToNull()[0].undefinedToNull();
        if (first.as(Choice.class) instanceof Choice choice) {
            if (choice.error() instanceof Error error)
                throw DebugHelper.breakpointBeforeThrow(new RequestFailedException(STR."Error \{error.code}: \{error.message}"));
            switch (choice.finish_reason()) {
                case "length"         -> throw new LengthException("The completion is too long.");
                case "content_filter" -> throw new ContentFilterException("The completion was filtered by the content filter.");
                case null,
                     default          -> { }
            }
            if (first["message"].undefinedToNull()["refusal"].undefinedToNull().as() instanceof String refusal)
                throw new RefusalException(refusal);
        }
        return response;
    }
    
    @Override
    default String result(final DynamicObject response) = checkError(response)["choices"][0]["text"].asString();
    
    @Override
    default String completions(final DynamicObject body) = result(send(body));
    
    static OpenRouter make(final HttpSetting setting = HttpApi.authorization()) = HttpApi.make(setting, adapter);
    
}
