package amadeus.maho.util.depend;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.Delegate;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.control.Interrupt;
import amadeus.maho.util.link.http.HttpSetting;
import amadeus.maho.util.throwable.RetryException;

import static amadeus.maho.util.link.http.HttpHelper.StatesCode.*;
import static amadeus.maho.util.logging.LogLevel.*;

@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public abstract class CacheableHttpRepository implements Repository {

    Path cacheDir;

    String rootUrl;

    @Default
    @Getter(on = @Delegate)
    HttpSetting setting = HttpSetting.defaultInstance();

    ConcurrentHashMap<Project.Dependency, CompletableFuture<Collection<Project.Dependency>>> recursiveResolveCache = { };

    @Override
    public String debugInfo() = getClass().getSimpleName() + ": " + rootUrl();

    public abstract String uri(final Project project, final String extension);

    public boolean checkCacheCompleteness(final Path cache) = true;

    public void onDownloadCompleted(final Path relative, final Path cache, final boolean completenessMetadata) { }

    public Path relative(final Project project, final String extension) throws IOException = Path.of(uri(project, extension) + "." + extension);

    public Path cache(final Path relative, final Path cache = cacheDir() / relative) throws IOException {
        if (Files.exists(cache))
            if (checkCacheCompleteness(cache)) {
                logger().accept(DEBUG, "The local cache of %s detected, use it.".formatted(relative));
                return cache;
            } else
                logger().accept(WARNING, "The local cache of %s was detected, but it has been corrupted and will be downloaded again.".formatted(relative));
        else
            logger().accept(DEBUG, "The local cache of %s could not be detected and will be downloaded.".formatted(relative));
        return downloadDataFormRemote(relative, cache);
    }

    public @Nullable Path tryCache(final Path relative, final Path cache = cacheDir() / relative) { try { return cache(relative, cache); } catch (final IOException e) { return null; } }

    public HttpRequest.Builder request(final Path relative) = HttpRequest.newBuilder().GET().let(builder -> setting().headers().forEach(builder::header))
            .uri(URI.create(rootUrl() + relative.toString().replace(relative.getFileSystem().getSeparator(), "/")));

    @SneakyThrows
    public Path downloadDataFormRemote(final Path relative, final Path cache = cacheDir() / relative, final boolean completenessMetadata = false) throws IOException {
        int retries = maxRetries();
        final HttpRequest request = request(relative).build();
        logger().accept(DEBUG, "Downloading %s => %s".formatted(request.uri(), cache));
        final ArrayList<Throwable> throwables = { };
        do {
            try {
                ~-cache;
                return Interrupt.getUninterruptible(() -> client().sendMayThrow(request, info -> switch (info.statusCode()) {
                    case OK        -> HttpResponse.BodyHandlers.ofFile(cache--).apply(info);
                    case NOT_FOUND -> throw new FileNotFoundException(request.uri().toString());
                    default        -> throw new IOException("Response status code: %d (%s)".formatted(info.statusCode(), request.uri()));
                }).body()).let(it -> onDownloadCompleted(relative, it, completenessMetadata));
            } catch (final IOException e) {
                if (e instanceof FileNotFoundException notFoundEx)
                    throw notFoundEx;
                else
                    throwables += e;
            }
        } while (--retries > 0);
        throw new FileNotFoundException().let(it -> it.addSuppressed(new RetryException(throwables)));
    }

    @SneakyThrows
    public boolean exists(final Path relative) throws IOException {
        int retries = maxRetries();
        final HttpRequest request = request(relative).timeout(Duration.ofSeconds(5)).build();
        final ArrayList<Throwable> throwables = { };
        do {
            try {
                return Interrupt.getUninterruptible(() -> client().sendMayThrow(request, info -> switch (info.statusCode()) {
                    case OK        -> HttpResponse.BodySubscribers.replacing(true);
                    case NOT_FOUND -> HttpResponse.BodySubscribers.replacing(false);
                    default        -> throw new IOException("Response status code: %d (%s)".formatted(info.statusCode(), request.uri()));
                }).body());
            } catch (final IOException e) {
                throwables += e;
            }
        } while (--retries > 0);
        throw new IOException(new RetryException(throwables));
    }

}
