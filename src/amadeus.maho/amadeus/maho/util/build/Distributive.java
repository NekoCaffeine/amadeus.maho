package amadeus.maho.util.build;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.dynamic.DynamicObject;
import amadeus.maho.util.logging.LoggerHelper;
import amadeus.maho.util.runtime.DebugHelper;

import static amadeus.maho.util.concurrent.AsyncHelper.*;

public interface Distributive {

    static Path zip(final Workspace workspace, final Module module, final Consumer<Path> collect, final String name = module.name(), final DateTimeFormatter formatter = LoggerHelper.LOG_FILE_NAME_FORMATTER) {
        final Module.Metadata metadata = workspace.config().load(new Module.Metadata(), module.name());
        final Path distributive = ~(workspace.root() / workspace.buildDir() / "distributive") / (LocalDateTime.now().format(formatter) + "-%s-%s.zip".formatted(name, metadata.version));
        distributive | collect;
        return distributive;
    }
    
    static void release(
            final Workspace workspace,
            final Module module,
            final Github.Repo repo,
            final String commitHash = repo["master"]["commit"]["sha"].asString(),
            final List<Path> distributive,
            final String tag = STR."\{workspace.config().load(new Module.Metadata(), module.name()).version}.\{workspace.metadata().buildCount}",
            final String title = tag,
            final String body = "Local build results synchronized through the Maho Build System.",
            final Function<Path, String> nameFunction = path -> path.getFileName().toString()) {
        final Github.Releases releases = { tag, commitHash, title, body };
        final DynamicObject createResult = repo > releases;
        final @Nullable DynamicObject id = createResult["id"];
        if (id == null)
            throw DebugHelper.breakpointBeforeThrow(new IllegalStateException(createResult.toString()));
        System.out.println(STR."Created release: \{id}");
        await(distributive.stream().map(file -> async(() -> {
            final String fileName = nameFunction.apply(file);
            repo.uploadReleaseAsset(id.asInt(), fileName, fileName, file);
            System.out.println(STR."Upload completed: \{fileName}");
        })));
    }

}
