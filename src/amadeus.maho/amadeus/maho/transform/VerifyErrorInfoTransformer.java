package amadeus.maho.transform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.objectweb.asm.tree.ClassNode;

import amadeus.maho.core.MahoExport;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformMetadata;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.build.Javac;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.runtime.DebugHelper;

import static amadeus.maho.util.bytecode.Bytecodes.ALOAD;

@SneakyThrows
@TransformProvider
public interface VerifyErrorInfoTransformer {
    
    AtomicReference<List<VerifyError>> verifyErrorsRef = { };
    
    Pattern locationPattern = Pattern.compile("Location:\\s+(?<class>.*?)\\.(?<method>.*?)(?<descriptor>\\(.*?\\).*?) "), reasonPattern = Pattern.compile("Reason:\\s+(?<reason>.*)\\n");
    
    String EXPECTED_STACKMAP = "Expected stackmap frame at this location.", EXPECTED_STACKMAP_OR_DEAD_CODE = "Expected stackmap frame at this location, or this location is unreachable.";
    
    @Hook(at = @At(var = @At.VarInsn(opcode = ALOAD, var = 1)), before = false, capture = true, avoidRecursion = true, metadata = @TransformMetadata(enable = MahoExport.MAHO_DEBUG_DUMP_BYTECODE, aotLevel = AOTTransformer.Level.RUNTIME))
    private static @Nullable String _init_(final String capture, final VerifyError $this, final @Nullable String message) {
        if (message == null)
            return null;
        verifyErrorsRef.get()?.add($this);
        final String transformed[] = { message };
        try {
            {
                final Matcher matcher = reasonPattern.matcher(transformed[0]);
                if (matcher.find()) {
                    final String reason = matcher.group("reason");
                    if (reason.equals(EXPECTED_STACKMAP))
                        transformed[0] = matcher.replaceFirst(matcher.group().replace(EXPECTED_STACKMAP, EXPECTED_STACKMAP_OR_DEAD_CODE));
                }
            }
            {
                final Matcher matcher = locationPattern.matcher(transformed[0]);
                if (matcher.find()) {
                    final String owner = matcher.group("class"), name = matcher.group("method"), descriptor = matcher.group("descriptor"), classFile = owner + Javac.CLASS_SUFFIX;
                    final Path resultPath = TransformerManager.DebugDumper.dump_transform_result / classFile, sourcePath = TransformerManager.DebugDumper.dump_transform_source / classFile;
                    try {
                        if (Files.isRegularFile(resultPath) && Files.isRegularFile(sourcePath)) {
                            final ClassNode resultNode = ASMHelper.newClassNode(Files.readAllBytes(resultPath)), sourceNode = ASMHelper.newClassNode(Files.readAllBytes(sourcePath));
                            ASMHelper.lookupMethodNode(resultNode, name, descriptor)
                                    .ifPresent(resultMethod -> ASMHelper.lookupMethodNode(sourceNode, name, descriptor)
                                            .ifPresent(sourceMethod -> transformed[0] += STR."  Result Dump:\n\{ASMHelper.dumpBytecode(resultMethod)}  Source Dump:\n\{ASMHelper.dumpBytecode(sourceMethod)}"));
                        }
                    } catch (final IOException ignored) { }
                }
            }
        } catch (final Throwable throwable) { DebugHelper.breakpoint(throwable); }
        return transformed[0];
    }
    
}
