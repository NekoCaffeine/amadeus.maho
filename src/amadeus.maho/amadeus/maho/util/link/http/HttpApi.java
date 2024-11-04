package amadeus.maho.util.link.http;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.StringConcatFactory;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import jdk.internal.net.http.ResponseSubscribers;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import amadeus.maho.core.MahoExport;
import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.annotation.mark.IndirectCaller;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.generator.MethodGenerator;
import amadeus.maho.util.data.JSON;
import amadeus.maho.util.dynamic.CallerContext;
import amadeus.maho.util.dynamic.LookupHelper;
import amadeus.maho.util.dynamic.Wrapper;
import amadeus.maho.util.misc.Environment;
import amadeus.maho.util.runtime.ArrayHelper;
import amadeus.maho.util.runtime.DebugHelper;
import amadeus.maho.util.runtime.MethodHandleHelper;
import amadeus.maho.util.runtime.ObjectHelper;
import amadeus.maho.util.type.TypeInferer;
import amadeus.maho.util.type.TypeToken;

@SneakyThrows
public interface HttpApi {
    
    interface Adapter {
        
        interface Mapping extends Adapter {
            
            Map<Type, MethodHandle> publisherMappers();
            
            Map<Type, MethodHandle> handlerMappers();
            
            @Override
            default @Nullable HttpRequest.BodyPublisher publisher(final Callable callable, final Object body) = (HttpRequest.BodyPublisher) publisherMappers()[callable.bodyType]?.invoke(body) ?? null;
            
            @Override
            default @Nullable HttpResponse.BodyHandler<?> handler(final Callable callable) = (HttpResponse.BodyHandler<?>) handlerMappers()[callable.returnType]?.invoke() ?? null;
            
            static Map<Type, MethodHandle> form(final Class<?> clazz, final Predicate<Method> predicate, final Function<Method, Type> typeFunction) = Stream.of(clazz.getDeclaredMethods())
                    .filter(method -> Modifier.isStatic(method.getModifiers()))
                    .filter(predicate)
                    .collect(Collectors.toMap(typeFunction, MethodHandleHelper.lookup()::unreflect));
            
        }
        
        @Getter
        @RequiredArgsConstructor
        @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
        class Fallback implements Adapter {
            
            Adapter adapter, fallback;
            
            @Override
            public HttpRequest.BodyPublisher publisher(final Callable callable, final Object body) = adapter().publisher(callable, body) ?? fallback().publisher(callable, body);
            
            @Override
            public HttpResponse.BodyHandler handler(final Callable callable) = responseInfo -> adapter().handler(callable)?.apply(responseInfo) ?? fallback().handler(callable)?.apply(responseInfo) ?? null;
            
        }
        
        @NoArgsConstructor(AccessLevel.PRIVATE)
        class Default implements Mapping {
            
            @Getter
            private static final Default instance = { };
            
            private static final TypeToken<?> bodyHandlerT = locateBodyHandlerT();
            
            private static <T> TypeToken<T> locateBodyHandlerT() = TypeToken.<T, HttpResponse.BodyHandler<T>>locate();
            
            @Getter(nonStatic = true)
            private static final Map<Type, MethodHandle>
                    publisherMappers = Mapping.form(HttpRequest.BodyPublishers.class, method -> method.getParameterCount() == 1, method -> method.getParameters()[0].getParameterizedType());
            
            static { publisherMappers[Void.class] = LookupHelper.methodHandle0(HttpRequest.BodyPublishers::noBody); }
            
            @Getter(nonStatic = true)
            private static final Map<Type, MethodHandle>
                    handlerMappers = Mapping.form(HttpResponse.BodyHandlers.class, method -> method.getParameterCount() == 0, method -> TypeInferer.infer(bodyHandlerT, method.getGenericReturnType()).erasedType());
            
            static { handlerMappers[void.class] = LookupHelper.methodHandle0(HttpResponse.BodyHandlers::discarding); }
            
        }
        
        class Json implements Adapter {
            
            @Getter
            private static final Json instance = { };
            
            @Override
            public HttpRequest.BodyPublisher publisher(final Callable callable, final @Nullable Object body) = HttpRequest.BodyPublishers.ofString(JSON.Writer.write(body));
            
            @Override
            public @Nullable HttpResponse.BodyHandler handler(final Callable callable) = responseInfo -> MediaType.charsetBySubType(responseInfo, "json") instanceof Charset charset ?
                    new ResponseSubscribers.ByteArraySubscriber<>(bytes -> JSON.Dynamic.read(new String(bytes, charset))) : null;
            
        }
        
        @Nullable HttpRequest.BodyPublisher publisher(Callable callable, Object body);
        
