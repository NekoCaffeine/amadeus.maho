package amadeus.maho.vm;

import java.util.Map;
import java.util.function.Function;

import com.sun.jna.Callback;
import com.sun.jna.LastErrorException;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.annotation.mark.HiddenDanger;
import amadeus.maho.util.misc.ConstantLookup;
import amadeus.maho.util.runtime.UnsafeHelper;

import static java.io.File.*;

public interface JNI extends Library {
    
    {
        final String key = "jna.library.path", home = System.getProperty("java.home");
        switch (Platform.getOSType()) {
            case Platform.WINDOWS, Platform.WINDOWSCE -> {
                final String bin = home + separator + "bin";
                System.setProperty(key, bin + pathSeparator + System.getProperty(key, ""));
            }
            case Platform.MAC                         -> {
                final String lib = home + separator + "lib", server = lib + separator + "server";
                System.setProperty(key, lib + pathSeparator + server + pathSeparator + System.getProperty(key, ""));
            }
        }
    }
    
    JNI INSTANCE = Native.load("jvm", JNI.class, Map.of(OPTION_ALLOW_OBJECTS, true));
    
    /* JNI Result code */
    int
            // @formatter:off
            JNI_OK        = 0,      /* success */
            JNI_ERR       = -1,     /* unknown error */
            JNI_EDETACHED = -2,     /* thread detached from the VM */
            JNI_EVERSION  = -3,     /* JNI version error */
            JNI_ENOMEM    = -4,     /* not enough memory */
            JNI_EEXIST    = -5,     /* VM already created */
            JNI_EINVAL    = -6;     /* invalid arguments */
            // @formatter:on
    
    /* JNI VersionInfo */
    int
            // @formatter:off
            JNI_VERSION_1_1 = 0x00010001,
            JNI_VERSION_1_2 = 0x00010002,
            JNI_VERSION_1_4 = 0x00010004,
            JNI_VERSION_1_6 = 0x00010006,
            JNI_VERSION_1_8 = 0x00010008,
            JNI_VERSION_9   = 0x00090000,
            JNI_VERSION_10  = 0x000a0000;
            // @formatter:on
    
    ConstantLookup lookup = new ConstantLookup().recording(JNI.class);
    
    @Structure.FieldOrder("reserved")
    class NativeInterface extends Structure {
        
        public static class ByReference extends NativeInterface implements Structure.ByReference { }
        
        public static class ByValue extends NativeInterface implements Structure.ByValue { }
        
        public long reserved;
        
        public NativeInterface() { }
        
        public NativeInterface(final Pointer pointer) {
            super(pointer);
            autoRead();
        }
        
    }
    
    @Structure.FieldOrder("functions")
    class Env extends Structure {
        
        public static class ByReference extends NativeInterface implements Structure.ByReference { }
        
        public static class ByValue extends NativeInterface implements Structure.ByValue { }
        
        public @Nullable NativeInterface.ByReference functions;
        
        public Env() { }
        
        public Env(final Pointer pointer) {
            super(pointer);
            autoRead();
        }
        
    }
    
    @SuppressWarnings("ConstantConditions")
    @Structure.FieldOrder("functions")
    class JavaVM extends Structure {
        
        public static class ByReference extends JavaVM implements Structure.ByReference { }
        
        public static class ByValue extends JavaVM implements Structure.ByValue { }
        
        @Structure.FieldOrder({
                "reserved0",
                "reserved1",
                "reserved2",
                "DestroyJavaVM",
                "AttachCurrentThread",
                "DetachCurrentThread",
                "GetEnv",
                "AttachCurrentThreadAsDaemon"
        })
        public static class InvokeInterface extends Structure {
            
            public static class ByReference extends InvokeInterface implements Structure.ByReference { }
            
            public static class ByValue extends InvokeInterface implements Structure.ByValue { }
            
            public @Nullable Pointer reserved0, reserved1, reserved2;
            
            public interface DestroyJavaVM extends Callback {
                
                int invoke(Pointer p_javaVM);
                
            }
            
            public @Nullable DestroyJavaVM DestroyJavaVM;
            
            public interface AttachCurrentThread extends Callback {
                
                int invoke(Pointer p_javaVM, PointerByReference p_penv, Pointer p_args);
                
            }
            
            public @Nullable AttachCurrentThread AttachCurrentThread;
            
            public interface DetachCurrentThread extends Callback {
                
                int invoke(Pointer p_javaVM);
                
            }
            
            public @Nullable DetachCurrentThread DetachCurrentThread;
            
            public interface GetEnv extends Callback {
                
                int invoke(Pointer p_javaVM, PointerByReference p_penv, int version);
                
            }
            
            public @Nullable GetEnv GetEnv;
            
