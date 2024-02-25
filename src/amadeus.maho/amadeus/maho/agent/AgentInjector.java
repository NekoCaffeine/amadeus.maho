package amadeus.maho.agent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import amadeus.maho.core.MahoExport;
import amadeus.maho.core.MahoImage;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.util.build.Jar;
import amadeus.maho.util.misc.Environment;
import amadeus.maho.util.runtime.DebugHelper;
import amadeus.maho.vm.JNI;

import sun.instrument.InstrumentationImpl;

import static java.nio.file.StandardOpenOption.*;

public interface AgentInjector {
    
    static void inject(final String name, final Class<?> agent, final Class<?>... resources) throws IOException {
        final String redirectKey = STR."\{MahoExport.MAHO_AGENT_REDIRECT}.\{name}", agentPath = Environment.local().lookup(redirectKey, "");
        if (!agentPath.isEmpty()) {
            final Path jarFile = Path.of(agentPath);
            if (Files.isDirectory(jarFile))
                throw DebugHelper.breakpointBeforeThrow(new IllegalArgumentException(STR."\{redirectKey}: \{agentPath}"));
            if (!Files.isRegularFile(jarFile))
                generateAgentJar(jarFile, agent, resources);
            inject(jarFile);
        } else {
            final Path imageInlineAgent = Path.of(System.getProperty("java.home")) / "agent" / name;
            if (Files.isRegularFile(imageInlineAgent))
                inject(imageInlineAgent);
            else
                inject(generateAgentJar(name, agent, resources));
        }
    }
    
    @SneakyThrows
    static void inject(final Path jarFile) throws IOException {
        final String path = jarFile.toRealPath().toString();
        try {
            InstrumentationImpl.loadAgent(path);
        } catch (final IllegalAccessError | InternalError e) {
            try {
                if (MahoImage.isImage()) {
                    DebugHelper.breakpoint();
                    System.err.println("Fallback to JNA!");
                }
                JNI.Instrument.INSTANCE.attachAgent(path);
            } catch (final Exception re) {
                re.addSuppressed(e);
                throw DebugHelper.breakpointBeforeThrow(re);
            }
        }
    }
    
    @SneakyThrows
    static Path generateAgentJar(final String prefix, final Class<?> agent, final Class<?>... resources) throws IOException {
        final Path jarFile = Files.createTempFile(STR."\{prefix}-Agent-", Jar.SUFFIX);
        generateAgentJar(jarFile, agent, resources);
        return jarFile;
    }
    
    @SneakyThrows
    static void generateAgentJar(final Path jarFile, final Class<?> agent, final Class<?>... resources) throws IOException {
        final Manifest manifest = { };
        final Attributes attributes = manifest.getMainAttributes();
        // Create manifest stating that agent is allowed to transform classes
        attributes[Attributes.Name.MANIFEST_VERSION] = "1.0";
        attributes[Jar.AGENT_CLASS] = agent.getName();
        attributes[Jar.LAUNCHER_AGENT_CLASS] = agent.getName();
        attributes[Jar.CAN_RETRANSFORM_CLASSES] = Boolean.TRUE.toString();
        attributes[Jar.CAN_REDEFINE_CLASSES] = Boolean.TRUE.toString();
        try (final JarOutputStream output = { Files.newOutputStream(jarFile, CREATE, WRITE), manifest }) {
            output.putNextEntry(new JarEntry(path(agent)));
            output.write(getBytesFromClass(agent));
            for (final Class<?> resource : resources) {
                output.putNextEntry(new JarEntry(path(resource)));
                output.write(getBytesFromClass(resource));
            }
        }
    }
    
    private static String path(final Class<?> clazz) = STR."\{clazz.getName().replace('.', '/')}.class";
    
    private static byte[] getBytesFromClass(final Class<?> clazz) throws IOException {
        try (final var input = clazz.getClassLoader().getResourceAsStream(path(clazz))) {
            final ByteArrayOutputStream buffer = { };
            int nRead;
            final byte data[] = new byte[1 << 12];
            while ((nRead = input.read(data, 0, data.length)) != -1)
                buffer.write(data, 0, nRead);
            buffer.flush();
            return buffer.toByteArray();
        }
    }
    
}
