package amadeus.maho.util.link.http;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.function.BiConsumer;

import amadeus.maho.core.MahoExport;
import amadeus.maho.lang.Getter;
import amadeus.maho.util.logging.LogLevel;

public record HttpSetting(
        int maxRetries = 3,
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(3)).version(HttpClient.Version.HTTP_1_1).build(),
        HttpRequest.Builder baseDownloadRequest = HttpRequest.newBuilder().GET().header(HttpHelper.Header.User_Agent, "Maho/%s".formatted(MahoExport.VERSION)),
        BiConsumer<LogLevel, String> logger = MahoExport.logger().namedLogger("HttpClient")
) {
    
    @Getter
    private static final HttpSetting defaultInstance = { };
    
}