            public interface AttachCurrentThreadAsDaemon extends Callback {
                
                int invoke(Pointer p_javaVM, PointerByReference p_penv, Pointer p_args);
                
            }
            
            public @Nullable AttachCurrentThreadAsDaemon AttachCurrentThreadAsDaemon;
            
            public InvokeInterface() { }
            
            public InvokeInterface(final Pointer pointer) = super(pointer);
            
        }
        
        public @Nullable InvokeInterface.ByReference functions;
        
        public JavaVM() { }
        
        public JavaVM(final Pointer pointer) {
            super(pointer);
            autoRead();
        }
        
        public void destroyJavaVM() throws LastErrorException = checkJNIError(functions.DestroyJavaVM.invoke(getPointer()));
        
        public void attachCurrentThread(final PointerByReference p_penv, final Pointer p_args) throws LastErrorException = checkJNIError(functions.AttachCurrentThread.invoke(getPointer(), p_penv, p_args));
        
        public void detachCurrentThread() throws LastErrorException = checkJNIError(functions.DetachCurrentThread.invoke(getPointer()));
        
        public <T> T getEnv(final Function<Pointer, ? extends T> mapper, final int version) throws LastErrorException {
            final PointerByReference p_penv = { };
            checkJNIError(functions.GetEnv.invoke(getPointer(), p_penv, version));
            return mapper.apply(p_penv.getValue());
        }
        
        public void attachCurrentThreadAsDaemon(final PointerByReference p_penv, final Pointer p_args) throws LastErrorException = checkJNIError(functions.AttachCurrentThreadAsDaemon.invoke(getPointer(), p_penv, p_args));
        
        public JNI.Env jniEnv(final int version = JNI_VERSION_10) throws LastErrorException, IllegalVersionException {
            IllegalVersionException.checkVersion(version, lookup, name -> name.startsWith("JNI_VERSION_"));
            return getEnv(JNI.Env::new, version);
        }
        
        public JVMTI.Env jvmtiEnv(final int version = JVMTI.JVMTI_VERSION_11, final boolean addPotentialCapabilities = true) throws LastErrorException, IllegalVersionException {
            IllegalVersionException.checkVersion(version, JVMTI.lookup, name -> name.startsWith("JVMTI_VERSION_"));
            final JVMTI.Env env = getEnv(JVMTI.Env::new, version);
            if (addPotentialCapabilities)
                env.addPotentialCapabilities();
            return env;
        }
        
        public static JavaVM contextVM() = { INSTANCE.contextVM() };
        
    }
    
    interface Instrument extends Library {
        
        Instrument INSTANCE = Native.load("instrument", Instrument.class);
        
        int Agent_OnAttach(Pointer p_vm, String path, @Nullable Pointer p_reserved = null);
        
        default void attachAgent(final String path, final @Nullable Pointer p_reserved = null) = checkJNIError(Agent_OnAttach(JNI.INSTANCE.contextVM(), path, p_reserved));
        
    }
    
    static String jniReturnCodeName(final int jniReturnCode) = lookup.lookupFieldName(jniReturnCode, name -> !name.startsWith("JNI_VERSION_"));
    
    static void checkJNIError(final int jniReturnCode) throws LastErrorException {
        if (jniReturnCode != JNI_OK)
            throw new LastErrorException(jniReturnCodeName(jniReturnCode) + "(" + jniReturnCode + ")");
    }
    
    int JNI_GetDefaultJavaVMInitArgs(Pointer p_args);
    
    int JNI_CreateJavaVM(PointerByReference p_vms, PointerByReference p_penv, Pointer p_args);
    
    int JNI_GetCreatedJavaVMs(PointerByReference p_vms, int count, IntByReference p_found);
    
    default Pointer contextVM() throws LastErrorException {
        final PointerByReference p_vms = { };
        final IntByReference p_found = { };
        checkJNIError(INSTANCE.JNI_GetCreatedJavaVMs(p_vms, 1, p_found));
        return p_vms.getValue();
    }
    
    @HiddenDanger(HiddenDanger.GC)
    default <T> @Nullable T resolvePointer(final @Nullable Pointer pointer) = pointer == null ? null : UnsafeHelper.fromAddress(Pointer.nativeValue(pointer));
    
    @HiddenDanger(HiddenDanger.GC)
    default <T> @Nullable T resolveReference(final PointerByReference reference) = resolvePointer(reference.getValue());
    
    @HiddenDanger(HiddenDanger.GC)
    default @Nullable Pointer toPointer(final @Nullable Object object) = object == null ? Pointer.NULL : new Pointer(UnsafeHelper.toAddress(object));
    
    @HiddenDanger(HiddenDanger.GC)
    default PointerByReference toReference(final @Nullable Object object) = { toPointer(object) };
    
}
