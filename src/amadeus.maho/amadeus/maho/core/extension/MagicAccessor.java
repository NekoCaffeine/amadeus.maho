package amadeus.maho.core.extension;

import org.objectweb.asm.tree.ClassNode;

import amadeus.maho.core.Maho;
import amadeus.maho.core.MahoExport;
import amadeus.maho.util.bytecode.ASMHelper;

import static org.objectweb.asm.Opcodes.*;

public class MagicAccessor {
    
    public static final String
            Impl = ASMHelper.className("jdk.internal.reflect.MagicAccessorImpl"),
            Bridge = ASMHelper.className("jdk.internal.reflect.MagicAccessorMahoBridge");
    
    public static final Class<?> bridgeClass = Maho.shareClass(makeMagicAccessorBridge());
    
    private static ClassNode makeMagicAccessorBridge() {
        final ClassNode result = { };
        result.name = Bridge;
        result.superName = Impl;
        result.access = ACC_PUBLIC | ACC_SYNTHETIC;
        result.version = MahoExport.bytecodeVersion();
        return result;
    }
    
}
