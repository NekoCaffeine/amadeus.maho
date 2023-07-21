package amadeus.maho.vm.reflection;

import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

public class JVMSymbols {
    
    private static NativeLibrary nativeLibrary;
    
    static { reload(); }
    
    public static void reload() = nativeLibrary = NativeLibrary.getInstance("jvm");
    
    public static long lookup(final String entryName) = Pointer.nativeValue(nativeLibrary.getGlobalVariableAddress(entryName));
    
}
