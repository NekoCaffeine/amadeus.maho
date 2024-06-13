package amadeus.maho.util.link.http;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import amadeus.maho.core.MahoExport;
import amadeus.maho.lang.Getter;
import amadeus.maho.util.logging.LogLevel;

public record HttpSetting(
        Map<String, String> headers = baseHeaders,
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(3)).version(HttpClient.Version.HTTP_1_1).build(),
        int maxRetries = 2,
        BiConsumer<LogLevel, String> logger = MahoExport.namedLogger("HttpClient")
) {
    
    public static final Map<String, String> baseHeaders = Map.of(HttpHelper.Header.User_Agent, STR."Maho/\{MahoExport.VERSION}");
    
    @Getter
    private static final HttpSetting defaultInstance = { };
    
    public static Map<String, String> withBaseHeaders(final Map<String, String> headers) = Map.copyOf(new HashMap<>(headers) *= baseHeaders);
    
}
