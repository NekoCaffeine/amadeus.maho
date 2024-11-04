package amadeus.maho.util.shell;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import jdk.internal.misc.Unsafe;

import org.objectweb.asm.ClassReader;

import amadeus.maho.core.Maho;
import amadeus.maho.core.MahoExport;
import amadeus.maho.core.MahoProfile;
import amadeus.maho.lang.Include;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.build.Scaffold;
import amadeus.maho.util.build.ScriptHelper;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.dynamic.LambdaHelper;
import amadeus.maho.util.llm.LLMHelper;
import amadeus.maho.util.misc.Dumper;
import amadeus.maho.util.runtime.UnsafeHelper;

@Include({Scaffold.class, ScriptHelper.class, LLMHelper.class})
@SneakyThrows
public interface ShellHelper {
    
    Unsafe unsafe = UnsafeHelper.unsafe();
    
    static void print(final Class<?> target) = ASMHelper.printBytecode(Maho.getClassNodeFromClassNonNull(target));
    
    static void print(final @Nullable Object object) {
        if (object != null && LambdaHelper.isLambdaClass(object.getClass()))
            ASMHelper.printBytecode(Maho.getMethodNodeFromMethodNonNull((Method) object.getClass().constantPool().lastExecutableWithoutBoxed()));
    }
    
    static void profile() = Dumper.print(Stream.of(MahoProfile::dump));
    
    static void dump(final boolean onlyProfile = true) = Dumper.dump(~(MahoExport.workDirectory() / "dump"), (onlyProfile ? Map.<String, BiConsumer<List<String>, String>>of("Profile", MahoProfile::dump) : Dumper.dumper).entrySet().stream());
    
    static void dumpBytecode(final Path classFile, final Path bytecodeFile = classFile << ".bytecode") = ASMHelper.dumpBytecode(new ClassReader(Files.readAllBytes(classFile))) >> bytecodeFile;
    
    static void printBytecode(final Path classFile) = ASMHelper.printBytecode(new ClassReader(Files.readAllBytes(classFile)));
    
}
