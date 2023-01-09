package amadeus.maho.util.build;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

import amadeus.maho.util.logging.LoggerHelper;

public interface Distributive {

    static Path zip(final Workspace workspace, final Module module, final Consumer<Path> collect, final String name = module.name(), final DateTimeFormatter formatter = LoggerHelper.LOG_FILE_NAME_FORMATTER) {
        final Module.Metadata metadata = workspace.config().load(new Module.Metadata(), module.name());
        final Path distributive = ~(workspace.buildDir() / "distributive") / (LocalDateTime.now().format(formatter) + "-%s-%s.zip".formatted(name, metadata.version));
        distributive | collect;
        return distributive;
    }

}
