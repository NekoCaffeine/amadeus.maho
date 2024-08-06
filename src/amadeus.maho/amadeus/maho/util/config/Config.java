package amadeus.maho.util.config;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Builder;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.util.data.Cfg;
import amadeus.maho.util.data.Converter;
import amadeus.maho.util.event.Event;
import amadeus.maho.util.event.EventBus;

public interface Config {
    
    interface Locator {
        
        @Getter
        @RequiredArgsConstructor
        @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
        class FileSystemLocator implements Locator {
            
            Path root;
            
            String suffix;
            
            @Override
            public Path locate(final Class<?> configType, final String name) = root() / name(configType, name);
            
            protected String name(final Class<?> configType, final String name) = STR."\{configType.getSimpleName().replace('$', '/')}.\{name}.\{suffix()}";
            
        }
        
        Path locate(Class<?> configType, String name);
        
        static FileSystemLocator ofFileSystem(final Path root, final String suffix = "cfg") = { root, suffix };
        
    }
    
    @Getter
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    abstract sealed class ProcessEvent<C> extends Event {
        
        Config config;
        
        C instance;
        
        String name;
        
        @NoArgsConstructor
        public static final class Load<C> extends ProcessEvent<C> { }
        
        @NoArgsConstructor
        public static final class Save<C> extends ProcessEvent<C> { }
        
    }
    
    @Getter
    @RequiredArgsConstructor(on = @Builder)
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    class WithBus implements Config {
        
        Supplier<EventBus> bus;
        
        Config config;
        
        @Override
        public <T> T load(final T data, final String name) = (bus().get() >> new ProcessEvent.Load<>(this, data, name)).instance();
        
        @Override
        public void save(final Object data, final String name) = config().save((bus().get() >> new ProcessEvent.Save<>(this, data, name)).instance(), name);
        
        @Override
        public void delete(final Class<?> configType, final String name) = config.delete(configType, name);
        
    }
    
    String DEFAULT_NAME = "default";
    
    <T> T load(T data, String name = DEFAULT_NAME);
    
    void save(Object data, String name = DEFAULT_NAME);
    
    void delete(Class<?> configType, String name = DEFAULT_NAME);
    
    @SneakyThrows
    static Config of(final Locator locator, final Converter converter = Cfg.instance(), final Consumer<Throwable> handler = Throwable::printStackTrace, final Executor executor = Runnable::run) = new Config() {
        
        @Override
        public <T> T load(final T data, final String name) {
            final Path path = locator.locate(data.getClass(), name);
            try (final var input = Files.newInputStream(path)) {
                converter.read(input, data, path.toString());
                return data;
            } catch (final Throwable throwable) {
                if (throwable instanceof NoSuchFileException) {
                    save(data, name);
                    return data;
                }
                handler.accept(throwable);
                return data;
            }
        }
        
        @Override
        public void save(final Object data, final String name) = executor.execute(() -> {
            final Path locate = ++locator.locate(data.getClass(), name);
            try (final var output = Files.newOutputStream(locate)) { converter.write(output, data); }
        });
    
        @Override
        public void delete(final Class<?> configType, final String name) = locator.locate(configType, name)--;
        
    };
    
}
