package amadeus.maho.util.build;

import java.lang.ref.Reference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.stream.Stream;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;

import amadeus.maho.core.MahoExport;
import amadeus.maho.core.MahoImage;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.misc.Environment;

import static com.sun.jna.Platform.*;
import static com.sun.jna.platform.win32.WinUser.*;

public interface ScriptHelper {
    
    String
            D_PAIR        = "-D%s=%s",
            X_FLAG        = "-X%s",
            X_PAIR        = "-X%s:%s",
            XX_PAIR       = "-XX:%s=%s",
            XX_ENABLE     = "-XX:+%s",
            XX_DISABLE    = "-XX:-%s",
            ENABLE_ASSERT = "-ea";
    
    String
            ALL_DEFAULT     = "ALL-DEFAULT",
            ALL_SYSTEM      = "ALL-SYSTEM",
            ALL_MODULE_PATH = "ALL-MODULE-PATH";
    
    String MAHO_JAVA_EXECUTION = "maho.java.execution";
    
    static void useMahoImageIfPossible() {
        final @Nullable String image = System.getenv(MahoImage.VARIABLE);
        if (image != null) {
            final Path imagePath = Path.of(image);
            if (Files.isDirectory(imagePath)) {
                final Path java = imagePath / "bin" / (getOSType() == WINDOWS ? "java.exe" : "java");
                if (Files.isRegularFile(java))
                    Environment.local()[MAHO_JAVA_EXECUTION] = java.toAbsolutePath().toString();
            }
        }
    }
    
    static void useDefaultImage() = Environment.local()[MAHO_JAVA_EXECUTION] = "java";
    
    static void useContextImage() = Environment.local()[MAHO_JAVA_EXECUTION] = (Path.of(System.getProperty("java.home")) / "bin" / "java").toAbsolutePath().toString();
    
    static void addModules(final Collection<String> args, final String... modules) = Stream.of(modules).forEach(module -> {
        args += "--add-modules";
        args += module;
    });
    
    static void addAgent(final Collection<String> args, final Path agentJar) = args += "-javaagent:%s".formatted(agentJar.toAbsolutePath() | "/");
    
    static void println(final Object msg) = System.out.println(msg);
    
    static void info(final Object msg) = System.out.println("INFO: " + msg);
    
    static void debug(final Object msg) {
        if (MahoExport.debug())
            System.out.println("DEBUG: " + msg);
    }
    
    static void warning(final Object msg) = System.err.println("WARNING: " + msg);
    
    static void error(final Object msg) = System.err.println("ERROR: " + msg);
    
    static void fatal(final Object msg) {
        System.err.println("FATAL: " + msg);
        System.exit(-1);
    }
    
    static void refreshEnv() = switch (getOSType()) {
        case WINDOWS -> {
            final String environment = "Environment";
            final Pointer pointer = new Memory(environment.length() + 1);
            pointer.setString(0, environment);
            User32.INSTANCE.SendMessageTimeout(HWND_BROADCAST, 0x001A, new WinDef.WPARAM(0), new WinDef.LPARAM(Pointer.nativeValue(pointer)), SMTO_ABORTIFHUNG, 500, null);
            Reference.reachabilityFence(pointer);
        }
        default      -> { }
    };
    
}
