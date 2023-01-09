package amadeus.maho.agent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.util.build.Jar;
import amadeus.maho.vm.JNI;

import static java.nio.file.StandardOpenOption.*;

public interface AgentInjector {
    
    static void inject(final String prefix, final Class<?> agent, final Class<?>... resources) throws IOException = JNI.Instrument.INSTANCE.attachAgent(generateAgentJar(prefix, agent, resources));
    
    @SneakyThrows
    static String generateAgentJar(final String prefix, final Class<?> agent, final Class<?>... resources) throws IOException {
        final Path jarFile = Files.createTempFile(prefix + "-Agent-", Jar.SUFFIX);
        final Manifest manifest = { };
        final Attributes attributes = manifest.getMainAttributes();
        // Create manifest stating that agent is allowed to transform classes
        attributes[Attributes.Name.MANIFEST_VERSION] = "1.0";
        attributes[Jar.AGENT_CLASS] = agent.getName();
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
        return jarFile.toRealPath().toString();
    }
    
    private static String path(final Class<?> clazz) = clazz.getName().replace('.', '/') + ".class";
    
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
