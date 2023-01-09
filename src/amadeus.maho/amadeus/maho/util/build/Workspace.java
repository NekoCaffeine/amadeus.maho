package amadeus.maho.util.build;

import java.nio.file.Path;

import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.ToString;
import amadeus.maho.util.config.Config;

@ToString
@EqualsAndHashCode
public record Workspace(Path root, Config config = Config.of(Config.Locator.ofFileSystem(root / Path.of("build"))), Path buildDir = Path.of("build"), Path outputDir = Path.of("output")) {
    
    public Path buildOutputDir() = buildDir() / outputDir();
    
    public Path output(final String type) = buildOutputDir() / type;
    
    public Path output(final String type, final Module module) = buildOutputDir() / module.path() / type;
    
    public BuildMetadata metadata() = config().load(new BuildMetadata());
    
    public self flushMetadata(final BuildMetadata metadata = metadata().let(it -> it.buildCount++)) = config().save(metadata);
    
    public self clean(final Module module) = --(root() / module.path() / buildOutputDir());
    
    public static Workspace here(final Path buildDir = Path.of("build"), final Path outputDir = Path.of("output"))
            = { Path.of(""), Config.of(Config.Locator.ofFileSystem(buildDir)), buildDir, outputDir };
    
}