        @Nullable HttpResponse.BodyHandler handler(Callable callable);
        
    }
    
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Header {
        
        String value() default "";
        
    }
    
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Body { }
    
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Endpoint {
        
        String value();
        
        String[] headers() default { };
        
    }
    
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Request {
        
        String endpoint() default "";
        
        String path();
        
        String method();
        
        String[] headers() default { };
        
        int timeout() default 3 * 1000;
        
        HttpClient.Version version() default HttpClient.Version.HTTP_1_1;
        
    }
    
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
    class Callable {
        
        HttpSetting setting;
        
        Adapter adapter;
        
        Method method;
        
        Endpoint endpoint;
        
        Request request;
        
        String realEndpoint = request.endpoint().isEmptyOr(endpoint.value());
        
        int pathParameterCount = (int) Stream.of(method.getParameters()).takeWhile(parameter -> !parameter.isAnnotationPresent(Body.class)).count();
        
        {
            if (method.getParameterCount() > pathParameterCount + 1)
                throw DebugHelper.breakpointBeforeThrow(new IllegalStateException(STR."Cannot be mapped to \{Callable.class.getCanonicalName()}: \{method}"));
        }
        
        Type bodyType = method.getParameterCount() == pathParameterCount ? Void.class : method.getParameters()[pathParameterCount].getParameterizedType();
        
        Type returnType = method.getGenericReturnType();
        
        ParameterizedHeader parameterizedHeaders[] = ParameterizedHeader.parse(method.getParameters());
        
        @Nullable
        ParameterizedPath parameterizedPath = ParameterizedPath.parse(request.path(), method.getParameters());
        
        Object cache = makeCache();
        
        public static final org.objectweb.asm.Type type = org.objectweb.asm.Type.getType(Callable.class);
        
        public static final org.objectweb.asm.commons.Method call = { "call", org.objectweb.asm.Type.getMethodDescriptor(ASMHelper.TYPE_OBJECT, ASMHelper.TYPE_OBJECT_ARRAY) };
        
        protected Object makeCache() {
            final HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .method(request.method(), HttpRequest.BodyPublishers.noBody())
                    .let(it -> setting.headers().forEach(it::header));
            if (endpoint.headers().length > 0)
                builder.headers(endpoint.headers());
            if (request.headers().length > 0)
                builder.headers(request.headers());
            if (parameterizedPath == null) {
                builder.uri(URI.create(realEndpoint + request.path()));
                if (bodyType == Void.class && parameterizedHeaders.length == 0)
                    return builder.build();
            }
            return builder;
        }
        
        public Object call(final Object... args) = switch (cache) {
            case HttpRequest request         -> setting.client().send(request, ObjectHelper.requireNonNull(adapter.handler(this))).body();
            case HttpRequest.Builder builder -> {
                final HttpRequest.Builder copy = builder.copy();
                if (parameterizedHeaders.length > 0)
                    Stream.of(parameterizedHeaders).forEach(header -> {
                        switch (args[header.index]) {
                            case Map<?, ?> map -> map.forEach((key, value) -> copy.header(String.valueOf(key), String.valueOf(value)));
                            case Object object -> copy.header(header.name(), String.valueOf(object));
                            case null          -> { }
                        }
                    });
                if (parameterizedPath != null)
                    copy.uri(URI.create(realEndpoint + parameterizedPath.merge(args)));
                if (bodyType != Void.class) {
                    final Object body = args[pathParameterCount];
                    copy.method(request.method(), ObjectHelper.requireNonNull(adapter.publisher(this, body)));
                    if (bodyType == Path.class && body instanceof Path path)
                        copy.header(HttpHelper.Header.Content_Type, URLConnection.getFileNameMap().getContentTypeFor(path.getFileName().toString()));
                }
                yield setting.client().send(copy.build(), ObjectHelper.requireNonNull(adapter.handler(this))).body();
            }
            default                          -> throw new IllegalStateException(STR."Unexpected value: \{cache}");
        };
        
    }
    
    record ParameterizedHeader(String name, int index) {
        
        public static ParameterizedHeader[] parse(final Parameter parameters[]) = IntStream.range(0, parameters.length)
                .filter(index -> parameters[index].isAnnotationPresent(Header.class))
                .mapToObj(index -> new ParameterizedHeader(parameters[index].getName().isEmptyOr(parameters[index].getName()), index))
                .toArray(ParameterizedHeader[]::new);
        
    }
    
    record ParameterizedPath(MethodHandle merger, int indexes[]) {
        
        public static final Pattern parameter = Pattern.compile("\\{(.*?)}"), query = Pattern.compile("\\{\\?(.*?)}");
        
        private static final String TAG_ARG = "\u0001";
        
