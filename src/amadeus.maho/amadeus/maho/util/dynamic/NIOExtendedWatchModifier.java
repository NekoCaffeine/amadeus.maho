package amadeus.maho.util.dynamic;

import java.nio.file.WatchEvent;

import jdk.internal.misc.FileSystemOption;

import com.sun.nio.file.ExtendedWatchEventModifier;
import com.sun.nio.file.SensitivityWatchEventModifier;

import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.WeakLinking;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.runtime.MethodHandleHelper;

import static amadeus.maho.util.bytecode.Bytecodes.GETSTATIC;

public enum NIOExtendedWatchModifier {
    
    FILE_TREE((@WeakLinking Privilege) ExtendedWatchEventModifier.FILE_TREE),
    
    SENSITIVITY_HIGH((@WeakLinking Privilege) SensitivityWatchEventModifier.HIGH, 2),
    SENSITIVITY_MEDIUM((@WeakLinking Privilege) SensitivityWatchEventModifier.MEDIUM, 10),
    SENSITIVITY_LOW((@WeakLinking Privilege) SensitivityWatchEventModifier.LOW, 30);
    
    record ModifierProxy(String name) implements WatchEvent.Modifier { }
    
    @Getter
    final WatchEvent.Modifier modifier;
    
    @SneakyThrows
    NIOExtendedWatchModifier(final @Nullable WatchEvent.Modifier instance, final @Nullable Object value = null)
            = modifier = instance ?? new ModifierProxy(name()).let(proxy -> ((FileSystemOption) MethodHandleHelper.lookup(GETSTATIC, FileSystemOption.class, name(), FileSystemOption.class).invoke()).register(proxy, value));
    
}
