package amadeus.maho.util.depend;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.Delegate;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.control.Interrupt;
import amadeus.maho.util.link.http.HttpSetting;
import amadeus.maho.util.logging.progress.BaseIOTaskProgress;
import amadeus.maho.util.logging.progress.ProgressBar;
import amadeus.maho.util.throwable.RetryException;

import static amadeus.maho.util.concurrent.AsyncHelper.resolveExecutionException;
import static amadeus.maho.util.link.http.HttpHelper.StatesCode.*;
import static amadeus.maho.util.logging.LogLevel.*;

@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public abstract class CacheableHttpRepository implements Repository {
    
    @Getter
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class DownloadingTask<T> implements HttpResponse.BodySubscriber<T> {
        
        final HttpResponse.BodySubscriber<T> subscriber;
        
        final LongConsumer onNext;
        
        final Runnable onComplete;
        
        @Default
        final long interval = 1024;
        
        long receivedBytes = 0L, calledBytes = 0L;
        
        @Override
        public void onSubscribe(final Flow.Subscription subscription) = subscriber.onSubscribe(subscription);
        
        @Override
        public void onNext(final List<ByteBuffer> item) {
            receivedBytes += item.stream().mapToLong(ByteBuffer::remaining).sum();
            if (receivedBytes - calledBytes > interval) {
                onNext.accept(receivedBytes);
                calledBytes = receivedBytes;
            }
            subscriber.onNext(item);
        }
        
        @Override
        public void onError(final Throwable throwable) {
            onComplete.run();
            subscriber.onError(throwable);
        }
        
        @Override
        public void onComplete() {
            onComplete.run();
            subscriber.onComplete();
        }
        
        @Override
        public CompletionStage<T> getBody() = subscriber.getBody();
        
        @SneakyThrows
        public static <T> HttpResponse.BodySubscriber<T> ofProgressBar(final HttpResponse.BodySubscriber<T> subscriber, final String url, final long contentLength) {
            if (!ProgressBar.supported() || contentLength < 1)
                return subscriber;
            final BaseIOTaskProgress progress = { STR."Downloading: \{url}", contentLength };
            final ProgressBar<BaseIOTaskProgress> progressBar = { BaseIOTaskProgress.renderer, progress };
            return new DownloadingTask<>(subscriber, bytes -> {
                progress.update(bytes);
                progressBar.update();
            }, progressBar::close);
        }
        
    }
    
    Path cacheDir;
    
    String rootUrl;
    
    @Default
    @Getter(on = @Delegate)
    HttpSetting setting = HttpSetting.defaultInstance();
    
    ConcurrentHashMap<Project.Dependency, CompletableFuture<Collection<Project.Dependency>>> recursiveResolveCache = { };
    
    @Override
    public String debugInfo() = STR."\{getClass().getSimpleName()}: \{rootUrl()}";
    
    public abstract String uri(final Project project, final String extension);
    
    public boolean checkCacheCompleteness(final Path cache) = true;
    
    public void onDownloadCompleted(final Path relative, final Path cache, final boolean completenessMetadata) { }
    
    public Path relative(final Project project, final String extension) throws IOException = Path.of(STR."\{uri(project, extension)}.\{extension}");
    
    public Path cache(final Path relative, final Path cache = cacheDir() / relative) throws IOException {
        if (Files.exists(cache))
            if (checkCacheCompleteness(cache)) {
                logger().accept(DEBUG, STR."The local cache of \{relative} detected, use it.");
                return cache;
            } else
                logger().accept(WARNING, STR."The local cache of \{relative} was detected, but it has been corrupted and will be downloaded again.");
        else
            logger().accept(DEBUG, STR."The local cache of \{relative} could not be detected and will be downloaded.");
        return downloadDataFormRemote(relative, cache);
    }
    
    @SneakyThrows
    public @Nullable Path tryCache(final Supplier<Path> relative, final UnaryOperator<Path> cache = cacheDir()::resolve) {
        try {
            final Path path = relative.get();
            return cache(path, cache.apply(path));
        } catch (final Throwable throwable) {
            if (resolveExecutionException(throwable) instanceof RepositoryFileNotFoundException)
                return null;
            throw throwable;
        }
    }
    
    public HttpRequest.Builder request(final Path relative) = HttpRequest.newBuilder().GET().let(builder -> setting().headers().forEach(builder::header))
            .uri(URI.create(rootUrl() + relative.toString().replace(relative.getFileSystem().getSeparator(), "/")));
    
    @SneakyThrows
    public Path downloadDataFormRemote(final Path relative, final Path cache = cacheDir() / relative, final boolean completenessMetadata = false) throws IOException {
        int retries = maxRetries();
        final HttpRequest request = request(relative).build();
        logger().accept(DEBUG, STR."Downloading \{request.uri()} => \{cache}");
        final ArrayList<Throwable> throwables = { };
        do {
            try {
                ++cache;
                return Interrupt.getUninterruptible(() -> client().sendMayThrow(request, info -> {
                    final long contentLength = info.headers().firstValueAsLong("Content-Length").orElse(-1L);
                    return switch (info.statusCode()) {
                        case OK        -> {
                            final HttpResponse.BodySubscriber<Path> subscriber = HttpResponse.BodyHandlers.ofFile(cache--).apply(info);
                            yield contentLength > 0 ? DownloadingTask.ofProgressBar(subscriber, request.uri().toASCIIString(), contentLength) : subscriber;
                        }
                        case NOT_FOUND -> throw new RepositoryFileNotFoundException(request.uri().toString(), this);
                        default        -> throw new IOException("Response status code: %d (%s)".formatted(info.statusCode(), request.uri()));
                    };
                }).body()).let(it -> onDownloadCompleted(relative, it, completenessMetadata));
            } catch (final IOException e) {
                if (e instanceof RepositoryFileNotFoundException notFoundEx)
                    throw notFoundEx;
                else
                    throwables += e;
            }
        } while (--retries > 0);
        throw new RepositoryFileNotFoundException(request.uri().toString(), this).let(it -> it.addSuppressed(new RetryException(throwables)));
    }
    
    @SneakyThrows
    public boolean exists(final Path relative) throws IOException {
        int retries = maxRetries();
        final HttpRequest request = request(relative).HEAD().timeout(Duration.ofSeconds(5)).build();
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