        public String merge(final Object... args) = (String) merger().invokeWithArguments((Object[]) IntStream.of(indexes()).mapToObj(index -> URLEncoder.encode(String.valueOf(args[index]), StandardCharsets.UTF_8)).toArray(String[]::new));
        
        public static @Nullable ParameterizedPath parse(final String path, final Parameter parameters[]) {
            String withQueryPath = path;
            {
                final Matcher matcher = query.matcher(path);
                if (matcher.find()) {
                    final String queryParameters[] = matcher.group(1).split(",");
                    if (matcher.find())
                        throw DebugHelper.breakpointBeforeThrow(new IllegalArgumentException(STR."Invalid path with more than one optional query parameter list: \{path}"));
                    withQueryPath = matcher.replaceFirst(Stream.of(queryParameters).map(it -> STR."\{it}={\{it}}").collect(Collectors.joining("&", "?", "")));
                }
            }
            final Matcher matcher = parameter.matcher(withQueryPath);
            final int indexes[] = new int[parameters.length];
            int length = 0;
            while (matcher.find()) {
                final String group = matcher.group(1);
                for (int index = 0; index < parameters.length; index++)
                    if (parameters[index].getName().equals(group)) {
                        indexes[length++] = index;
                        break;
                    }
            }
            if (length == 0)
                return null;
            final MethodType concatMethodType = MethodType.methodType(String.class, Stream.generate(() -> String.class).limit(length).toArray(Class[]::new));
            return {
                    StringConcatFactory.makeConcatWithConstants(MethodHandleHelper.lookup(), "merge", concatMethodType, matcher.replaceAll(TAG_ARG)).getTarget(),
                    ArrayHelper.sub(indexes, 0, length)
            };
        }
        
    }

    @NoArgsConstructor
    class MissingTokenException extends RuntimeException { }
    
    String root();
    
    HttpSetting setting();
    
    @IndirectCaller
    static <T extends HttpApi> T make(final HttpSetting setting = HttpSetting.defaultInstance(), final Adapter adapter = Adapter.Default.instance(), final Class<T> apiInterface = CallerContext.caller()) {
        final @Nullable Endpoint endpoint = apiInterface.getAnnotation(Endpoint.class);
        if (endpoint == null)
            throw DebugHelper.breakpointBeforeThrow(new IllegalStateException(STR."Class \{apiInterface.getCanonicalName()} missing @Endpoint"));
        final String root = endpoint.value();
        final Wrapper<T> wrapper = { apiInterface, "HttpApiInstance" };
        final org.objectweb.asm.Type wrapperType = wrapper.wrapperType();
        final ClassNode node = wrapper.node();
        final FieldNode rootField = wrapper.field(String.class, "root"), settingField = wrapper.field(HttpSetting.class, "setting");
        wrapper.copyAllConstructors(rootField, settingField);
        Stream.of(rootField, settingField).forEach(fieldNode -> ASMHelper.generateGetter(node, fieldNode));
        final HashMap<FieldNode, Callable> callableMap = { };
        wrapper.unimplementedMethods().forEach(method -> {
            final @Nullable Request request = method.getAnnotation(Request.class);
            if (request != null) {
                final FieldNode field = wrapper.field(Callable.class, "$%s_%d".formatted(method.getName(), callableMap.size()));
                callableMap[field] = { setting, adapter, method, endpoint, request };
                final MethodGenerator generator = wrapper.wrap(method);
                generator.loadThis();
                generator.getField(wrapperType, field);
                generator.loadArgArray();
                generator.invokeVirtual(Callable.type, Callable.call);
                generator.checkCast(generator.returnType);
                generator.returnValue();
                generator.endMethod();
            }
        });
        wrapper.context().markCompute(wrapper.node());
        final Class<? extends T> wrapperClass = wrapper.defineWrapperClass();
        final T instance = (T) wrapperClass.getConstructors()[0].newInstance(root, setting);
        final MethodHandles.Lookup lookup = MethodHandleHelper.lookup();
        callableMap.forEach((fieldNode, callable) -> lookup.findSetter(wrapperClass, fieldNode.name, Callable.class).invoke(instance, callable));
        return instance;
    }
    
    @IndirectCaller
    static String token(final String variable = MahoExport.subKey("token")) {
        final @Nullable String token = Environment.local().lookup(variable);
        if (token == null)
            throw new MissingTokenException(variable);
        return token;
    }
    
    @IndirectCaller
    static Map<String, String> authorizationArgs(final String token = token(), final String type = "Bearer") = Map.of(HttpHelper.Header.Authorization, STR."\{type} \{token}");
    
    @IndirectCaller
    static HttpSetting authorization(final String token = token(), final String type = "Bearer") = { HttpSetting.withBaseHeaders(authorizationArgs(token, type)) };
    
}
