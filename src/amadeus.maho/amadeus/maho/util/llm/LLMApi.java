package amadeus.maho.util.llm;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import amadeus.maho.core.MahoExport;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Unsupported;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.data.JSON;
import amadeus.maho.util.dynamic.DynamicObject;
import amadeus.maho.util.link.http.HttpApi;
import amadeus.maho.util.llm.backend.OpenRouter;
import amadeus.maho.util.logging.LogLevel;
import amadeus.maho.util.runtime.ArrayHelper;
import amadeus.maho.util.runtime.DebugHelper;

public interface LLMApi {
    
    @Unsupported
    class UnsupportedInstance implements LLMApi { }
    
    BiConsumer<LogLevel, String> logger = MahoExport.namedLogger();
    
    List<Supplier<? extends LLMApi>> implementations = List.of(OpenRouter::make);
    
    @Getter
    LLMApi defaultInstance = makeDefaultInstance();
    
    static LLMApi makeDefaultInstance() = ~implementations.stream().safeMap(Supplier::get, HttpApi.MissingTokenException.class, (_, e) -> {
        logger[LogLevel.DEBUG] = STR."Missing variable: \{e.getMessage()}";
        return null;
    }) ?? new UnsupportedInstance();
    
    String defaultModel();
    
    DynamicObject defaultRequestParameters();
    
    DynamicObject withModel(DynamicObject parameters, String model);
    
    DynamicObject withContent(DynamicObject parameters = defaultRequestParameters(), String content);
    
    default DynamicObject beforeInvoke(final DynamicObject request, final Method method, final String model, final boolean structuredOutputs) = request;
    
    DynamicObject send(DynamicObject request);
    
    String result(DynamicObject response);
    
    String completions(DynamicObject body);
    
    default String completions(final String content) = completions(withContent(content));
    
    default Object invoke(final Method method, final Object... args) {
        final boolean isStatic = Modifier.isStatic(method.getModifiers());
        final @Nullable Object instance = isStatic ? null : args[0];
        final LLM llm = method.getAnnotation(LLM.class);
        final LLM.ParametersProvider provider = instance instanceof LLM.ParametersProvider p ? p : llm.parameter().isInterface() ? LLM.ParametersProvider.empty : llm.parameter().defaultInstance();
        final Class<?> returnType = method.getReturnType();
        final String model = (instance instanceof LLM.ModelContext context ? context.model() : null) ?? model(llm);
        final boolean structuredOutputs = supportStructuredOutputs(model);
        final Object arguments[] = isStatic ? args : ArrayHelper.sub(args, 1);
        final DynamicObject sourceRequest = withModel(withContent(defaultRequestParameters(), makeInvokePrompt(method, structuredOutputs, arguments)), model).let(provider);
        final DynamicObject request = beforeInvoke(sourceRequest, method, model, structuredOutputs);
        final DynamicObject response = send(request);
        final String result = result(response);
        if (result.isEmpty())
            throw DebugHelper.breakpointBeforeThrow(new AssertionError(STR."Empty result with: \{JSON.stringify(request)}"));
        final DynamicObject parsed = JSON.parse(tryFixJson(result));
        final @Nullable String typeOf = LLMJSONSchema.typeMap[returnType];
        return (typeOf != null || returnType.isArray() ? parsed["value"] : parsed).as(returnType)!;
    }
    
    default String tryFixJson(final String text) = skipMarkdown(text);
    
    default String model(final LLM llm) = llm.model().isEmpty() ? defaultModel() : llm.model();
    
    default boolean supportStructuredOutputs(final String model) = false;
    
    default String makeInvokePrompt(final Method method, final LLM annotation = method.getAnnotation(LLM.class), final boolean structuredOutputs,
            final Parameter parameters[] = method.getParameters(), final Object... args) = STR."""
            Please simulate the following method call:
            \{method.getName()}(\{IntStream.range(0, method.getParameterCount()).mapToObj(index -> STR."\{parameters[index].getName()} = \{JSON.stringify(args[index])}").collect(Collectors.joining(", "))})
            \{annotation.value().isEmpty() ? "" : STR."Additional information: \{annotation.value()}"}
            \{structuredOutputs ? "" : LLMJSONSchema.responseFormatLocal[method.getReturnType()] != null ? STR."""
                Return type: \{method.getReturnType().getName()}
                The return value is stored in the 'value' field of json.
                The JSON should be strictly formatted and contain no additional text or fields.
            """ : STR."""
                Don't use markdown to annotate the returned json.
                Return json schema: \{JSON.stringify(LLMJSONSchema.responseFormatLocal[method.getReturnType()])}"}
                The JSON should be strictly formatted and contain no additional text or fields.
            """}
            For array types, return empty array if no matches.
            """.trim();
    
    static boolean hasDefaultInstance() = !(defaultInstance instanceof UnsupportedInstance);
    
    static String skipDoubleQuotes(final String value) = value.startsWith("\\\"") && value.endsWith("\\\"") ? value.substring(2, value.length() - 2) :
            value.startsWith("\"") && value.endsWith("\"") ? value.substring(1, value.length() - 1) : value;
    
    static Object invokeDefaultInstance(final Method method, final Object... args) = defaultInstance.invoke(method, args);
    
    static String skipMarkdown(final String text) {
        if (text.startsWith("```json") && text.endsWith("```"))
            return text.substring(7, text.length() - 3).trim();
        return text;
    }
    
}
