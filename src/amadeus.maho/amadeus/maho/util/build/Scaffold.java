package amadeus.maho.util.build;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import amadeus.maho.lang.SneakyThrows;

public interface Scaffold {
    
    @SneakyThrows
    static void initIDEA(final Workspace workspace = Workspace.here(), final String jdkVersion = "17", final boolean preview = true) {
        final Module build = Module.build();
        IDEA.generateAll(workspace, jdkVersion, preview, Set.of(build));
        build.subModules().forEach((name, location) -> {
            final Path moduleInfo = workspace.root() / build.path() / location / (Javac.MODULE_INFO + Javac.JAVA_SUFFIX);
            if (Files.notExists(moduleInfo))
                """
                        module %s {
                            
                            requires amadeus.maho;
                            
                        }
                        """.formatted(name) >> moduleInfo;
        });
    }
    
}
