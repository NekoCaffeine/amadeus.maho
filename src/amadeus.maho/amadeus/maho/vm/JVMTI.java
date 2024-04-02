package amadeus.maho.vm;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.sun.jna.Callback;
import com.sun.jna.LastErrorException;
import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.Union;
import com.sun.jna.ptr.DoubleByReference;
import com.sun.jna.ptr.FloatByReference;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;

import amadeus.maho.lang.Getter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.misc.ConstantLookup;
import amadeus.maho.util.misc.Dumper;

public interface JVMTI extends Library {
    
    /* JVMTI VersionInfo */
    int
            JVMTI_VERSION_VALUE = 0x30000000,
            JVMTI_VERSION_1     = 0x30010000,
            JVMTI_VERSION_1_0   = 0x30010000,
            JVMTI_VERSION_1_1   = 0x30010100,
            JVMTI_VERSION_1_2   = 0x30010200,
            JVMTI_VERSION_9     = 0x30090000,
            JVMTI_VERSION_11    = 0x300B0000;
    
    /* Thread State Flags */
    int
            JVMTI_THREAD_STATE_ALIVE                    = 0x0001,
            JVMTI_THREAD_STATE_TERMINATED               = 0x0002,
            JVMTI_THREAD_STATE_RUNNABLE                 = 0x0004,
            JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER = 0x0400,
            JVMTI_THREAD_STATE_WAITING                  = 0x0080,
            JVMTI_THREAD_STATE_WAITING_INDEFINITELY     = 0x0010,
            JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT     = 0x0020,
            JVMTI_THREAD_STATE_SLEEPING                 = 0x0040,
            JVMTI_THREAD_STATE_IN_OBJECT_WAIT           = 0x0100,
            JVMTI_THREAD_STATE_PARKED                   = 0x0200,
            JVMTI_THREAD_STATE_SUSPENDED                = 0x100000,
            JVMTI_THREAD_STATE_INTERRUPTED              = 0x200000,
            JVMTI_THREAD_STATE_IN_NATIVE                = 0x400000,
            JVMTI_THREAD_STATE_VENDOR_1                 = 0x10000000,
            JVMTI_THREAD_STATE_VENDOR_2                 = 0x20000000,
            JVMTI_THREAD_STATE_VENDOR_3                 = 0x40000000;
    
    /* java.lang.Thread.State Conversion Masks */
    int
            JVMTI_JAVA_LANG_THREAD_STATE_MASK          = JVMTI_THREAD_STATE_TERMINATED | JVMTI_THREAD_STATE_ALIVE | JVMTI_THREAD_STATE_RUNNABLE |
            JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER | JVMTI_THREAD_STATE_WAITING | JVMTI_THREAD_STATE_WAITING_INDEFINITELY |
            JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT,
            JVMTI_JAVA_LANG_THREAD_STATE_NEW           = 0,
            JVMTI_JAVA_LANG_THREAD_STATE_TERMINATED    = JVMTI_THREAD_STATE_TERMINATED,
            JVMTI_JAVA_LANG_THREAD_STATE_RUNNABLE      = JVMTI_THREAD_STATE_ALIVE | JVMTI_THREAD_STATE_RUNNABLE,
            JVMTI_JAVA_LANG_THREAD_STATE_BLOCKED       = JVMTI_THREAD_STATE_ALIVE | JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER,
            JVMTI_JAVA_LANG_THREAD_STATE_WAITING       = JVMTI_THREAD_STATE_ALIVE | JVMTI_THREAD_STATE_WAITING | JVMTI_THREAD_STATE_WAITING_INDEFINITELY,
            JVMTI_JAVA_LANG_THREAD_STATE_TIMED_WAITING = JVMTI_THREAD_STATE_ALIVE | JVMTI_THREAD_STATE_WAITING | JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT;
    
    /* Thread Priority Constants */
    int
            JVMTI_THREAD_MIN_PRIORITY  = 1,
            JVMTI_THREAD_NORM_PRIORITY = 5,
            JVMTI_THREAD_MAX_PRIORITY  = 10;
    
    /* Heap Filter Flags */
    int
            JVMTI_HEAP_FILTER_TAGGED         = 0x4,
            JVMTI_HEAP_FILTER_UNTAGGED       = 0x8,
            JVMTI_HEAP_FILTER_CLASS_TAGGED   = 0x10,
            JVMTI_HEAP_FILTER_CLASS_UNTAGGED = 0x20;
    
    /* Heap Visit Control Flags */
    int
            JVMTI_VISIT_OBJECTS = 0x100,
            JVMTI_VISIT_ABORT   = 0x8000;
    
    /* jvmtiHeapReferenceKind */
    /* Heap Reference Enumeration */
    int
            JVMTI_HEAP_REFERENCE_CLASS             = 1,
            JVMTI_HEAP_REFERENCE_FIELD             = 2,
            JVMTI_HEAP_REFERENCE_ARRAY_ELEMENT     = 3,
            JVMTI_HEAP_REFERENCE_CLASS_LOADER      = 4,
            JVMTI_HEAP_REFERENCE_SIGNERS           = 5,
            JVMTI_HEAP_REFERENCE_PROTECTION_DOMAIN = 6,
            JVMTI_HEAP_REFERENCE_INTERFACE         = 7,
            JVMTI_HEAP_REFERENCE_STATIC_FIELD      = 8,
            JVMTI_HEAP_REFERENCE_CONSTANT_POOL     = 9,
            JVMTI_HEAP_REFERENCE_SUPERCLASS        = 10,
            JVMTI_HEAP_REFERENCE_JNI_GLOBAL        = 21,
            JVMTI_HEAP_REFERENCE_SYSTEM_CLASS      = 22,
            JVMTI_HEAP_REFERENCE_MONITOR           = 23,
            JVMTI_HEAP_REFERENCE_STACK_LOCAL       = 24,
            JVMTI_HEAP_REFERENCE_JNI_LOCAL         = 25,
            JVMTI_HEAP_REFERENCE_THREAD            = 26,
            JVMTI_HEAP_REFERENCE_OTHER             = 27;
    
    /* jvmtiPrimitiveType */
    /* Primitive Type Enumeration */
    int
            JVMTI_PRIMITIVE_TYPE_BOOLEAN = 90,
            JVMTI_PRIMITIVE_TYPE_BYTE    = 66,
            JVMTI_PRIMITIVE_TYPE_CHAR    = 67,
            JVMTI_PRIMITIVE_TYPE_SHORT   = 83,
            JVMTI_PRIMITIVE_TYPE_INT     = 73,
            JVMTI_PRIMITIVE_TYPE_LONG    = 74,
            JVMTI_PRIMITIVE_TYPE_FLOAT   = 70,
            JVMTI_PRIMITIVE_TYPE_DOUBLE  = 68;
    
    /* jvmtiHeapObjectFilter */
    /* Heap Object Filter Enumeration */
    int
            JVMTI_HEAP_OBJECT_TAGGED   = 1,
            JVMTI_HEAP_OBJECT_UNTAGGED = 2,
            JVMTI_HEAP_OBJECT_EITHER   = 3;
    
    /* jvmtiHeapRootKind */
    /* Heap Root Parser Enumeration */
    int
            JVMTI_HEAP_ROOT_JNI_GLOBAL   = 1,
            JVMTI_HEAP_ROOT_SYSTEM_CLASS = 2,
            JVMTI_HEAP_ROOT_MONITOR      = 3,
            JVMTI_HEAP_ROOT_STACK_LOCAL  = 4,
            JVMTI_HEAP_ROOT_JNI_LOCAL    = 5,
            JVMTI_HEAP_ROOT_THREAD       = 6,
            JVMTI_HEAP_ROOT_OTHER        = 7;
    
    /* jvmtiObjectReferenceKind */
    /* Object Reference Enumeration */
    int
            JVMTI_REFERENCE_CLASS             = 1,
            JVMTI_REFERENCE_FIELD             = 2,
            JVMTI_REFERENCE_ARRAY_ELEMENT     = 3,
            JVMTI_REFERENCE_CLASS_LOADER      = 4,
            JVMTI_REFERENCE_SIGNERS           = 5,
            JVMTI_REFERENCE_PROTECTION_DOMAIN = 6,
            JVMTI_REFERENCE_INTERFACE         = 7,
            JVMTI_REFERENCE_STATIC_FIELD      = 8,
            JVMTI_REFERENCE_CONSTANT_POOL     = 9;
    
    /* jvmtiIterationControl */
    /* Iteration Control Enumeration */
    int
            JVMTI_ITERATION_CONTINUE = 1,
            JVMTI_ITERATION_IGNORE   = 2,
            JVMTI_ITERATION_ABORT    = 0;
    
    /* Class<?> Status Flags */
    int
            JVMTI_CLASS_STATUS_VERIFIED    = 1,
            JVMTI_CLASS_STATUS_PREPARED    = 2,
            JVMTI_CLASS_STATUS_INITIALIZED = 4,
            JVMTI_CLASS_STATUS_ERROR       = 8,
            JVMTI_CLASS_STATUS_ARRAY       = 16,
            JVMTI_CLASS_STATUS_PRIMITIVE   = 32;
    
    /* jvmtiParamTypes */
    /* Extension Function/Event Parameter Types */
    int
            JVMTI_TYPE_JBYTE     = 101,
            JVMTI_TYPE_JCHAR     = 102,
            JVMTI_TYPE_JSHORT    = 103,
            JVMTI_TYPE_int       = 104,
            JVMTI_TYPE_long      = 105,
            JVMTI_TYPE_JFLOAT    = 106,
            JVMTI_TYPE_JDOUBLE   = 107,
            JVMTI_TYPE_JBOOLEAN  = 108,
            JVMTI_TYPE_JOBJECT   = 109,
            JVMTI_TYPE_JTHREAD   = 110,
            JVMTI_TYPE_JCLASS    = 111,
            JVMTI_TYPE_JVALUE    = 112,
            JVMTI_TYPE_JFIELDID  = 113,
            JVMTI_TYPE_JMETHODID = 114,
            JVMTI_TYPE_CCHAR     = 115,
            JVMTI_TYPE_CVOID     = 116,
            JVMTI_TYPE_JNIENV    = 117;
    
    /* jvmtiParamKind */
    /* Extension Function/Event Parameter Kinds */
    int
            JVMTI_KIND_IN              = 91,
            JVMTI_KIND_IN_PTR          = 92,
            JVMTI_KIND_IN_BUF          = 93,
            JVMTI_KIND_ALLOC_BUF       = 94,
            JVMTI_KIND_ALLOC_ALLOC_BUF = 95,
            JVMTI_KIND_OUT             = 96,
            JVMTI_KIND_OUT_BUF         = 97;
    
    /* jvmtiTimerKind */
    /* Timer Kinds */
    int
            JVMTI_TIMER_USER_CPU  = 30,
            JVMTI_TIMER_TOTAL_CPU = 31,
            JVMTI_TIMER_ELAPSED   = 32;
    
    /* jvmtiPhase */
    /* Phases of execution */
    int
            JVMTI_PHASE_ONLOAD     = 1,
            JVMTI_PHASE_PRIMORDIAL = 2,
            JVMTI_PHASE_START      = 6,
            JVMTI_PHASE_LIVE       = 4,
            JVMTI_PHASE_DEAD       = 8;
    
    /* VersionInfo Interface Types */
    int
            JVMTI_VERSION_INTERFACE_JNI   = 0x00000000,
            JVMTI_VERSION_INTERFACE_JVMTI = 0x30000000;
    
    /* VersionInfo Masks */
    int
            JVMTI_VERSION_MASK_INTERFACE_TYPE = 0x70000000,
            JVMTI_VERSION_MASK_MAJOR          = 0x0FFF0000,
            JVMTI_VERSION_MASK_MINOR          = 0x0000FF00,
            JVMTI_VERSION_MASK_MICRO          = 0x000000FF;
    
    /* VersionInfo Shifts */
    int
            JVMTI_VERSION_SHIFT_MAJOR = 16,
            JVMTI_VERSION_SHIFT_MINOR = 8,
            JVMTI_VERSION_SHIFT_MICRO = 0;
    
    /* jvmtiVerboseFlag */
    /* Verbose Flag Enumeration */
    int
            JVMTI_VERBOSE_OTHER = 0,
            JVMTI_VERBOSE_GC    = 1,
            JVMTI_VERBOSE_CLASS = 2,
            JVMTI_VERBOSE_JNI   = 4;
    
    /* jvmtiJlocationFormat */
    /* JLocation Format Enumeration */
    int
            JVMTI_JLOCATION_JVMBCI    = 1,
            JVMTI_JLOCATION_MACHINEPC = 2,
            JVMTI_JLOCATION_OTHER     = 0;
    
    /* Resource Exhaustion Flags */
    int
            JVMTI_RESOURCE_EXHAUSTED_OOM_ERROR = 0x0001,
            JVMTI_RESOURCE_EXHAUSTED_JAVA_HEAP = 0x0002,
            JVMTI_RESOURCE_EXHAUSTED_THREADS   = 0x0004;
    
    /*
        JVMTI Errors
            Every JVM TI function returns a jvmtiError error code.
            It is the responsibility of the agent to call JVM TI functions with valid parameters and in the proper context
                (calling thread is attached, phase is correct, etc.). Detecting some error conditions may be difficult, inefficient,
                or impossible for an implementation. The errors listed in Function Specific Required Errors must be detected by the
                implementation. All other errors represent the recommended response to the error condition.
     */
    int
            /*
                Universal Errors
                    The following errors may be returned by any function
            */
            JVMTI_ERROR_NONE                = 0, /* No error has occurred. This is the error code that is returned on successful completion of the function. */
            JVMTI_ERROR_NULL_POINTER        = 100, /* Pointer is unexpectedly NULL. */
            JVMTI_ERROR_OUT_OF_MEMORY       = 110, /* The function attempted to allocate memory and no more memory was available for allocation. */
            JVMTI_ERROR_ACCESS_DENIED       = 111, /* The desired functionality has not been enabled in this virtual machine. */
            JVMTI_ERROR_UNATTACHED_THREAD   = 115, /* The thread being used to call this function is not attached to the virtual machine.
                Calls must be made from attached threads. See AttachCurrentThread in the JNI invocation API. */
            JVMTI_ERROR_INVALID_ENVIRONMENT = 116, /* The JVM TI environment provided is no longer connected or is not an environment. */
            JVMTI_ERROR_WRONG_PHASE         = 112, /* The desired functionality is not available in the current phase. Always returned if the virtual machine
                has completed running. */
            JVMTI_ERROR_INTERNAL            = 113; /* An unexpected internal error has occurred. */
    
    int
            /*
                Function Specific Required Errors
                    The following errors are returned by some JVM TI functions and must be returned by the implementation when the condition occurs.
            */
            JVMTI_ERROR_INVALID_PRIORITY         = 12, /* Invalid priority. */
            JVMTI_ERROR_THREAD_NOT_SUSPENDED     = 13, /* Thread was not suspended. */
            JVMTI_ERROR_THREAD_SUSPENDED         = 14, /* Thread already suspended. */
            JVMTI_ERROR_THREAD_NOT_ALIVE         = 15, /* This operation requires the thread to be alive--that is, it must be started and not yet have died. */
            JVMTI_ERROR_CLASS_NOT_PREPARED       = 22, /* The class has been loaded but not yet prepared. */
            JVMTI_ERROR_NO_MORE_FRAMES           = 31, /* There are no Java programming language or JNI stack frames at the specified depth. */
            JVMTI_ERROR_OPAQUE_FRAME             = 32, /* Information about the frame is not available = e.g. for native frames). */
            JVMTI_ERROR_DUPLICATE                = 40, /* Item already set. */
            JVMTI_ERROR_NOT_FOUND                = 41, /* Desired element = e.g. field or breakpoint) not found. */
            JVMTI_ERROR_NOT_MONITOR_OWNER        = 51, /* This thread doesn't own the raw monitor. */
            JVMTI_ERROR_INTERRUPT                = 52, /* The call has been interrupted before completion. */
            JVMTI_ERROR_UNMODIFIABLE_CLASS       = 79, /* The class cannot be modified. */
            JVMTI_ERROR_NOT_AVAILABLE            = 98, /* The functionality is not available in this virtual machine. */
            JVMTI_ERROR_ABSENT_INFORMATION       = 101, /* The requested information is not available. */
            JVMTI_ERROR_INVALID_EVENT_TYPE       = 102, /* The specified event type ID is not recognized. */
            JVMTI_ERROR_NATIVE_METHOD            = 104, /* The requested information is not available for native method. */
            JVMTI_ERROR_CLASS_LOADER_UNSUPPORTED = 106; /* The class loader does not support this operation. */
    
    int
            /*
                Function Specific Agent Errors
                    The following errors are returned by some JVM TI functions. They are returned in the event of invalid parameters passed
                     by the agent or usage in an invalid context. An implementation is not required to detect these errors.
            */
            JVMTI_ERROR_INVALID_THREAD                                    = 10, /* The passed thread is not a valid thread. */
            JVMTI_ERROR_INVALID_FIELDID                                   = 25, /* Invalid field. */
            JVMTI_ERROR_INVALID_METHODID                                  = 23, /* Invalid method. */
            JVMTI_ERROR_INVALID_LOCATION                                  = 24, /* Invalid location. */
            JVMTI_ERROR_INVALID_OBJECT                                    = 20, /* Invalid object. */
            JVMTI_ERROR_INVALID_CLASS                                     = 21, /* Invalid class. */
            JVMTI_ERROR_TYPE_MISMATCH                                     = 34, /* The variable is not an appropriate type for the function used. */
            JVMTI_ERROR_INVALID_SLOT                                      = 35, /* Invalid slot. */
            JVMTI_ERROR_MUST_POSSESS_CAPABILITY                           = 99, /* The capability being used is false in this environment. */
            JVMTI_ERROR_INVALID_THREAD_GROUP                              = 11, /* Thread group invalid. */
            JVMTI_ERROR_INVALID_MONITOR                                   = 50, /* Invalid raw monitor. */
            JVMTI_ERROR_ILLEGAL_ARGUMENT                                  = 103, /* Illegal argument. */
            JVMTI_ERROR_INVALID_TYPESTATE                                 = 65, /* The state of the thread has been modified, and is now inconsistent. */
            JVMTI_ERROR_UNSUPPORTED_VERSION                               = 68, /* A new class file has a version number not supported by this VM. */
            JVMTI_ERROR_INVALID_CLASS_FORMAT                              = 60, /* A new class file is malformed = the VM would return a ClassFormatError). */
            JVMTI_ERROR_CIRCULAR_CLASS_DEFINITION                         = 61, /* The new class file definitions would lead to a circular definition = the VM would return a
                ClassCircularityError). */
            JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_ADDED             = 63, /* A new class file would require adding a method. */
            JVMTI_ERROR_UNSUPPORTED_REDEFINITION_SCHEMA_CHANGED           = 64, /* A new class version changes a field. */
            JVMTI_ERROR_FAILS_VERIFICATION                                = 62, /* The class bytes fail verification. */
            JVMTI_ERROR_UNSUPPORTED_REDEFINITION_HIERARCHY_CHANGED        = 66, /* A direct superclass is different for the new class version, or the set of
                directly implemented interfaces is different. */
            JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_DELETED           = 67, /* A new class version does not declare a method declared in the old class version. */
            JVMTI_ERROR_NAMES_DONT_MATCH                                  = 69, /* The class name defined in the new class file is different from the name in the old class object. */
            JVMTI_ERROR_UNSUPPORTED_REDEFINITION_CLASS_MODIFIERS_CHANGED  = 70, /* A new class version has different modifiers. */
            JVMTI_ERROR_UNSUPPORTED_REDEFINITION_METHOD_MODIFIERS_CHANGED = 71; /* A method in the new class version has different modifiers than
                its counterpart in the old class version. */
    
    /* jvmtiEventMode */
    /* Event Enable/Disable */
    int
            JVMTI_ENABLE  = 1,
            JVMTI_DISABLE = 0;
    
    /* Event IDs */
    int
            JVMTI_MIN_EVENT_TYPE_VAL              = 50,
            JVMTI_EVENT_VM_INIT                   = 50,
            JVMTI_EVENT_VM_DEATH                  = 51,
            JVMTI_EVENT_THREAD_START              = 52,
            JVMTI_EVENT_THREAD_END                = 53,
            JVMTI_EVENT_CLASS_FILE_LOAD_HOOK      = 54,
            JVMTI_EVENT_CLASS_LOAD                = 55,
            JVMTI_EVENT_CLASS_PREPARE             = 56,
            JVMTI_EVENT_VM_START                  = 57,
            JVMTI_EVENT_EXCEPTION                 = 58,
            JVMTI_EVENT_EXCEPTION_CATCH           = 59,
            JVMTI_EVENT_SINGLE_STEP               = 60,
            JVMTI_EVENT_FRAME_POP                 = 61,
            JVMTI_EVENT_BREAKPOINT                = 62,
            JVMTI_EVENT_FIELD_ACCESS              = 63,
            JVMTI_EVENT_FIELD_MODIFICATION        = 64,
            JVMTI_EVENT_METHOD_ENTRY              = 65,
            JVMTI_EVENT_METHOD_EXIT               = 66,
            JVMTI_EVENT_NATIVE_METHOD_BIND        = 67,
            JVMTI_EVENT_COMPILED_METHOD_LOAD      = 68,
            JVMTI_EVENT_COMPILED_METHOD_UNLOAD    = 69,
            JVMTI_EVENT_DYNAMIC_CODE_GENERATED    = 70,
            JVMTI_EVENT_DATA_DUMP_REQUEST         = 71,
            JVMTI_EVENT_MONITOR_WAIT              = 73,
            JVMTI_EVENT_MONITOR_WAITED            = 74,
            JVMTI_EVENT_MONITOR_CONTENDED_ENTER   = 75,
            JVMTI_EVENT_MONITOR_CONTENDED_ENTERED = 76,
            JVMTI_EVENT_RESOURCE_EXHAUSTED        = 80,
            JVMTI_EVENT_GARBAGE_COLLECTION_START  = 81,
            JVMTI_EVENT_GARBAGE_COLLECTION_FINISH = 82,
            JVMTI_EVENT_OBJECT_FREE               = 83,
            JVMTI_EVENT_VM_OBJECT_ALLOC           = 84,
            JVMTI_EVENT_SAMPLED_OBJECT_ALLOC      = 86,
            JVMTI_MAX_EVENT_TYPE_VAL              = 86;
    
    Map<String, ?> OPTIONS = Map.of(OPTION_ALLOW_OBJECTS, Boolean.TRUE);
    
    ConstantLookup lookup = new ConstantLookup().recording(JVMTI.class);
    
    @Getter
    JVMTI.Env env = JNI.JavaVM.contextVM().jvmtiEnv();
    
    interface StartFunction extends Callback {
        
        void invoke(Pointer p_env, Pointer jniEnv, Pointer p_args) throws InterruptedException;
        
    }
    
    interface HeapIterationCallback extends Callback {
        
        int invoke(long class_tag, long size, LongByReference tag_ptr, int length, Pointer p_args);
        
    }
    
    interface HeapReferenceCallback extends Callback {
        
        int invoke(int reference_kind /* jvmtiHeapReferenceKind */, HeapReferenceInfo.ByReference reference_info, long class_tag,
                long referrer_class_tag, long size, LongByReference tag_ptr, LongByReference referrer_tag_ptr, int length, Pointer user_data);
        
    }
    
    interface PrimitiveFieldCallback extends Callback {
        
        int invoke(int kind /* jvmtiHeapReferenceKind */, HeapReferenceInfo.ByReference info, long object_class_tag, LongByReference object_tag_ptr, Object value /* jvalue */, int value_type /* jvmtiPrimitiveType */, Pointer user_data);
        
    }
    
    interface ArrayPrimitiveValueCallback extends Callback {
        
        int invoke(long class_tag, long size, LongByReference tag_ptr, int element_count, int element_type /* jvmtiPrimitiveType */,
                Pointer elements, Pointer user_data);
        
    }
    
    interface StringPrimitiveValueCallback extends Callback {
        
        int invoke(long class_tag, long size, LongByReference tag_ptr, String value, int value_length, Pointer user_data);
        
    }
    
    interface ReservedCallback extends Callback {
        
        int invoke();
        
    }
    
    interface HeapObjectCallback extends Callback {
        
        int /* jvmtiIterationControl */ invoke(long class_tag, long size, LongByReference tag_ptr, Pointer user_data);
        
    }
    
    interface HeapRootCallback extends Callback {
        
        int /* jvmtiIterationControl */ invoke(int root_kind /* jvmtiHeapRootKind */, long class_tag, long size, LongByReference tag_ptr, Pointer user_data);
        
    }
    
    interface StackReferenceCallback extends Callback {
        
        int /* jvmtiIterationControl */ invoke(int root_kind /* jvmtiHeapRootKind */, long class_tag, long size, LongByReference tag_ptr, long thread_tag, int depth, PointerByReference method /* jmethodID */, int slot, Pointer user_data);
        
    }
    
    interface ObjectReferenceCallback extends Callback {
        
        int /* jvmtiIterationControl */ invoke(int reference_kind /* jvmtiObjectReferenceKind */, long class_tag, long size, LongByReference tag_ptr, long referrer_tag, int referrer_index, Pointer user_data);
        
    }
    
    interface ExtensionFunction extends Callback {
        
        void invoke(Pointer p_env, Object... args);
        
    }
    
    interface ExtensionEvent extends Callback {
        
        void invoke(Pointer p_env, Object... args);
        
    }
    
    @Structure.FieldOrder({
            "name",
            "priority",
            "is_daemon",
            "thread_group",
            "context_class_loader"
    })
    class ThreadInfo extends Structure {
        
        public static class ByReference extends ThreadInfo implements Structure.ByReference { }
        
        public static class ByValue extends ThreadInfo implements Structure.ByValue { }
        
        public String      name;
        public int         priority;
        public boolean     is_daemon;
        public ThreadGroup thread_group;
        public ClassLoader context_class_loader;
        
        public ThreadInfo() { }
        
        public ThreadInfo(final Pointer pointer) {
            super(pointer);
            autoRead();
        }
        
    }
    
    @Structure.FieldOrder({
            "monitor",
            "stack_depth"
    })
    class MonitorStackDepthInfo extends Structure {
        
        public static class ByReference extends MonitorStackDepthInfo implements Structure.ByReference { }
        
        public static class ByValue extends MonitorStackDepthInfo implements Structure.ByValue { }
        
        public Object monitor;
        public int    stack_depth;
        
        public MonitorStackDepthInfo() { }
        
        public MonitorStackDepthInfo(final Pointer pointer) {
            super(pointer);
            autoRead();
        }
        
    }
    
    @Structure.FieldOrder({
            "parent",
            "name",
            "max_priority",
            "is_daemon"
    })
    class ThreadGroupInfo extends Structure {
        
        public static class ByReference extends ThreadGroupInfo implements Structure.ByReference { }
        
        public static class ByValue extends ThreadGroupInfo implements Structure.ByValue { }
        
        public ThreadGroup parent;
        public String      name;
        public int         max_priority;
        public boolean     is_daemon;
        
        public ThreadGroupInfo() { }
        
        public ThreadGroupInfo(final Pointer pointer) {
            super(pointer);
            autoRead();
        }
        
    }
    
    @Structure.FieldOrder({
            "method",
            "location"
    })
    class FrameInfo extends Structure {
        
        public static class ByReference extends FrameInfo implements Structure.ByReference { }
        
        public static class ByValue extends FrameInfo implements Structure.ByValue { }
        
        public PointerByReference method;
        public long               location;
        
        public FrameInfo() { }
        
        public FrameInfo(final Pointer pointer) {
            super(pointer);
            autoRead();
        }
        
    }
    
    @Structure.FieldOrder({
            "thread",
            "state",
            "frame_buffer",
            "frame_count"
    })
    class StackInfo extends Structure {
        
        public static class ByReference extends StackInfo implements Structure.ByReference { }
        
        public static class ByValue extends StackInfo implements Structure.ByValue { }
        
        public Thread  thread;
        public int     state;
        public Pointer frame_buffer; /* jvmtiFrameInfo* */
        public int     frame_count;
        
        public StackInfo() { }
        
        public StackInfo(final Pointer pointer) {
            super(pointer);
            autoRead();
        }
        
    }
    
    class HeapReferenceInfo extends Union {
        
        public static class ByReference extends HeapReferenceInfo implements Structure.ByReference { }
        
        public static class ByValue extends HeapReferenceInfo implements Structure.ByValue { }
        
        public @Nullable HeapReferenceInfoField        field;
        public @Nullable HeapReferenceInfoArray        array;
        public @Nullable HeapReferenceInfoConstantPool constant_pool;
        public @Nullable HeapReferenceInfoStackLocal   stack_local;
        public @Nullable HeapReferenceInfoJniLocal     jni_local;
        public @Nullable HeapReferenceInfoReserved     other;
        
        public HeapReferenceInfo() { }
        
        public HeapReferenceInfo(final Pointer pointer) {
            super(pointer);
            autoRead();
        }
        
    }
    
    @Structure.FieldOrder({
            "index"
    })
    class HeapReferenceInfoField extends Structure {
        
        public static class ByReference extends HeapReferenceInfoField implements Structure.ByReference { }
        
        public static class ByValue extends HeapReferenceInfoField implements Structure.ByValue { }
        
        public int index;
        
        public HeapReferenceInfoField() { }
        
        public HeapReferenceInfoField(final Pointer pointer) {
            super(pointer);
            autoRead();
        }
        
    }
    
    @Structure.FieldOrder({
            "index"
    })
    class HeapReferenceInfoArray extends Structure {
        
        public static class ByReference extends HeapReferenceInfoArray implements Structure.ByReference { }
        
        public static class ByValue extends HeapReferenceInfoArray implements Structure.ByValue { }
        
        public int index;
        
        public HeapReferenceInfoArray() { }
        
        public HeapReferenceInfoArray(final Pointer pointer) {
            super(pointer);
            autoRead();
        }
        
    }
    
    @Structure.FieldOrder({
            "index"
    })
    class HeapReferenceInfoConstantPool extends Structure {
        
        public static class ByReference extends HeapReferenceInfoConstantPool implements Structure.ByReference { }
        
        public static class ByValue extends HeapReferenceInfoConstantPool implements Structure.ByValue { }
        
        public int index;
        
        public HeapReferenceInfoConstantPool() { }
        
        public HeapReferenceInfoConstantPool(final Pointer pointer) {
            super(pointer);
            autoRead();
        }
        
    }
    
    @Structure.FieldOrder({
            "thread_tag",
            "thread_id",
            "depth",
            "method",
            "location",
            "slot"
    })
    class HeapReferenceInfoStackLocal extends Structure {
        
        public static class ByReference extends HeapReferenceInfoStackLocal implements Structure.ByReference { }
        
        public static class ByValue extends HeapReferenceInfoStackLocal implements Structure.ByValue { }
        
        public long               thread_tag;
        public long               thread_id;
        public int                depth;
        public PointerByReference method;
        public long               location;
        public int                slot;
        
        public HeapReferenceInfoStackLocal() { }
        
        public HeapReferenceInfoStackLocal(final Pointer pointer) {
            super(pointer);
            autoRead();
        }
        
    }
    
    @Structure.FieldOrder({
            "thread_tag",
            "thread_id",
            "depth",
            "method"
    })
    class HeapReferenceInfoJniLocal extends Structure {
        
        public static class ByReference extends HeapReferenceInfoJniLocal implements Structure.ByReference { }
        
        public static class ByValue extends HeapReferenceInfoJniLocal implements Structure.ByValue { }
        
        public long               thread_tag;
        public long               thread_id;
        public int                depth;
        public PointerByReference method;
        
        public HeapReferenceInfoJniLocal() { }
        
        public HeapReferenceInfoJniLocal(final Pointer pointer) {
            super(pointer);
            autoRead();
        }
        
    }
    
    @Structure.FieldOrder({
            "reserved1",
            "reserved2",
            "reserved3",
            "reserved4",
            "reserved5",
            "reserved6",
            "reserved7",
            "reserved8"
    })
    class HeapReferenceInfoReserved extends Structure {
        
        public static class ByReference extends HeapReferenceInfoReserved implements Structure.ByReference { }
        
        public static class ByValue extends HeapReferenceInfoReserved implements Structure.ByValue { }
        
        public long reserved1;
        public long reserved2;
        public long reserved3;
        public long reserved4;
        public long reserved5;
        public long reserved6;
        public long reserved7;
        public long reserved8;
        
        public HeapReferenceInfoReserved() { }
        
        public HeapReferenceInfoReserved(final Pointer pointer) {
            super(pointer);
            autoRead();
        }
        
    }
    
    @Structure.FieldOrder({
            "heap_iteration_callback",
            "heap_reference_callback",
            "primitive_field_callback",
            "array_primitive_value_callback",
            "string_primitive_value_callback",
            "reserved5",
            "reserved6",
            "reserved7",
            "reserved8",
            "reserved9",
            "reserved10",
            "reserved11",
            "reserved12",
            "reserved13",
            "reserved14",
            "reserved15"
    })
    class HeapCallbacks extends Structure {
        
        public static class ByReference extends HeapCallbacks implements Structure.ByReference { }
        
        public static class ByValue extends HeapCallbacks implements Structure.ByValue { }
        
        public HeapIterationCallback        heap_iteration_callback;
        public HeapReferenceCallback        heap_reference_callback;
        public PrimitiveFieldCallback       primitive_field_callback;
        public ArrayPrimitiveValueCallback  array_primitive_value_callback;
        public StringPrimitiveValueCallback string_primitive_value_callback;
        public ReservedCallback             reserved5;
        public ReservedCallback             reserved6;
        public ReservedCallback             reserved7;
        public ReservedCallback             reserved8;
        public ReservedCallback             reserved9;
        public ReservedCallback             reserved10;
        public ReservedCallback             reserved11;
        public ReservedCallback             reserved12;
        public ReservedCallback             reserved13;
        public ReservedCallback             reserved14;
        public ReservedCallback             reserved15;
        
        public HeapCallbacks() { }
        
        public HeapCallbacks(final Pointer pointer) {
            super(pointer);
            autoRead();
        }
        
    }
    
    @Structure.FieldOrder({
            "klass",
            "class_byte_count",
            "class_bytes"
    })
    class ClassDefinition extends Structure {
        
        public static class ByReference extends ClassDefinition implements Structure.ByReference { }
        
        public static class ByValue extends ClassDefinition implements Structure.ByValue { }
        
        public Class<?> klass;
        public int      class_byte_count;
        public byte     class_bytes[];
        
        public ClassDefinition() { }
        
        public ClassDefinition(final Pointer pointer) {
            super(pointer);
            autoRead();
        }
        
    }
    
    @Structure.FieldOrder({
            "owner",
            "entry_count",
            "waiter_count",
            "waiters",
            "notify_waiter_count",
            "notify_waiters"
    })
    class MonitorUsage extends Structure {
        
        public static class ByReference extends MonitorUsage implements Structure.ByReference { }
        
        public static class ByValue extends MonitorUsage implements Structure.ByValue { }
        
        public Thread             owner;
        public int                entry_count;
        public int                waiter_count;
        public PointerByReference waiters; /* Thread* */
        public int                notify_waiter_count;
        public PointerByReference notify_waiters; /* Thread* */
        
        public MonitorUsage() { }
        
        public MonitorUsage(final Pointer pointer) {
            super(pointer);
            autoRead();
        }
        
    }
    
    @Structure.FieldOrder({
            "start_location",
            "line_number"
    })
    class LineNumberEntry extends Structure {
        
        public static class ByReference extends LineNumberEntry implements Structure.ByReference { }
        
        public static class ByValue extends LineNumberEntry implements Structure.ByValue { }
        
        public long start_location;
        public int  line_number;
        
        public LineNumberEntry() { }
        
        public LineNumberEntry(final Pointer pointer) {
            super(pointer);
            autoRead();
        }
        
    }
    
    @Structure.FieldOrder({
            "start_location",
            "length",
            "name",
            "signature",
            "generic_signature",
            "slot"
    })
    class LocalVariableEntry extends Structure {
        
        public static class ByReference extends LocalVariableEntry implements Structure.ByReference { }
        
        public static class ByValue extends LocalVariableEntry implements Structure.ByValue { }
        
        public long   start_location;
        public int    length;
        public String name;
        public String signature;
        public String generic_signature;
        public int    slot;
        
        public LocalVariableEntry() { }
        
        public LocalVariableEntry(final Pointer pointer) {
            super(pointer);
            autoRead();
        }
        
    }
    
    @Structure.FieldOrder({
            "name",
            "kind",
            "base_type",
            "null_ok"
    })
    class ParamInfo extends Structure {
        
        public static class ByReference extends ParamInfo implements Structure.ByReference { }
        
        public static class ByValue extends ParamInfo implements Structure.ByValue { }
        
        public String  name;
        public int     kind; /* jvmtiParamKind */
        public int     base_type; /* jvmtiParamTypes */
        public boolean null_ok;
        
        public ParamInfo() { }
        
        public ParamInfo(final Pointer pointer) {
            super(pointer);
            autoRead();
        }
        
    }
    
    @Structure.FieldOrder({
            "func",
            "id",
            "short_description",
            "param_count",
            "params",
            "error_count",
            "errors"
    })
    class ExtensionFunctionInfo extends Structure {
        
        public static class ByReference extends ExtensionFunctionInfo implements Structure.ByReference { }
        
        public static class ByValue extends ExtensionFunctionInfo implements Structure.ByValue { }
        
        public ExtensionFunction     func;
        public String                id;
        public String                short_description;
        public int                   param_count;
        public ParamInfo.ByReference params;
        public int                   error_count;
        public IntByReference        errors;
        
        public ExtensionFunctionInfo() { }
        
        public ExtensionFunctionInfo(final Pointer pointer) {
            super(pointer);
            autoRead();
        }
        
    }
    
    @Structure.FieldOrder({
            "extension_event_index",
            "id",
            "short_description",
            "param_count",
            "params"
    })
    class ExtensionEventInfo extends Structure {
        
        public static class ByReference extends ExtensionEventInfo implements Structure.ByReference { }
        
        public static class ByValue extends ExtensionEventInfo implements Structure.ByValue { }
        
        public int                   extension_event_index;
        public String                id;
        public String                short_description;
        public int                   param_count;
        public ParamInfo.ByReference params;
        
        public ExtensionEventInfo() { }
        
        public ExtensionEventInfo(final Pointer pointer) {
            super(pointer);
            autoRead();
        }
        
    }
    
    @Structure.FieldOrder({
            "max_value",
            "may_skip_forward",
            "may_skip_backward",
            "kind",
            "reserved1",
            "reserved2"
    })
    class TimerInfo extends Structure {
        
        public static class ByReference extends TimerInfo implements Structure.ByReference { }
        
        public static class ByValue extends TimerInfo implements Structure.ByValue { }
        
        public long    max_value;
        public boolean may_skip_forward;
        public boolean may_skip_backward;
        public int     kind; /* jvmtiTimerKind */
        public long    reserved1;
        public long    reserved2;
        
        public TimerInfo() { }
        
        public TimerInfo(final Pointer pointer) {
            super(pointer);
            autoRead();
        }
        
    }
    
    @Structure.FieldOrder({
            "start_address",
            "location"
    })
    class AddrLocationMap extends Structure {
        
        public static class ByReference extends AddrLocationMap implements Structure.ByReference { }
        
        public static class ByValue extends AddrLocationMap implements Structure.ByValue { }
        
        public Pointer start_address;
        public long    location;
        
        public AddrLocationMap() { }
        
        public AddrLocationMap(final Pointer pointer) {
            super(pointer);
            autoRead();
        }
        
    }
    
    interface EventReserved extends Callback {
        
        void invoke();
        
    }
    
    interface EventBreakpoint extends Callback {
        
        void invoke(Pointer p_env, Pointer jni_env, Pointer thread, PointerByReference method, long location);
        
    }
    
    interface EventClassFileLoadHook extends Callback {
        
        void invoke(Pointer p_env, Pointer jni_env, Class<?> class_being_redefined, ClassLoader loader, String name, Object protection_domain, int class_data_len, byte class_data[],
                IntByReference new_class_data_len, PointerByReference new_class_data);
        
    }
    
    interface EventClassLoad extends Callback {
        
        void invoke(Pointer p_env, Pointer jni_env, Thread thread, Class<?> klass);
        
    }
    
    interface EventClassPrepare extends Callback {
        
        void invoke(Pointer p_env, Pointer jni_env, Thread thread, Class<?> klass);
        
    }
    
    interface EventCompiledMethodLoad extends Callback {
        
        void invoke(Pointer p_env, PointerByReference method, int code_size, Pointer code_addr, int map_length, AddrLocationMap.ByReference map, Pointer compile_info);
        
    }
    
    interface EventCompiledMethodUnload extends Callback {
        
        void invoke(Pointer p_env, PointerByReference method, Pointer code_addr);
        
    }
    
    interface EventDataDumpRequest extends Callback {
        
        void invoke(Pointer p_env);
        
    }
    
    interface EventDynamicCodeGenerated extends Callback {
        
        void invoke(Pointer p_env, String name, Pointer address, int length);
        
    }
    
    interface EventException extends Callback {
        
        void invoke(Pointer p_env, Pointer jni_env, Thread thread, PointerByReference method, long location, Throwable exception, Pointer catch_method, long catch_location);
        
    }
    
    interface EventExceptionCatch extends Callback {
        
        void invoke(Pointer p_env, Pointer jni_env, Thread thread, PointerByReference method, long location, Throwable exception);
        
    }
    
    interface EventFieldAccess extends Callback {
        
        void invoke(Pointer p_env, Pointer jni_env, Thread thread, PointerByReference method, long location, Class<?> field_klass, Object object, Pointer field);
        
    }
    
    interface EventFieldModification extends Callback {
        
        void invoke(Pointer p_env, Pointer jni_env, Thread thread, PointerByReference method, long location, Class<?> field_klass, Object object, Pointer field, char signature_type, Object new_value);
        
    }
    
    interface EventFramePop extends Callback {
        
        void invoke(Pointer p_env, Pointer jni_env, Thread thread, PointerByReference method, boolean was_popped_by_exception);
        
    }
    
    interface EventGarbageCollectionFinish extends Callback {
        
        void invoke(Pointer p_env);
        
    }
    
    interface EventGarbageCollectionStart extends Callback {
        
        void invoke(Pointer p_env);
        
    }
    
    interface EventMethodEntry extends Callback {
        
        void invoke(Pointer p_env, Pointer jni_env, Thread thread, PointerByReference method);
        
    }
    
    interface EventMethodExit extends Callback {
        
        void invoke(Pointer p_env, Pointer jni_env, Thread thread, PointerByReference method, boolean was_popped_by_exception,
                Object return_value);
        
    }
    
    interface EventMonitorContendedEnter extends Callback {
        
        void invoke(Pointer p_env, Pointer jni_env, Thread thread, Object object);
        
    }
    
    interface EventMonitorContendedEntered extends Callback {
        
        void invoke(Pointer p_env, Pointer jni_env, Thread thread, Object object);
        
    }
    
    interface EventMonitorWait extends Callback {
        
        void invoke(Pointer p_env, Pointer jni_env, Thread thread, Object object, long timeout);
        
    }
    
    interface EventMonitorWaited extends Callback {
        
        void invoke(Pointer p_env, Pointer jni_env, Thread thread, Object object, long timed_out);
        
    }
    
    interface EventNativeMethodBind extends Callback {
        
        void invoke(Pointer p_env, Pointer jni_env, Thread thread, PointerByReference method, Pointer address, PointerByReference new_address_ptr);
        
    }
    
    interface EventObjectFree extends Callback {
        
        void invoke(Pointer p_env, long tag);
        
    }
    
    interface EventResourceExhausted extends Callback {
        
        void invoke(Pointer p_env, Pointer jni_env, int flags, Pointer reserved, String description);
        
    }
    
    interface EventSampledObjectAlloc extends Callback {
        
        void invoke(Pointer p_env, Pointer jni_env, Thread thread, Object object, Class<?> object_klass, long size);
        
    }
    
    interface EventSingleStep extends Callback {
        
        void invoke(Pointer p_env, Pointer jni_env, Thread thread, PointerByReference method, long location);
        
    }
    
    interface EventThreadEnd extends Callback {
        
        void invoke(Pointer p_env, Pointer jni_env, Thread thread);
        
    }
    
    interface EventThreadStart extends Callback {
        
        void invoke(Pointer p_env, Pointer jni_env, Thread thread);
        
    }
    
    interface EventVMDeath extends Callback {
        
        void invoke(Pointer p_env, Pointer jni_env);
        
    }
    
    interface EventVMInit extends Callback {
        
        void invoke(Pointer p_env, Pointer jni_env, Thread thread);
        
    }
    
    interface EventVMObjectAlloc extends Callback {
        
        void invoke(Pointer p_env, Pointer jni_env, Thread thread, Object object, Class<?> object_klass, long size);
        
    }
    
    interface EventVMStart extends Callback {
        
        void invoke(Pointer p_env, Pointer jni_env);
        
    }
    
    @Structure.FieldOrder({
            "VMInit",
            "VMDeath",
            "ThreadStart",
            "ThreadEnd",
            "ClassFileLoadHook",
            "ClassLoad",
            "ClassPrepare",
            "VMStart",
            "Exception",
            "ExceptionCatch",
            "SingleStep",
            "FramePop",
            "Breakpoint",
            "FieldAccess",
            "FieldModification",
            "MethodEntry",
            "MethodExit",
            "NativeMethodBind",
            "CompiledMethodLoad",
            "CompiledMethodUnload",
            "DynamicCodeGenerated",
            "DataDumpRequest",
            "reserved72",
            "MonitorWait",
            "MonitorWaited",
            "MonitorContendedEnter",
            "MonitorContendedEntered",
            "reserved77",
            "reserved78",
            "reserved79",
            "ResourceExhausted",
            "GarbageCollectionStart",
            "GarbageCollectionFinish",
            "ObjectFree",
            "VMObjectAlloc",
            "reserved85",
            "SampledObjectAlloc"
    })
    class EventCallbacks extends Structure {
        
        public static class ByReference extends EventCallbacks implements Structure.ByReference { }
        
        public static class ByValue extends EventCallbacks implements Structure.ByValue { }
        
        /*   50 : VM Initialization Event */
        public @Nullable EventVMInit                  VMInit;
        /*   51 : VM Death Event */
        public @Nullable EventVMDeath                 VMDeath;
        /*   52 : Thread Start */
        public @Nullable EventThreadStart             ThreadStart;
        /*   53 : Thread End */
        public @Nullable EventThreadEnd               ThreadEnd;
        /*   54 : Class<?> File Load Hook */
        public @Nullable EventClassFileLoadHook       ClassFileLoadHook;
        /*   55 : Class<?> Load */
        public @Nullable EventClassLoad               ClassLoad;
        /*   56 : Class<?> Prepare */
        public @Nullable EventClassPrepare            ClassPrepare;
        /*   57 : VM Start Event */
        public @Nullable EventVMStart                 VMStart;
        /*   58 : Exception */
        public @Nullable EventException               Exception;
        /*   59 : Exception Catch */
        public @Nullable EventExceptionCatch          ExceptionCatch;
        /*   60 : Single Step */
        public @Nullable EventSingleStep              SingleStep;
        /*   61 : Frame0 Pop */
        public @Nullable EventFramePop                FramePop;
        /*   62 : Breakpoint */
        public @Nullable EventBreakpoint              Breakpoint;
        /*   63 : Field Access */
        public @Nullable EventFieldAccess             FieldAccess;
        /*   64 : Field Modification */
        public @Nullable EventFieldModification       FieldModification;
        /*   65 : Method Entry */
        public @Nullable EventMethodEntry             MethodEntry;
        /*   66 : Method Exit */
        public @Nullable EventMethodExit              MethodExit;
        /*   67 : Native Method Bind */
        public @Nullable EventNativeMethodBind        NativeMethodBind;
        /*   68 : Compiled Method Load */
        public @Nullable EventCompiledMethodLoad      CompiledMethodLoad;
        /*   69 : Compiled Method Unload */
        public @Nullable EventCompiledMethodUnload    CompiledMethodUnload;
        /*   70 : Dynamic Code Generated */
        public @Nullable EventDynamicCodeGenerated    DynamicCodeGenerated;
        /*   71 : Data Dump Request */
        public @Nullable EventDataDumpRequest         DataDumpRequest;
        /*   72 */
        public @Nullable EventReserved                reserved72;
        /*   73 : Monitor Wait */
        public @Nullable EventMonitorWait             MonitorWait;
        /*   74 : Monitor Waited */
        public @Nullable EventMonitorWaited           MonitorWaited;
        /*   75 : Monitor Contended Enter */
        public @Nullable EventMonitorContendedEnter   MonitorContendedEnter;
        /*   76 : Monitor Contended Entered */
        public @Nullable EventMonitorContendedEntered MonitorContendedEntered;
        /*   77 */
        public @Nullable EventReserved                reserved77;
        /*   78 */
        public @Nullable EventReserved                reserved78;
        /*   79 */
        public @Nullable EventReserved                reserved79;
        /*   80 : Resource Exhausted */
        public @Nullable EventResourceExhausted       ResourceExhausted;
        /*   81 : Garbage Collection Start */
        public @Nullable EventGarbageCollectionStart  GarbageCollectionStart;
        /*   82 : Garbage Collection Finish */
        public @Nullable EventGarbageCollectionFinish GarbageCollectionFinish;
        /*   83 : Object Free */
        public @Nullable EventObjectFree              ObjectFree;
        /*   84 : VM Object Allocation */
        public @Nullable EventVMObjectAlloc           VMObjectAlloc;
        /*   85 */
        public @Nullable EventReserved                reserved85;
        /*   86 : Sampled Object Allocation */
        public @Nullable EventSampledObjectAlloc      SampledObjectAlloc;
        
        public EventCallbacks() { }
        
        public EventCallbacks(final Pointer pointer) {
            super(pointer);
            autoRead();
        }
        
    }
    
    @Structure.FieldOrder({
            "region1",
            "region2",
            "region3",
            "region4",
            "region5",
            "region6",
            "reserved1",
            "reserved2",
            "reserved3",
            "reserved4",
            "reserved5"
    })
    class Capabilities extends Structure {
        
        public static class ByReference extends Capabilities implements Structure.ByReference { }
        
        public static class ByValue extends Capabilities implements Structure.ByValue { }
        
        public byte region1, region2, region3, region4, region5, region6;
        public short reserved1, reserved2, reserved3, reserved4, reserved5;
        
        public int offset(final int index) = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN ?
                Byte.SIZE - 1 - index : index;
        
        public boolean can_tag_objects() = (region1 & 1 << offset(0)) != 0;
        
        public void can_tag_objects(final boolean value) {
            if (value)
                region1 |= 1 << offset(0);
            else
                region1 &= ~(1 << offset(0));
        }
        
        public boolean can_generate_field_modification_events() = (region1 & 1 << offset(1)) != 0;
        
        public void can_generate_field_modification_events(final boolean value) {
            if (value)
                region1 |= 1 << offset(1);
            else
                region1 &= ~(1 << offset(1));
        }
        
        public boolean can_generate_field_access_events() = (region1 & 1 << offset(2)) != 0;
        
        public void can_generate_field_access_events(final boolean value) {
            if (value)
                region1 |= 1 << offset(2);
            else
                region1 &= ~(1 << offset(2));
        }
        
        public boolean can_get_bytecodes() = (region1 & 1 << offset(3)) != 0;
        
        public void can_get_bytecodes(final boolean value) {
            if (value)
                region1 |= 1 << offset(3);
            else
                region1 &= ~(1 << offset(3));
        }
        
        public boolean can_get_synthetic_attribute() = (region1 & 1 << offset(4)) != 0;
        
        public void can_get_synthetic_attribute(final boolean value) {
            if (value)
                region1 |= 1 << offset(4);
            else
                region1 &= ~(1 << offset(4));
        }
        
        public boolean can_get_owned_monitor_info() = (region1 & 1 << offset(5)) != 0;
        
        public void can_get_owned_monitor_info(final boolean value) {
            if (value)
                region1 |= 1 << offset(5);
            else
                region1 &= ~(1 << offset(5));
        }
        
        public boolean can_get_current_contended_monitor() = (region1 & 1 << offset(6)) != 0;
        
        public void can_get_current_contended_monitor(final boolean value) {
            if (value)
                region1 |= 1 << offset(6);
            else
                region1 &= ~(1 << offset(6));
        }
        
        public boolean can_get_monitor_info() = (region1 & 1 << offset(7)) != 0;
        
        public void can_get_monitor_info(final boolean value) {
            if (value)
                region1 |= 1 << offset(7);
            else
                region1 &= ~(1 << offset(7));
        }
        
        public boolean can_pop_frame() = (region2 & 1 << offset(0)) != 0;
        
        public void can_pop_frame(final boolean value) {
            if (value)
                region2 |= 1 << offset(0);
            else
                region2 &= ~(1 << offset(0));
        }
        
        public boolean can_redefine_classes() = (region2 & 1 << offset(1)) != 0;
        
        public void can_redefine_classes(final boolean value) {
            if (value)
                region2 |= 1 << offset(1);
            else
                region2 &= ~(1 << offset(1));
        }
        
        public boolean can_signal_thread() = (region2 & 1 << offset(2)) != 0;
        
        public void can_signal_thread(final boolean value) {
            if (value)
                region2 |= 1 << offset(2);
            else
                region2 &= ~(1 << offset(2));
        }
        
        public boolean can_get_source_file_name() = (region2 & 1 << offset(3)) != 0;
        
        public void can_get_source_file_name(final boolean value) {
            if (value)
                region2 |= 1 << offset(3);
            else
                region2 &= ~(1 << offset(3));
        }
        
        public boolean can_get_line_numbers() = (region2 & 1 << offset(4)) != 0;
        
        public void can_get_line_numbers(final boolean value) {
            if (value)
                region2 |= 1 << offset(4);
            else
                region2 &= ~(1 << offset(4));
        }
        
        public boolean can_get_source_debug_extension() = (region2 & 1 << offset(5)) != 0;
        
        public void can_get_source_debug_extension(final boolean value) {
            if (value)
                region2 |= 1 << offset(5);
            else
                region2 &= ~(1 << offset(5));
        }
        
        public boolean can_access_local_variables() = (region2 & 1 << offset(6)) != 0;
        
        public void can_access_local_variables(final boolean value) {
            if (value)
                region2 |= 1 << offset(6);
            else
                region2 &= ~(1 << offset(6));
        }
        
        public boolean can_maintain_original_method_order() = (region2 & 1 << offset(7)) != 0;
        
        public void can_maintain_original_method_order(final boolean value) {
            if (value)
                region2 |= 1 << offset(7);
            else
                region2 &= ~(1 << offset(7));
        }
        
        public boolean can_generate_single_step_events() = (region3 & 1 << offset(0)) != 0;
        
        public void can_generate_single_step_events(final boolean value) {
            if (value)
                region3 |= 1 << offset(0);
            else
                region3 &= ~(1 << offset(0));
        }
        
        public boolean can_generate_exception_events() = (region3 & 1 << offset(1)) != 0;
        
        public void can_generate_exception_events(final boolean value) {
            if (value)
                region3 |= 1 << offset(1);
            else
                region3 &= ~(1 << offset(1));
        }
        
        public boolean can_generate_frame_pop_events() = (region3 & 1 << offset(2)) != 0;
        
        public void can_generate_frame_pop_events(final boolean value) {
            if (value)
                region3 |= 1 << offset(2);
            else
                region3 &= ~(1 << offset(2));
        }
        
        public boolean can_generate_breakpoint_events() = (region3 & 1 << offset(3)) != 0;
        
        public void can_generate_breakpoint_events(final boolean value) {
            if (value)
                region3 |= 1 << offset(3);
            else
                region3 &= ~(1 << offset(3));
        }
        
        public boolean can_suspend() = (region3 & 1 << offset(4)) != 0;
        
        public void can_suspend(final boolean value) {
            if (value)
                region3 |= 1 << offset(4);
            else
                region3 &= ~(1 << offset(4));
        }
        
        public boolean can_redefine_any_class() = (region3 & 1 << offset(5)) != 0;
        
        public void can_redefine_any_class(final boolean value) {
            if (value)
                region3 |= 1 << offset(5);
            else
                region3 &= ~(1 << offset(5));
        }
        
        public boolean can_get_current_thread_cpu_time() = (region3 & 1 << offset(6)) != 0;
        
        public void can_get_current_thread_cpu_time(final boolean value) {
            if (value)
                region3 |= 1 << offset(6);
            else
                region3 &= ~(1 << offset(6));
        }
        
        public boolean can_get_thread_cpu_time() = (region3 & 1 << offset(7)) != 0;
        
        public void can_get_thread_cpu_time(final boolean value) {
            if (value)
                region3 |= 1 << offset(7);
            else
                region3 &= ~(1 << offset(7));
        }
        
        public boolean can_generate_method_entry_events() = (region4 & 1 << offset(0)) != 0;
        
        public void can_generate_method_entry_events(final boolean value) {
            if (value)
                region4 |= 1 << offset(0);
            else
                region4 &= ~(1 << offset(0));
        }
        
        public boolean can_generate_method_exit_events() = (region4 & 1 << offset(1)) != 0;
        
        public void can_generate_method_exit_events(final boolean value) {
            if (value)
                region4 |= 1 << offset(1);
            else
                region4 &= ~(1 << offset(1));
        }
        
        public boolean can_generate_all_class_hook_events() = (region4 & 1 << offset(2)) != 0;
        
        public void can_generate_all_class_hook_events(final boolean value) {
            if (value)
                region4 |= 1 << offset(2);
            else
                region4 &= ~(1 << offset(2));
        }
        
        public boolean can_generate_compiled_method_load_events() = (region4 & 1 << offset(3)) != 0;
        
        public void can_generate_compiled_method_load_events(final boolean value) {
            if (value)
                region4 |= 1 << offset(3);
            else
                region4 &= ~(1 << offset(3));
        }
        
        public boolean can_generate_monitor_events() = (region4 & 1 << offset(4)) != 0;
        
        public void can_generate_monitor_events(final boolean value) {
            if (value)
                region4 |= 1 << offset(4);
            else
                region4 &= ~(1 << offset(4));
        }
        
        public boolean can_generate_vm_object_alloc_events() = (region4 & 1 << offset(5)) != 0;
        
        public void can_generate_vm_object_alloc_events(final boolean value) {
            if (value)
                region4 |= 1 << offset(5);
            else
                region4 &= ~(1 << offset(5));
        }
        
        public boolean can_generate_native_method_bind_events() = (region4 & 1 << offset(6)) != 0;
        
        public void can_generate_native_method_bind_events(final boolean value) {
            if (value)
                region4 |= 1 << offset(6);
            else
                region4 &= ~(1 << offset(6));
        }
        
        public boolean can_generate_garbage_collection_events() = (region4 & 1 << offset(7)) != 0;
        
        public void can_generate_garbage_collection_events(final boolean value) {
            if (value)
                region4 |= 1 << offset(7);
            else
                region4 &= ~(1 << offset(7));
        }
        
        public boolean can_generate_object_free_events() = (region5 & 1 << offset(0)) != 0;
        
        public void can_generate_object_free_events(final boolean value) {
            if (value)
                region5 |= 1 << offset(0);
            else
                region5 &= ~(1 << offset(0));
        }
        
        public boolean can_force_early_return() = (region5 & 1 << offset(1)) != 0;
        
        public void can_force_early_return(final boolean value) {
            if (value)
                region5 |= 1 << offset(1);
            else
                region5 &= ~(1 << offset(1));
        }
        
        public boolean can_get_owned_monitor_stack_depth_info() = (region5 & 1 << offset(2)) != 0;
        
        public void can_get_owned_monitor_stack_depth_info(final boolean value) {
            if (value)
                region5 |= 1 << offset(2);
            else
                region5 &= ~(1 << offset(2));
        }
        
        public boolean can_get_constant_pool() = (region5 & 1 << offset(3)) != 0;
        
        public void can_get_constant_pool(final boolean value) {
            if (value)
                region5 |= 1 << offset(3);
            else
                region5 &= ~(1 << offset(3));
        }
        
        public boolean can_set_native_method_prefix() = (region5 & 1 << offset(4)) != 0;
        
        public void can_set_native_method_prefix(final boolean value) {
            if (value)
                region5 |= 1 << offset(4);
            else
                region5 &= ~(1 << offset(4));
        }
        
        public boolean can_retransform_classes() = (region5 & 1 << offset(5)) != 0;
        
        public void can_retransform_classes(final boolean value) {
            if (value)
                region5 |= 1 << offset(5);
            else
                region5 &= ~(1 << offset(5));
        }
        
        public boolean can_retransform_any_class() = (region5 & 1 << offset(6)) != 0;
        
        public void can_retransform_any_class(final boolean value) {
            if (value)
                region5 |= 1 << offset(6);
            else
                region5 &= ~(1 << offset(6));
        }
        
        public boolean can_generate_resource_exhaustion_heap_events() = (region5 & 1 << offset(7)) != 0;
        
        public void can_generate_resource_exhaustion_heap_events(final boolean value) {
            if (value)
                region5 |= 1 << offset(7);
            else
                region5 &= ~(1 << offset(7));
        }
        
        public boolean can_generate_resource_exhaustion_threads_events() = (region6 & 1 << offset(0)) != 0;
        
        public void can_generate_resource_exhaustion_threads_events(final boolean value) {
            if (value)
                region6 |= 1 << offset(0);
            else
                region6 &= ~(1 << offset(0));
        }
        
        public boolean can_generate_early_vmstart() = (region6 & 1 << offset(1)) != 0;
        
        public void can_generate_early_vmstart(final boolean value) {
            if (value)
                region6 |= 1 << offset(1);
            else
                region6 &= ~(1 << offset(1));
        }
        
        public boolean can_generate_early_class_hook_events() = (region6 & 1 << offset(2)) != 0;
        
        public void can_generate_early_class_hook_events(final boolean value) {
            if (value)
                region6 |= 1 << offset(2);
            else
                region6 &= ~(1 << offset(2));
        }
        
        public boolean can_generate_sampled_object_alloc_events() = (region6 & 1 << offset(3)) != 0;
        
        public void can_generate_sampled_object_alloc_events(final boolean value) {
            if (value)
                region6 |= 1 << offset(3);
            else
                region6 &= ~(1 << offset(3));
        }
        
        public boolean reserved1() = (region6 & 1 << offset(4)) != 0;
        
        public void reserved1(final boolean value) {
            if (value)
                region6 |= 1 << offset(4);
            else
                region6 &= ~(1 << offset(4));
        }
        
        public boolean reserved2() = (region6 & 1 << offset(5)) != 0;
        
        public void reserved2(final boolean value) {
            if (value)
                region6 |= 1 << offset(5);
            else
                region6 &= ~(1 << offset(5));
        }
        
        public boolean reserved3() = (region6 & 1 << offset(6)) != 0;
        
        public void reserved3(final boolean value) {
            if (value)
                region6 |= 1 << offset(6);
            else
                region6 &= ~(1 << offset(6));
        }
        
        public boolean reserved4() = (region6 & 1 << offset(7)) != 0;
        
        public void reserved4(final boolean value) {
            if (value)
                region6 |= 1 << offset(7);
            else
                region6 &= ~(1 << offset(7));
        }
        
        public Capabilities() { }
        
        public Capabilities(final Pointer pointer) {
            super(pointer);
            autoRead();
        }
        
        @SneakyThrows
        public void dump(final List<String> list, final String subHead) = Stream.of(Capabilities.class.getMethods())
                .filter(method -> method.getName().startsWith("can_") && method.getReturnType() == boolean.class)
                .forEach(method -> list += STR."\{subHead}\{method.getName()}: \{method.invoke(this)}");
    
        @Override
        public String toString() {
            final ArrayList<String> list = { };
            dump(list, Dumper.head);
            return STR."""
\{super.toString()}
\{String.join("\n", list)}""";
        }
    }
    
    @Structure.FieldOrder("functions")
    class Env extends Structure {
        
        @Structure.FieldOrder({
                "reserved1",
                "SetEventNotificationMode",
                "GetAllModules",
                "GetAllThreads",
                "SuspendThread",
                "ResumeThread",
                "StopThread",
                "InterruptThread",
                "GetThreadInfo",
                "GetOwnedMonitorInfo",
                "GetCurrentContendedMonitor",
                "RunAgentThread",
                "GetTopThreadGroups",
                "GetThreadGroupInfo",
                "GetThreadGroupChildren",
                "GetFrameCount",
                "GetThreadState",
                "GetCurrentThread",
                "GetFrameLocation",
                "NotifyFramePop",
                "GetLocalObject",
                "GetLocalInt",
                "GetLocalLong",
                "GetLocalFloat",
                "GetLocalDouble",
                "SetLocalObject",
                "SetLocalInt",
                "SetLocalLong",
                "SetLocalFloat",
                "SetLocalDouble",
                "CreateRawMonitor",
                "DestroyRawMonitor",
                "RawMonitorEnter",
                "RawMonitorExit",
                "RawMonitorWait",
                "RawMonitorNotify",
                "RawMonitorNotifyAll",
                "SetBreakpoint",
                "ClearBreakpoint",
                "GetNamedModule",
                "SetFieldAccessWatch",
                "ClearFieldAccessWatch",
                "SetFieldModificationWatch",
                "ClearFieldModificationWatch",
                "IsModifiableClass",
                "Allocate",
                "Deallocate",
                "GetClassSignature",
                "GetClassStatus",
                "GetSourceFileName",
                "GetClassModifiers",
                "GetClassMethods",
                "GetClassFields",
                "GetImplementedInterfaces",
                "IsInterface",
                "IsArrayClass",
                "GetClassLoader",
                "GetObjectHashCode",
                "GetObjectMonitorUsage",
                "GetFieldName",
                "GetFieldDeclaringClass",
                "GetFieldModifiers",
                "IsFieldSynthetic",
                "GetMethodName",
                "GetMethodDeclaringClass",
                "GetMethodModifiers",
                "reserved67",
                "GetMaxLocals",
                "GetArgumentsSize",
                "GetLineNumberTable",
                "GetMethodLocation",
                "GetLocalVariableTable",
                "SetNativeMethodPrefix",
                "SetNativeMethodPrefixes",
                "GetBytecodes",
                "IsMethodNative",
                "IsMethodSynthetic",
                "GetLoadedClasses",
                "GetClassLoaderClasses",
                "PopFrame",
                "ForceEarlyReturnObject",
                "ForceEarlyReturnInt",
                "ForceEarlyReturnLong",
                "ForceEarlyReturnFloat",
                "ForceEarlyReturnDouble",
                "ForceEarlyReturnVoid",
                "RedefineClasses",
                "GetVersionNumber",
                "GetCapabilities",
                "GetSourceDebugExtension",
                "IsMethodObsolete",
                "SuspendThreadList",
                "ResumeThreadList",
                "AddModuleReads",
                "AddModuleExports",
                "AddModuleOpens",
                "AddModuleUses",
                "AddModuleProvides",
                "IsModifiableModule",
                "GetAllStackTraces",
                "GetThreadListStackTraces",
                "GetThreadLocalStorage",
                "SetThreadLocalStorage",
                "GetStackTrace",
                "reserved105",
                "GetTag",
                "SetTag",
                "ForceGarbageCollection",
                "IterateOverObjectsReachableFromObject",
                "IterateOverReachableObjects",
                "IterateOverHeap",
                "IterateOverInstancesOfClass",
                "reserved113",
                "GetObjectsWithTags",
                "FollowReferences",
                "IterateThroughHeap",
                "reserved117",
                "reserved118",
                "reserved119",
                "SetJNIFunctionTable",
                "GetJNIFunctionTable",
                "SetEventCallbacks",
                "GenerateEvents",
                "GetExtensionFunctions",
                "GetExtensionEvents",
                "SetExtensionEventCallback",
                "DisposeEnvironment",
                "GetErrorName",
                "GetJLocationFormat",
                "GetSystemProperties",
                "GetSystemProperty",
                "SetSystemProperty",
                "GetPhase",
                "GetCurrentThreadCpuTimerInfo",
                "GetCurrentThreadCpuTime",
                "GetThreadCpuTimerInfo",
                "GetThreadCpuTime",
                "GetTimerInfo",
                "GetTime",
                "GetPotentialCapabilities",
                "reserved141",
                "AddCapabilities",
                "RelinquishCapabilities",
                "GetAvailableProcessors",
                "GetClassVersionNumbers",
                "GetConstantPool",
                "GetEnvironmentLocalStorage",
                "SetEnvironmentLocalStorage",
                "AddToBootstrapClassLoaderSearch",
                "SetVerboseFlag",
                "AddToSystemClassLoaderSearch",
                "RetransformClasses",
                "GetOwnedMonitorStackDepthInfo",
                "GetObjectSize",
                "GetLocalInstance",
                "SetHeapSamplingInterval"
        })
        public static class Interface extends Structure {
            
            public static class ByReference extends Interface implements Structure.ByReference { }
            
            public static class ByValue extends Interface implements Structure.ByValue { }
            
            public @Nullable Pointer reserved1;
            
            public interface SetEventNotificationMode extends Callback {
                
                int invoke(Pointer p_env, int mode, int event_type, Thread event_thread, Object... args);
                
            }
            
            public @Nullable SetEventNotificationMode SetEventNotificationMode;
            
            public interface GetAllModules extends Callback {
                
                int invoke(Pointer p_env, IntByReference module_count_ptr, PointerByReference modules_ptr);
                
            }
            
            public @Nullable GetAllModules GetAllModules;
            
            public interface GetAllThreads extends Callback {
                
                int invoke(Pointer p_env, IntByReference threads_count_ptr, PointerByReference threads_ptr);
                
            }
            
            public @Nullable GetAllThreads GetAllThreads;
            
            public interface SuspendThread extends Callback {
                
                int invoke(Pointer p_env, Thread thread);
                
            }
            
            public @Nullable SuspendThread SuspendThread;
            
            public interface ResumeThread extends Callback {
                
                int invoke(Pointer p_env, Thread thread);
                
            }
            
            public @Nullable ResumeThread ResumeThread;
            
            public interface StopThread extends Callback {
                
                int invoke(Pointer p_env, Thread thread, Object exception);
                
            }
            
            public @Nullable StopThread StopThread;
            
            public interface InterruptThread extends Callback {
                
                int invoke(Pointer p_env, Thread thread);
                
            }
            
            public @Nullable InterruptThread InterruptThread;
            
            public interface GetThreadInfo extends Callback {
                
                int invoke(Pointer p_env, Thread thread, ThreadInfo.ByReference info_ptr);
                
            }
            
            public @Nullable GetThreadInfo GetThreadInfo;
            
            public interface GetOwnedMonitorInfo extends Callback {
                
                int invoke(Pointer p_env, Thread thread, IntByReference owned_monitor_count_ptr, PointerByReference owned_monitors_ptr);
                
            }
            
            public @Nullable GetOwnedMonitorInfo GetOwnedMonitorInfo;
            
            public interface GetCurrentContendedMonitor extends Callback {
                
                int invoke(Pointer p_env, Thread thread, PointerByReference monitor_ptr);
                
            }
            
            public @Nullable GetCurrentContendedMonitor GetCurrentContendedMonitor;
            
            public interface RunAgentThread extends Callback {
                
                int invoke(Pointer p_env, Thread thread, StartFunction proc, Pointer arg, int priority);
                
            }
            
            public @Nullable RunAgentThread RunAgentThread;
            
            public interface GetTopThreadGroups extends Callback {
                
                int invoke(Pointer p_env, IntByReference group_count_ptr, PointerByReference groups_ptr);
                
            }
            
            public @Nullable GetTopThreadGroups GetTopThreadGroups;
            
            public interface GetThreadGroupInfo extends Callback {
                
                int invoke(Pointer p_env, ThreadGroup group, ThreadGroupInfo.ByReference info_ptr);
                
            }
            
            public @Nullable GetThreadGroupInfo GetThreadGroupInfo;
            
            public interface GetThreadGroupChildren extends Callback {
                
                int invoke(Pointer p_env, ThreadGroup group, IntByReference thread_count_ptr, PointerByReference threads_ptr,
                        IntByReference group_count_ptr, PointerByReference groups_ptr);
                
            }
            
            public @Nullable GetThreadGroupChildren GetThreadGroupChildren;
            
            public interface GetFrameCount extends Callback {
                
                int invoke(Pointer p_env, Thread thread, IntByReference count_ptr);
                
            }
            
            public @Nullable GetFrameCount GetFrameCount;
            
            public interface GetThreadState extends Callback {
                
                int invoke(Pointer p_env, Thread thread, IntByReference thread_state_ptr);
                
            }
            
            public @Nullable GetThreadState GetThreadState;
            
            public interface GetCurrentThread extends Callback {
                
                int invoke(Pointer p_env, PointerByReference thread_ptr);
                
            }
            
            public @Nullable GetCurrentThread GetCurrentThread;
            
            public interface GetFrameLocation extends Callback {
                
                int invoke(Pointer p_env, Thread thread, int depth, PointerByReference method_ptr, LongByReference location_ptr);
                
            }
            
            public @Nullable GetFrameLocation GetFrameLocation;
            
            public interface NotifyFramePop extends Callback {
                
                int invoke(Pointer p_env, Thread thread, int depth);
                
            }
            
            public @Nullable NotifyFramePop NotifyFramePop;
            
            public interface GetLocalObject extends Callback {
                
                int invoke(Pointer p_env, Thread thread, int depth, int slot, PointerByReference value_ptr);
                
            }
            
            public @Nullable GetLocalObject GetLocalObject;
            
            public interface GetLocalInt extends Callback {
                
                int invoke(Pointer p_env, Thread thread, int depth, int slot, IntByReference value_ptr);
                
            }
            
            public @Nullable GetLocalInt GetLocalInt;
            
            public interface GetLocalLong extends Callback {
                
                int invoke(Pointer p_env, Thread thread, int depth, int slot, LongByReference value_ptr);
                
            }
            
            public @Nullable GetLocalLong GetLocalLong;
            
            public interface GetLocalFloat extends Callback {
                
                int invoke(Pointer p_env, Thread thread, int depth, int slot, FloatByReference value_ptr);
                
            }
            
            public @Nullable GetLocalFloat GetLocalFloat;
            
            public interface GetLocalDouble extends Callback {
                
                int invoke(Pointer p_env, Thread thread, int depth, int slot, DoubleByReference value_ptr);
                
            }
            
            public @Nullable GetLocalDouble GetLocalDouble;
            
            public interface SetLocalObject extends Callback {
                
                int invoke(Pointer p_env, Thread thread, int depth, int slot, Object value);
                
            }
            
            public @Nullable SetLocalObject SetLocalObject;
            
            public interface SetLocalInt extends Callback {
                
                int invoke(Pointer p_env, Thread thread, int depth, int slot, int value);
                
            }
            
            public @Nullable SetLocalInt SetLocalInt;
            
            public interface SetLocalLong extends Callback {
                
                int invoke(Pointer p_env, Thread thread, int depth, int slot, long value);
                
            }
            
            public @Nullable SetLocalLong SetLocalLong;
            
            public interface SetLocalFloat extends Callback {
                
                int invoke(Pointer p_env, Thread thread, int depth, int slot, float value);
                
            }
            
            public @Nullable SetLocalFloat SetLocalFloat;
            
            public interface SetLocalDouble extends Callback {
                
                int invoke(Pointer p_env, Thread thread, int depth, int slot, double value);
                
            }
            
            public @Nullable SetLocalDouble SetLocalDouble;
            
            public interface CreateRawMonitor extends Callback {
                
                int invoke(Pointer p_env, String name, PointerByReference monitor_ptr);
                
            }
            
            public @Nullable CreateRawMonitor CreateRawMonitor;
            
            public interface DestroyRawMonitor extends Callback {
                
                int invoke(Pointer p_env, Pointer monitor);
                
            }
            
            public @Nullable DestroyRawMonitor DestroyRawMonitor;
            
            public interface RawMonitorEnter extends Callback {
                
                int invoke(Pointer p_env, Pointer monitor);
                
            }
            
            public @Nullable RawMonitorEnter RawMonitorEnter;
            
            public interface RawMonitorExit extends Callback {
                
                int invoke(Pointer p_env, Pointer monitor);
                
            }
            
            public @Nullable RawMonitorExit RawMonitorExit;
            
            public interface RawMonitorWait extends Callback {
                
                int invoke(Pointer p_env, Pointer monitor, long millis);
                
            }
            
            public @Nullable RawMonitorWait RawMonitorWait;
            
            public interface RawMonitorNotify extends Callback {
                
                int invoke(Pointer p_env, Pointer monitor);
                
            }
            
            public @Nullable RawMonitorNotify RawMonitorNotify;
            
            public interface RawMonitorNotifyAll extends Callback {
                
                int invoke(Pointer p_env, Pointer monitor);
                
            }
            
            public @Nullable RawMonitorNotifyAll RawMonitorNotifyAll;
            
            public interface SetBreakpoint extends Callback {
                
                int invoke(Pointer p_env, Pointer method, long location);
                
            }
            
            public @Nullable SetBreakpoint SetBreakpoint;
            
            public interface ClearBreakpoint extends Callback {
                
                int invoke(Pointer p_env, Pointer method, long location);
                
            }
            
            public @Nullable ClearBreakpoint ClearBreakpoint;
            
            public interface GetNamedModule extends Callback {
                
                int invoke(Pointer p_env, Object class_loader, String package_name, PointerByReference module_ptr);
                
            }
            
            public @Nullable GetNamedModule GetNamedModule;
            
            public interface SetFieldAccessWatch extends Callback {
                
                int invoke(Pointer p_env, Class<?> klass, Pointer field);
                
            }
            
            public @Nullable SetFieldAccessWatch SetFieldAccessWatch;
            
            public interface ClearFieldAccessWatch extends Callback {
                
                int invoke(Pointer p_env, Class<?> klass, Pointer field);
                
            }
            
            public @Nullable ClearFieldAccessWatch ClearFieldAccessWatch;
            
            public interface SetFieldModificationWatch extends Callback {
                
                int invoke(Pointer p_env, Class<?> klass, Pointer field);
                
            }
            
            public @Nullable SetFieldModificationWatch SetFieldModificationWatch;
            
            public interface ClearFieldModificationWatch extends Callback {
                
                int invoke(Pointer p_env, Class<?> klass, Pointer field);
                
            }
            
            public @Nullable ClearFieldModificationWatch ClearFieldModificationWatch;
            
            public interface IsModifiableClass extends Callback {
                
                int invoke(Pointer p_env, Class<?> klass, IntByReference is_modifiable_class_ptr);
                
            }
            
            public @Nullable IsModifiableClass IsModifiableClass;
            
            public interface Allocate extends Callback {
                
                int invoke(Pointer p_env, long size, PointerByReference mem_ptr);
                
            }
            
            public @Nullable Allocate Allocate;
            
            public interface Deallocate extends Callback {
                
                int invoke(Pointer p_env, Pointer mem);
                
            }
            
            public @Nullable Deallocate Deallocate;
            
            public interface GetClassSignature extends Callback {
                
                int invoke(Pointer p_env, Class<?> klass, PointerByReference signature_ptr, PointerByReference generic_ptr);
                
            }
            
            public @Nullable GetClassSignature GetClassSignature;
            
            public interface GetClassStatus extends Callback {
                
                int invoke(Pointer p_env, Class<?> klass, IntByReference status_ptr);
                
            }
            
            public @Nullable GetClassStatus GetClassStatus;
            
            public interface GetSourceFileName extends Callback {
                
                int invoke(Pointer p_env, Class<?> klass, PointerByReference source_name_ptr);
                
            }
            
            public @Nullable GetSourceFileName GetSourceFileName;
            
            public interface GetClassModifiers extends Callback {
                
                int invoke(Pointer p_env, Class<?> klass, IntByReference modifiers_ptr);
                
            }
            
            public @Nullable GetClassModifiers GetClassModifiers;
            
            public interface GetClassMethods extends Callback {
                
                int invoke(Pointer p_env, Class<?> klass, IntByReference method_count_ptr, PointerByReference methods_ptr);
                
            }
            
            public @Nullable GetClassMethods GetClassMethods;
            
            public interface GetClassFields extends Callback {
                
                int invoke(Pointer p_env, Class<?> klass, IntByReference field_count_ptr, PointerByReference fields_ptr);
                
            }
            
            public @Nullable GetClassFields GetClassFields;
            
            public interface GetImplementedInterfaces extends Callback {
                
                int invoke(Pointer p_env, Class<?> klass, IntByReference interface_count_ptr, PointerByReference interfaces_ptr);
                
            }
            
            public @Nullable GetImplementedInterfaces GetImplementedInterfaces;
            
            public interface IsInterface extends Callback {
                
                int invoke(Pointer p_env, Class<?> klass, IntByReference is_interface_ptr);
                
            }
            
            public @Nullable IsInterface IsInterface;
            
            public interface IsArrayClass extends Callback {
                
                int invoke(Pointer p_env, Class<?> klass, IntByReference is_array_class_ptr);
                
            }
            
            public @Nullable IsArrayClass IsArrayClass;
            
            public interface GetClassLoader extends Callback {
                
                int invoke(Pointer p_env, Class<?> klass, PointerByReference classloader_ptr);
                
            }
            
            public @Nullable GetClassLoader GetClassLoader;
            
            public interface GetObjectHashCode extends Callback {
                
                int invoke(Pointer p_env, Object object, IntByReference hash_code_ptr);
                
            }
            
            public @Nullable GetObjectHashCode GetObjectHashCode;
            
            public interface GetObjectMonitorUsage extends Callback {
                
                int invoke(Pointer p_env, Object object, MonitorUsage.ByReference info_ptr);
                
            }
            
            public @Nullable GetObjectMonitorUsage GetObjectMonitorUsage;
            
            public interface GetFieldName extends Callback {
                
                int invoke(Pointer p_env, Class<?> klass, Pointer field, PointerByReference name_ptr, PointerByReference signature_ptr,
                        PointerByReference generic_ptr);
                
            }
            
            public @Nullable GetFieldName GetFieldName;
            
            public interface GetFieldDeclaringClass extends Callback {
                
                int invoke(Pointer p_env, Class<?> klass, Pointer field, PointerByReference declaring_class_ptr);
                
            }
            
            public @Nullable GetFieldDeclaringClass GetFieldDeclaringClass;
            
            public interface GetFieldModifiers extends Callback {
                
                int invoke(Pointer p_env, Class<?> klass, Pointer field, IntByReference modifiers_ptr);
                
            }
            
            public @Nullable GetFieldModifiers GetFieldModifiers;
            
            public interface IsFieldSynthetic extends Callback {
                
                int invoke(Pointer p_env, Class<?> klass, Pointer field, IntByReference is_synthetic_ptr);
                
            }
            
            public @Nullable IsFieldSynthetic IsFieldSynthetic;
            
            public interface GetMethodName extends Callback {
                
                int invoke(Pointer p_env, Pointer method, PointerByReference name_ptr, PointerByReference signature_ptr, PointerByReference generic_ptr);
                
            }
            
            public @Nullable GetMethodName GetMethodName;
            
            public interface GetMethodDeclaringClass extends Callback {
                
                int invoke(Pointer p_env, Pointer method, PointerByReference declaring_class_ptr);
                
            }
            
            public @Nullable GetMethodDeclaringClass GetMethodDeclaringClass;
            
            public interface GetMethodModifiers extends Callback {
                
                int invoke(Pointer p_env, Pointer method, IntByReference modifiers_ptr);
                
            }
            
            public @Nullable GetMethodModifiers GetMethodModifiers;
            
            public @Nullable Pointer reserved67;
            
            public interface GetMaxLocals extends Callback {
                
                int invoke(Pointer p_env, Pointer method, IntByReference max_ptr);
                
            }
            
            public @Nullable GetMaxLocals GetMaxLocals;
            
            public interface GetArgumentsSize extends Callback {
                
                int invoke(Pointer p_env, Pointer method, IntByReference size_ptr);
                
            }
            
            public @Nullable GetArgumentsSize GetArgumentsSize;
            
            public interface GetLineNumberTable extends Callback {
                
                int invoke(Pointer p_env, Pointer method, IntByReference entry_count_ptr, PointerByReference table_ptr);
                
            }
            
            public @Nullable GetLineNumberTable GetLineNumberTable;
            
            public interface GetMethodLocation extends Callback {
                
                int invoke(Pointer p_env, Pointer method, LongByReference start_location_ptr, LongByReference end_location_ptr);
                
            }
            
            public @Nullable GetMethodLocation GetMethodLocation;
            
            public interface GetLocalVariableTable extends Callback {
                
                int invoke(Pointer p_env, Pointer method, IntByReference entry_count_ptr, PointerByReference table_ptr);
                
            }
            
            public @Nullable GetLocalVariableTable GetLocalVariableTable;
            
            public interface SetNativeMethodPrefix extends Callback {
                
                int invoke(Pointer p_env, String prefix);
                
            }
            
            public @Nullable SetNativeMethodPrefix SetNativeMethodPrefix;
            
            public interface SetNativeMethodPrefixes extends Callback {
                
                int invoke(Pointer p_env, int prefix_count, PointerByReference prefixes);
                
            }
            
            public @Nullable SetNativeMethodPrefixes SetNativeMethodPrefixes;
            
            public interface GetBytecodes extends Callback {
                
                int invoke(Pointer p_env, Pointer method, IntByReference bytecode_count_ptr, PointerByReference bytecodes_ptr);
                
            }
            
            public @Nullable GetBytecodes GetBytecodes;
            
            public interface IsMethodNative extends Callback {
                
                int invoke(Pointer p_env, Pointer method, IntByReference is_native_ptr);
                
            }
            
            public @Nullable IsMethodNative IsMethodNative;
            
            public interface IsMethodSynthetic extends Callback {
                
                int invoke(Pointer p_env, Pointer method, IntByReference is_synthetic_ptr);
                
            }
            
            public @Nullable IsMethodSynthetic IsMethodSynthetic;
            
            public interface GetLoadedClasses extends Callback {
                
                int invoke(Pointer p_env, IntByReference class_count_ptr, PointerByReference classes_ptr);
                
            }
            
            public @Nullable GetLoadedClasses GetLoadedClasses;
            
            public interface GetClassLoaderClasses extends Callback {
                
                int invoke(Pointer p_env, Object initiating_loader, IntByReference class_count_ptr, PointerByReference classes_ptr);
                
            }
            
            public @Nullable GetClassLoaderClasses GetClassLoaderClasses;
            
            public interface PopFrame extends Callback {
                
                int invoke(Pointer p_env, Thread thread);
                
            }
            
            public @Nullable PopFrame PopFrame;
            
            public interface ForceEarlyReturnObject extends Callback {
                
                int invoke(Pointer p_env, Thread thread, Object value);
                
            }
            
            public @Nullable ForceEarlyReturnObject ForceEarlyReturnObject;
            
            public interface ForceEarlyReturnInt extends Callback {
                
                int invoke(Pointer p_env, Thread thread, int value);
                
            }
            
            public @Nullable ForceEarlyReturnInt ForceEarlyReturnInt;
            
            public interface ForceEarlyReturnLong extends Callback {
                
                int invoke(Pointer p_env, Thread thread, long value);
                
            }
            
            public @Nullable ForceEarlyReturnLong ForceEarlyReturnLong;
            
            public interface ForceEarlyReturnFloat extends Callback {
                
                int invoke(Pointer p_env, Thread thread, float value);
                
            }
            
            public @Nullable ForceEarlyReturnFloat ForceEarlyReturnFloat;
            
            public interface ForceEarlyReturnDouble extends Callback {
                
                int invoke(Pointer p_env, Thread thread, double value);
                
            }
            
            public @Nullable ForceEarlyReturnDouble ForceEarlyReturnDouble;
            
            public interface ForceEarlyReturnVoid extends Callback {
                
                int invoke(Pointer p_env, Thread thread);
                
            }
            
            public @Nullable ForceEarlyReturnVoid ForceEarlyReturnVoid;
            
            public interface RedefineClasses extends Callback {
                
                int invoke(Pointer p_env, int class_count, ClassDefinition.ByReference class_definitions);
                
            }
            
            public @Nullable RedefineClasses RedefineClasses;
            
            public interface GetVersionNumber extends Callback {
                
                int invoke(Pointer p_env, IntByReference version_ptr);
                
            }
            
            public @Nullable GetVersionNumber GetVersionNumber;
            
            public interface GetCapabilities extends Callback {
                
                int invoke(Pointer p_env, Capabilities.ByReference capabilities_ptr);
                
            }
            
            public @Nullable GetCapabilities GetCapabilities;
            
            public interface GetSourceDebugExtension extends Callback {
                
                int invoke(Pointer p_env, Class<?> klass, PointerByReference source_debug_extension_ptr);
                
            }
            
            public @Nullable GetSourceDebugExtension GetSourceDebugExtension;
            
            public interface IsMethodObsolete extends Callback {
                
                int invoke(Pointer p_env, Pointer method, IntByReference is_obsolete_ptr);
                
            }
            
            public @Nullable IsMethodObsolete IsMethodObsolete;
            
            public interface SuspendThreadList extends Callback {
                
                int invoke(Pointer p_env, int request_count, PointerByReference request_list, IntByReference results);
                
            }
            
            public @Nullable SuspendThreadList SuspendThreadList;
            
            public interface ResumeThreadList extends Callback {
                
                int invoke(Pointer p_env, int request_count, PointerByReference request_list, IntByReference results);
                
            }
            
            public @Nullable ResumeThreadList ResumeThreadList;
            
            public interface AddModuleReads extends Callback {
                
                int invoke(Pointer p_env, Object module, Object to_module);
                
            }
            
            public @Nullable AddModuleReads AddModuleReads;
            
            public interface AddModuleExports extends Callback {
                
                int invoke(Pointer p_env, Object module, String pkg_name, Object to_module);
                
            }
            
            public @Nullable AddModuleExports AddModuleExports;
            
            public interface AddModuleOpens extends Callback {
                
                int invoke(Pointer p_env, Object module, String pkg_name, Object to_module);
                
            }
            
            public @Nullable AddModuleOpens AddModuleOpens;
            
            public interface AddModuleUses extends Callback {
                
                int invoke(Pointer p_env, Object module, Class<?> service);
                
            }
            
            public @Nullable AddModuleUses AddModuleUses;
            
            public interface AddModuleProvides extends Callback {
                
                int invoke(Pointer p_env, Object module, Class<?> service, Class<?> impl_class);
                
            }
            
            public @Nullable AddModuleProvides AddModuleProvides;
            
            public interface IsModifiableModule extends Callback {
                
                int invoke(Pointer p_env, Object module, IntByReference is_modifiable_module_ptr);
                
            }
            
            public @Nullable IsModifiableModule IsModifiableModule;
            
            public interface GetAllStackTraces extends Callback {
                
                int invoke(Pointer p_env, int max_frame_count, PointerByReference stack_info_ptr, IntByReference thread_count_ptr);
                
            }
            
            public @Nullable GetAllStackTraces GetAllStackTraces;
            
            public interface GetThreadListStackTraces extends Callback {
                
                int invoke(Pointer p_env, int thread_count, PointerByReference thread_list, int max_frame_count, PointerByReference stack_info_ptr);
                
            }
            
            public @Nullable GetThreadListStackTraces GetThreadListStackTraces;
            
            public interface GetThreadLocalStorage extends Callback {
                
                int invoke(Pointer p_env, Thread thread, PointerByReference data_ptr);
                
            }
            
            public @Nullable GetThreadLocalStorage GetThreadLocalStorage;
            
            public interface SetThreadLocalStorage extends Callback {
                
                int invoke(Pointer p_env, Thread thread, Pointer data);
                
            }
            
            public @Nullable SetThreadLocalStorage SetThreadLocalStorage;
            
            public interface GetStackTrace extends Callback {
                
                int invoke(Pointer p_env, Thread thread, int start_depth, int max_frame_count, PointerByReference frame_buffer,
                        IntByReference count_ptr);
                
            }
            
            public @Nullable GetStackTrace GetStackTrace;
            
            public @Nullable Pointer reserved105;
            
            public interface GetTag extends Callback {
                
                int invoke(Pointer p_env, Object object, LongByReference tag_ptr);
                
            }
            
            public @Nullable GetTag GetTag;
            
            public interface SetTag extends Callback {
                
                int invoke(Pointer p_env, Object object, long tag);
                
            }
            
            public @Nullable SetTag SetTag;
            
            public interface ForceGarbageCollection extends Callback {
                
                int invoke(Pointer p_env);
                
            }
            
            public @Nullable ForceGarbageCollection ForceGarbageCollection;
            
            public interface IterateOverObjectsReachableFromObject extends Callback {
                
                int invoke(Pointer p_env, Object object, ObjectReferenceCallback object_reference_callback, Pointer user_data);
                
            }
            
            public @Nullable IterateOverObjectsReachableFromObject IterateOverObjectsReachableFromObject;
            
            public interface IterateOverReachableObjects extends Callback {
                
                int invoke(Pointer p_env, HeapRootCallback heap_root_callback, StackReferenceCallback stack_ref_callback,
                        ObjectReferenceCallback object_ref_callback, Pointer user_data);
                
            }
            
            public @Nullable IterateOverReachableObjects IterateOverReachableObjects;
            
            public interface IterateOverHeap extends Callback {
                
                int invoke(Pointer p_env, int object_filter, HeapObjectCallback heap_object_callback, Pointer user_data);
                
            }
            
            public @Nullable IterateOverHeap IterateOverHeap;
            
            public interface IterateOverInstancesOfClass extends Callback {
                
                int invoke(Pointer p_env, Class<?> klass, int object_filter, HeapObjectCallback heap_object_callback, Pointer user_data);
                
            }
            
            public @Nullable IterateOverInstancesOfClass IterateOverInstancesOfClass;
            
            public @Nullable Pointer reserved113;
            
            public interface GetObjectsWithTags extends Callback {
                
                int invoke(Pointer p_env, int tag_count, LongByReference tags, IntByReference count_ptr, PointerByReference object_result_ptr,
                        PointerByReference tag_result_ptr);
                
            }
            
            public @Nullable GetObjectsWithTags GetObjectsWithTags;
            
            public interface FollowReferences extends Callback {
                
                int invoke(Pointer p_env, int heap_filter, Class<?> klass, Object initial_object, HeapCallbacks.ByReference callbacks, Pointer user_data);
                
            }
            
            public @Nullable FollowReferences FollowReferences;
            
            public interface IterateThroughHeap extends Callback {
                
                int invoke(Pointer p_env, int heap_filter, Class<?> klass, HeapCallbacks.ByReference callbacks, Pointer user_data);
                
            }
            
            public @Nullable IterateThroughHeap IterateThroughHeap;
            
            public @Nullable Pointer reserved117;
            
            public @Nullable Pointer reserved118;
            
            public @Nullable Pointer reserved119;
            
            public interface SetJNIFunctionTable extends Callback {
                
                int invoke(Pointer p_env, JNI.NativeInterface.ByReference function_table);
                
            }
            
            public @Nullable SetJNIFunctionTable SetJNIFunctionTable;
            
            public interface GetJNIFunctionTable extends Callback {
                
                int invoke(Pointer p_env, PointerByReference function_table);
                
            }
            
            public @Nullable GetJNIFunctionTable GetJNIFunctionTable;
            
            public interface SetEventCallbacks extends Callback {
                
                int invoke(Pointer p_env, EventCallbacks.ByReference callbacks, int size_of_callbacks);
                
            }
            
            public @Nullable SetEventCallbacks SetEventCallbacks;
            
            public interface GenerateEvents extends Callback {
                
                int invoke(Pointer p_env, int event_type);
                
            }
            
            public @Nullable GenerateEvents GenerateEvents;
            
            public interface GetExtensionFunctions extends Callback {
                
                int invoke(Pointer p_env, IntByReference extension_count_ptr, PointerByReference extensions);
                
            }
            
            public @Nullable GetExtensionFunctions GetExtensionFunctions;
            
            public interface GetExtensionEvents extends Callback {
                
                int invoke(Pointer p_env, IntByReference extension_count_ptr, PointerByReference extensions);
                
            }
            
            public @Nullable GetExtensionEvents GetExtensionEvents;
            
            public interface SetExtensionEventCallback extends Callback {
                
                int invoke(Pointer p_env, int extension_event_index, ExtensionEvent callback);
                
            }
            
            public @Nullable SetExtensionEventCallback SetExtensionEventCallback;
            
            public interface DisposeEnvironment extends Callback {
                
                int invoke(Pointer p_env);
                
            }
            
            public @Nullable DisposeEnvironment DisposeEnvironment;
            
            public interface GetErrorName extends Callback {
                
                int invoke(Pointer p_env, int error, PointerByReference name_ptr);
                
            }
            
            public @Nullable GetErrorName GetErrorName;
            
            public interface GetJLocationFormat extends Callback {
                
                int invoke(Pointer p_env, IntByReference format_ptr);
                
            }
            
            public @Nullable GetJLocationFormat GetJLocationFormat;
            
            public interface GetSystemProperties extends Callback {
                
                int invoke(Pointer p_env, IntByReference count_ptr, PointerByReference property_ptr);
                
            }
            
            public @Nullable GetSystemProperties GetSystemProperties;
            
            public interface GetSystemProperty extends Callback {
                
                int invoke(Pointer p_env, String property, PointerByReference value_ptr);
                
            }
            
            public @Nullable GetSystemProperty GetSystemProperty;
            
            public interface SetSystemProperty extends Callback {
                
                int invoke(Pointer p_env, String property, String value);
                
            }
            
            public @Nullable SetSystemProperty SetSystemProperty;
            
            public interface GetPhase extends Callback {
                
                int invoke(Pointer p_env, IntByReference phase_ptr);
                
            }
            
            public @Nullable GetPhase GetPhase;
            
            public interface GetCurrentThreadCpuTimerInfo extends Callback {
                
                int invoke(Pointer p_env, TimerInfo.ByReference info_ptr);
                
            }
            
            public @Nullable GetCurrentThreadCpuTimerInfo GetCurrentThreadCpuTimerInfo;
            
            public interface GetCurrentThreadCpuTime extends Callback {
                
                int invoke(Pointer p_env, LongByReference nanos_ptr);
                
            }
            
            public @Nullable GetCurrentThreadCpuTime GetCurrentThreadCpuTime;
            
            public interface GetThreadCpuTimerInfo extends Callback {
                
                int invoke(Pointer p_env, TimerInfo.ByReference info_ptr);
                
            }
            
            public @Nullable GetThreadCpuTimerInfo GetThreadCpuTimerInfo;
            
            public interface GetThreadCpuTime extends Callback {
                
                int invoke(Pointer p_env, Thread thread, LongByReference nanos_ptr);
                
            }
            
            public @Nullable GetThreadCpuTime GetThreadCpuTime;
            
            public interface GetTimerInfo extends Callback {
                
                int invoke(Pointer p_env, TimerInfo.ByReference info_ptr);
                
            }
            
            public @Nullable GetTimerInfo GetTimerInfo;
            
            public interface GetTime extends Callback {
                
                int invoke(Pointer p_env, LongByReference nanos_ptr);
                
            }
            
            public @Nullable GetTime GetTime;
            
            public interface GetPotentialCapabilities extends Callback {
                
                int invoke(Pointer p_env, Capabilities.ByReference capabilities_ptr);
                
            }
            
            public @Nullable GetPotentialCapabilities GetPotentialCapabilities;
            
            public @Nullable Pointer reserved141;
            
            public interface AddCapabilities extends Callback {
                
                int invoke(Pointer p_env, Capabilities.ByReference capabilities_ptr);
                
            }
            
            public @Nullable AddCapabilities AddCapabilities;
            
            public interface RelinquishCapabilities extends Callback {
                
                int invoke(Pointer p_env, Capabilities.ByReference capabilities_ptr);
                
            }
            
            public @Nullable RelinquishCapabilities RelinquishCapabilities;
            
            public interface GetAvailableProcessors extends Callback {
                
                int invoke(Pointer p_env, IntByReference processor_count_ptr);
                
            }
            
            public @Nullable GetAvailableProcessors GetAvailableProcessors;
            
            public interface GetClassVersionNumbers extends Callback {
                
                int invoke(Pointer p_env, Class<?> klass, IntByReference minor_version_ptr, IntByReference major_version_ptr);
                
            }
            
            public @Nullable GetClassVersionNumbers GetClassVersionNumbers;
            
            public interface GetConstantPool extends Callback {
                
                int invoke(Pointer p_env, Class<?> klass, IntByReference constant_pool_count_ptr,
                        IntByReference constant_pool_byte_count_ptr, PointerByReference constant_pool_bytes_ptr);
                
            }
            
            public @Nullable GetConstantPool GetConstantPool;
            
            public interface GetEnvironmentLocalStorage extends Callback {
                
                int invoke(Pointer p_env, PointerByReference data_ptr);
                
            }
            
            public @Nullable GetEnvironmentLocalStorage GetEnvironmentLocalStorage;
            
            public interface SetEnvironmentLocalStorage extends Callback {
                
                int invoke(Pointer p_env, Pointer data);
                
            }
            
            public @Nullable SetEnvironmentLocalStorage SetEnvironmentLocalStorage;
            
            public interface AddToBootstrapClassLoaderSearch extends Callback {
                
                int invoke(Pointer p_env, String segment);
                
            }
            
            public @Nullable AddToBootstrapClassLoaderSearch AddToBootstrapClassLoaderSearch;
            
            public interface SetVerboseFlag extends Callback {
                
                int invoke(Pointer p_env, int flag, boolean value);
                
            }
            
            public @Nullable SetVerboseFlag SetVerboseFlag;
            
            public interface AddToSystemClassLoaderSearch extends Callback {
                
                int invoke(Pointer p_env, String segment);
                
            }
            
            public @Nullable AddToSystemClassLoaderSearch AddToSystemClassLoaderSearch;
            
            public interface RetransformClasses extends Callback {
                
                int invoke(Pointer p_env, int class_count, PointerByReference classes);
                
            }
            
            public @Nullable RetransformClasses RetransformClasses;
            
            public interface GetOwnedMonitorStackDepthInfo extends Callback {
                
                int invoke(Pointer p_env, Thread thread, IntByReference monitor_info_count_ptr, PointerByReference monitor_info_ptr);
                
            }
            
            public @Nullable GetOwnedMonitorStackDepthInfo GetOwnedMonitorStackDepthInfo;
            
            public interface GetObjectSize extends Callback {
                
                int invoke(Pointer p_env, Object object, LongByReference size_ptr);
                
            }
            
            public @Nullable GetObjectSize GetObjectSize;
            
            public interface GetLocalInstance extends Callback {
                
                int invoke(Pointer p_env, Thread thread, int depth, PointerByReference value_ptr);
                
            }
            
            public @Nullable GetLocalInstance GetLocalInstance;
            
            public interface SetHeapSamplingInterval extends Callback {
                
                int invoke(Pointer p_env, int sampling_interval);
                
            }
            
            public @Nullable SetHeapSamplingInterval SetHeapSamplingInterval;
            
        }
        
        public static class ByReference extends Env implements Structure.ByReference { }
        
        public static class ByValue extends Env implements Structure.ByValue { }
        
        public @Nullable Interface.ByReference functions;
        
        public Env() { }
        
        public Env(final Pointer pointer) {
            super(pointer);
            autoRead();
        }
        
        public void setEventNotificationMode(final int mode, final int event_type, final Thread event_thread, final Object... args) throws LastErrorException
                = checkJVMTIError(functions.SetEventNotificationMode.invoke(getPointer(), mode, event_type, event_thread, args));
        
        public void getAllModules(final IntByReference module_count_ptr, final PointerByReference modules_ptr) throws LastErrorException = checkJVMTIError(functions.GetAllModules.invoke(getPointer(), module_count_ptr, modules_ptr));
        
        public void getAllThreads(final IntByReference threads_count_ptr, final PointerByReference threads_ptr) throws LastErrorException = checkJVMTIError(functions.GetAllThreads.invoke(getPointer(), threads_count_ptr, threads_ptr));
        
        public void suspendThread(final Thread thread) throws LastErrorException = checkJVMTIError(functions.SuspendThread.invoke(getPointer(), thread));
        
        public void resumeThread(final Thread thread) throws LastErrorException = checkJVMTIError(functions.ResumeThread.invoke(getPointer(), thread));
        
        public void stopThread(final Thread thread, final Object exception) throws LastErrorException = checkJVMTIError(functions.StopThread.invoke(getPointer(), thread, exception));
        
        public void interruptThread(final Thread thread) throws LastErrorException = checkJVMTIError(functions.InterruptThread.invoke(getPointer(), thread));
        
        public ThreadInfo.ByReference getThreadInfo(final Thread thread) throws LastErrorException {
            final ThreadInfo.ByReference info_ptr = { };
            checkJVMTIError(functions.GetThreadInfo.invoke(getPointer(), thread, info_ptr));
            info_ptr.autoRead();
            return info_ptr;
        }
        
        public void getOwnedMonitorInfo(final Thread thread, final IntByReference owned_monitor_count_ptr, final PointerByReference owned_monitors_ptr) throws LastErrorException
                = checkJVMTIError(functions.GetOwnedMonitorInfo.invoke(getPointer(), thread, owned_monitor_count_ptr, owned_monitors_ptr));
        
        public Pointer getCurrentContendedMonitor(final Thread thread) throws LastErrorException {
            final PointerByReference monitor_ptr = { };
            checkJVMTIError(functions.GetCurrentContendedMonitor.invoke(getPointer(), thread, monitor_ptr));
            return monitor_ptr.getValue();
        }
        
        public void runAgentThread(final Thread thread, final StartFunction proc, final Pointer arg, final int priority) throws LastErrorException
                = checkJVMTIError(functions.RunAgentThread.invoke(getPointer(), thread, proc, arg, priority));
        
        public void getTopThreadGroups(final IntByReference group_count_ptr, final PointerByReference groups_ptr) throws LastErrorException = checkJVMTIError(functions.GetTopThreadGroups.invoke(getPointer(), group_count_ptr, groups_ptr));
        
        public ThreadGroupInfo.ByReference getThreadGroupInfo(final ThreadGroup group) throws LastErrorException {
            final ThreadGroupInfo.ByReference info_ptr = { };
            checkJVMTIError(functions.GetThreadGroupInfo.invoke(getPointer(), group, info_ptr));
            info_ptr.autoRead();
            return info_ptr;
        }
        
        public void getThreadGroupChildren(final ThreadGroup group, final IntByReference thread_count_ptr, final PointerByReference threads_ptr, final IntByReference group_count_ptr, final PointerByReference groups_ptr)
                throws LastErrorException = checkJVMTIError(functions.GetThreadGroupChildren.invoke(getPointer(), group, thread_count_ptr, threads_ptr, group_count_ptr, groups_ptr));
        
        public int getFrameCount(final Thread thread) throws LastErrorException {
            final IntByReference count_ptr = { };
            checkJVMTIError(functions.GetFrameCount.invoke(getPointer(), thread, count_ptr));
            return count_ptr.getValue();
        }
        
        public int getThreadState(final Thread thread) throws LastErrorException {
            final IntByReference thread_state_ptr = { };
            checkJVMTIError(functions.GetThreadState.invoke(getPointer(), thread, thread_state_ptr));
            return thread_state_ptr.getValue();
        }
        
        public Thread getCurrentThread() throws LastErrorException {
            final PointerByReference thread_ptr = { };
            return GCLock.getCritical(() -> {
                checkJVMTIError(functions.GetCurrentThread.invoke(getPointer(), thread_ptr));
                return JNI.INSTANCE.resolveReference(thread_ptr);
            });
        }
        
        public void getFrameLocation(final Thread thread, final int depth, final PointerByReference method_ptr, final LongByReference location_ptr) throws LastErrorException
                = checkJVMTIError(functions.GetFrameLocation.invoke(getPointer(), thread, depth, method_ptr, location_ptr));
        
        public void notifyFramePop(final Thread thread, final int depth) throws LastErrorException = checkJVMTIError(functions.NotifyFramePop.invoke(getPointer(), thread, depth));
        
        public @Nullable Object getLocalObject(final Thread thread, final int depth, final int slot) throws LastErrorException {
            final PointerByReference value_ptr = { };
            return GCLock.getCritical(() -> {
                checkJVMTIError(functions.GetLocalObject.invoke(getPointer(), thread, depth, slot, value_ptr));
                return JNI.INSTANCE.resolveReference(value_ptr);
            });
        }
        
        public int getLocalInt(final Thread thread, final int depth, final int slot) throws LastErrorException {
            final IntByReference value_ptr = { };
            checkJVMTIError(functions.GetLocalInt.invoke(getPointer(), thread, depth, slot, value_ptr));
            return value_ptr.getValue();
        }
        
        public long getLocalLong(final Thread thread, final int depth, final int slot) throws LastErrorException {
            final LongByReference value_ptr = { };
            checkJVMTIError(functions.GetLocalLong.invoke(getPointer(), thread, depth, slot, value_ptr));
            return value_ptr.getValue();
        }
        
        public float getLocalFloat(final Thread thread, final int depth, final int slot) throws LastErrorException {
            final FloatByReference value_ptr = { };
            checkJVMTIError(functions.GetLocalFloat.invoke(getPointer(), thread, depth, slot, value_ptr));
            return value_ptr.getValue();
        }
        
        public double getLocalDouble(final Thread thread, final int depth, final int slot) throws LastErrorException {
            final DoubleByReference value_ptr = { };
            checkJVMTIError(functions.GetLocalDouble.invoke(getPointer(), thread, depth, slot, value_ptr));
            return value_ptr.getValue();
        }
        
        public void setLocalObject(final Thread thread, final int depth, final int slot, final Object value) throws LastErrorException = checkJVMTIError(functions.SetLocalObject.invoke(getPointer(), thread, depth, slot, value));
        
        public void setLocalInt(final Thread thread, final int depth, final int slot, final int value) throws LastErrorException = checkJVMTIError(functions.SetLocalInt.invoke(getPointer(), thread, depth, slot, value));
        
        public void setLocalLong(final Thread thread, final int depth, final int slot, final long value) throws LastErrorException = checkJVMTIError(functions.SetLocalLong.invoke(getPointer(), thread, depth, slot, value));
        
        public void setLocalFloat(final Thread thread, final int depth, final int slot, final float value) throws LastErrorException = checkJVMTIError(functions.SetLocalFloat.invoke(getPointer(), thread, depth, slot, value));
        
        public void setLocalDouble(final Thread thread, final int depth, final int slot, final double value) throws LastErrorException = checkJVMTIError(functions.SetLocalDouble.invoke(getPointer(), thread, depth, slot, value));
        
        public Pointer createRawMonitor(final String name) throws LastErrorException {
            final PointerByReference monitor_ptr = { };
            checkJVMTIError(functions.CreateRawMonitor.invoke(getPointer(), name, monitor_ptr));
            return monitor_ptr.getValue();
        }
        
        public void destroyRawMonitor(final Pointer monitor) throws LastErrorException = checkJVMTIError(functions.DestroyRawMonitor.invoke(getPointer(), monitor));
        
        public void rawMonitorEnter(final Pointer monitor) throws LastErrorException = checkJVMTIError(functions.RawMonitorEnter.invoke(getPointer(), monitor));
        
        public void rawMonitorExit(final Pointer monitor) throws LastErrorException = checkJVMTIError(functions.RawMonitorExit.invoke(getPointer(), monitor));
        
        public void rawMonitorWait(final Pointer monitor, final long millis) throws LastErrorException = checkJVMTIError(functions.RawMonitorWait.invoke(getPointer(), monitor, millis));
        
        public void rawMonitorNotify(final Pointer monitor) throws LastErrorException = checkJVMTIError(functions.RawMonitorNotify.invoke(getPointer(), monitor));
        
        public void rawMonitorNotifyAll(final Pointer monitor) throws LastErrorException = checkJVMTIError(functions.RawMonitorNotifyAll.invoke(getPointer(), monitor));
        
        public void setBreakpoint(final Pointer method, final long location) throws LastErrorException = checkJVMTIError(functions.SetBreakpoint.invoke(getPointer(), method, location));
        
        public void clearBreakpoint(final Pointer method, final long location) throws LastErrorException = checkJVMTIError(functions.ClearBreakpoint.invoke(getPointer(), method, location));
        
        public @Nullable Object getNamedModule(final Object class_loader, final String package_name) throws LastErrorException {
            final PointerByReference module_ptr = { };
            return GCLock.getCritical(() -> {
                checkJVMTIError(functions.GetNamedModule.invoke(getPointer(), class_loader, package_name, module_ptr));
                return JNI.INSTANCE.resolveReference(module_ptr);
            });
        }
        
        public void setFieldAccessWatch(final Class<?> klass, final Pointer field) throws LastErrorException = checkJVMTIError(functions.SetFieldAccessWatch.invoke(getPointer(), klass, field));
        
        public void clearFieldAccessWatch(final Class<?> klass, final Pointer field) throws LastErrorException = checkJVMTIError(functions.ClearFieldAccessWatch.invoke(getPointer(), klass, field));
        
        public void setFieldModificationWatch(final Class<?> klass, final Pointer field) throws LastErrorException = checkJVMTIError(functions.SetFieldModificationWatch.invoke(getPointer(), klass, field));
        
        public void clearFieldModificationWatch(final Class<?> klass, final Pointer field) throws LastErrorException = checkJVMTIError(functions.ClearFieldModificationWatch.invoke(getPointer(), klass, field));
        
        public boolean isModifiableClass(final Class<?> klass) throws LastErrorException {
            final IntByReference is_modifiable_class_ptr = { };
            checkJVMTIError(functions.IsModifiableClass.invoke(getPointer(), klass, is_modifiable_class_ptr));
            return is_modifiable_class_ptr.getValue() != 0;
        }
        
        public Pointer allocate(final long size) throws LastErrorException {
            final PointerByReference mem_ptr = { };
            checkJVMTIError(functions.Allocate.invoke(getPointer(), size, mem_ptr));
            return mem_ptr.getValue();
        }
        
        public void deallocate(final Pointer mem) throws LastErrorException = checkJVMTIError(functions.Deallocate.invoke(getPointer(), mem));
        
        public void getClassSignature(final Class<?> klass, final PointerByReference signature_ptr, final PointerByReference generic_ptr) throws LastErrorException
                = checkJVMTIError(functions.GetClassSignature.invoke(getPointer(), klass, signature_ptr, generic_ptr));
        
        public int getClassStatus(final Class<?> klass) throws LastErrorException {
            final IntByReference status_ptr = { };
            checkJVMTIError(functions.GetClassStatus.invoke(getPointer(), klass, status_ptr));
            return status_ptr.getValue();
        }
        
        public String getSourceFileName(final Class<?> klass) throws LastErrorException {
            final PointerByReference source_name_ptr = { };
            checkJVMTIError(functions.GetSourceFileName.invoke(getPointer(), klass, source_name_ptr));
            return source_name_ptr.getValue().getString(0L);
        }
        
        public int getClassModifiers(final Class<?> klass) throws LastErrorException {
            final IntByReference modifiers_ptr = { };
            checkJVMTIError(functions.GetClassModifiers.invoke(getPointer(), klass, modifiers_ptr));
            return modifiers_ptr.getValue();
        }
        
        public void getClassMethods(final Class<?> klass, final IntByReference method_count_ptr, final PointerByReference methods_ptr) throws LastErrorException
                = checkJVMTIError(functions.GetClassMethods.invoke(getPointer(), klass, method_count_ptr, methods_ptr));
        
        public void getClassFields(final Class<?> klass, final IntByReference field_count_ptr, final PointerByReference fields_ptr) throws LastErrorException
                = checkJVMTIError(functions.GetClassFields.invoke(getPointer(), klass, field_count_ptr, fields_ptr));
        
        public void getImplementedInterfaces(final Class<?> klass, final IntByReference interface_count_ptr, final PointerByReference interfaces_ptr) throws LastErrorException
                = checkJVMTIError(functions.GetImplementedInterfaces.invoke(getPointer(), klass, interface_count_ptr, interfaces_ptr));
        
        public boolean isInterface(final Class<?> klass) throws LastErrorException {
            final IntByReference is_interface_ptr = { };
            checkJVMTIError(functions.IsInterface.invoke(getPointer(), klass, is_interface_ptr));
            return is_interface_ptr.getValue() != 0;
        }
        
        public boolean isArrayClass(final Class<?> klass) throws LastErrorException {
            final IntByReference is_array_class_ptr = { };
            checkJVMTIError(functions.IsArrayClass.invoke(getPointer(), klass, is_array_class_ptr));
            return is_array_class_ptr.getValue() != 0;
        }
        
        public @Nullable ClassLoader getClassLoader(final Class<?> klass) throws LastErrorException {
            final PointerByReference classloader_ptr = { };
            return GCLock.getCritical(() -> {
                checkJVMTIError(functions.GetClassLoader.invoke(getPointer(), klass, classloader_ptr));
                return JNI.INSTANCE.resolveReference(classloader_ptr);
            });
        }
        
        public int getObjectHashCode(final Object object) throws LastErrorException {
            final IntByReference hash_code_ptr = { };
            checkJVMTIError(functions.GetObjectHashCode.invoke(getPointer(), object, hash_code_ptr));
            return hash_code_ptr.getValue();
        }
        
        public MonitorUsage.ByReference getObjectMonitorUsage(final Object object) throws LastErrorException {
            final MonitorUsage.ByReference info_ptr = { };
            checkJVMTIError(functions.GetObjectMonitorUsage.invoke(getPointer(), object, info_ptr));
            info_ptr.autoRead();
            return info_ptr;
        }
        
        public void getFieldName(final Class<?> klass, final Pointer field, final PointerByReference name_ptr, final PointerByReference signature_ptr, final PointerByReference generic_ptr) throws LastErrorException
                = checkJVMTIError(functions.GetFieldName.invoke(getPointer(), klass, field, name_ptr, signature_ptr, generic_ptr));
        
        public void getFieldDeclaringClass(final Class<?> klass, final Pointer field, final PointerByReference declaring_class_ptr) throws LastErrorException
                = checkJVMTIError(functions.GetFieldDeclaringClass.invoke(getPointer(), klass, field, declaring_class_ptr));
        
        public boolean getFieldModifiers(final Class<?> klass, final Pointer field) throws LastErrorException {
            final IntByReference modifiers_ptr = { };
            checkJVMTIError(functions.GetFieldModifiers.invoke(getPointer(), klass, field, modifiers_ptr));
            return modifiers_ptr.getValue() != 0;
        }
        
        public boolean isFieldSynthetic(final Class<?> klass, final Pointer field) throws LastErrorException {
            final IntByReference is_synthetic_ptr = { };
            checkJVMTIError(functions.IsFieldSynthetic.invoke(getPointer(), klass, field, is_synthetic_ptr));
            return is_synthetic_ptr.getValue() != 0;
        }
        
        public void getMethodName(final Pointer method, final PointerByReference name_ptr, final PointerByReference signature_ptr, final PointerByReference generic_ptr) throws LastErrorException
                = checkJVMTIError(functions.GetMethodName.invoke(getPointer(), method, name_ptr, signature_ptr, generic_ptr));
        
        public Class<?> getMethodDeclaringClass(final Pointer method) throws LastErrorException {
            final PointerByReference declaring_class_ptr = { };
            return GCLock.getCritical(() -> {
                checkJVMTIError(functions.GetMethodDeclaringClass.invoke(getPointer(), method, declaring_class_ptr));
                return JNI.INSTANCE.resolveReference(declaring_class_ptr);
            });
        }
        
        public int getMethodModifiers(final Pointer method) throws LastErrorException {
            final IntByReference modifiers_ptr = { };
            checkJVMTIError(functions.GetMethodModifiers.invoke(getPointer(), method, modifiers_ptr));
            return modifiers_ptr.getValue();
        }
        
        public int getMaxLocals(final Pointer method) throws LastErrorException {
            final IntByReference max_ptr = { };
            checkJVMTIError(functions.GetMaxLocals.invoke(getPointer(), method, max_ptr));
            return max_ptr.getValue();
        }
        
        public int getArgumentsSize(final Pointer method) throws LastErrorException {
            final IntByReference size_ptr = { };
            checkJVMTIError(functions.GetArgumentsSize.invoke(getPointer(), method, size_ptr));
            return size_ptr.getValue();
        }
        
        public void getLineNumberTable(final Pointer method, final IntByReference entry_count_ptr, final PointerByReference table_ptr) throws LastErrorException
                = checkJVMTIError(functions.GetLineNumberTable.invoke(getPointer(), method, entry_count_ptr, table_ptr));
        
        public void getMethodLocation(final Pointer method, final LongByReference start_location_ptr, final LongByReference end_location_ptr) throws LastErrorException
                = checkJVMTIError(functions.GetMethodLocation.invoke(getPointer(), method, start_location_ptr, end_location_ptr));
        
        public void getLocalVariableTable(final Pointer method, final IntByReference entry_count_ptr, final PointerByReference table_ptr) throws LastErrorException
                = checkJVMTIError(functions.GetLocalVariableTable.invoke(getPointer(), method, entry_count_ptr, table_ptr));
        
        public void setNativeMethodPrefix(final String prefix) throws LastErrorException = checkJVMTIError(functions.SetNativeMethodPrefix.invoke(getPointer(), prefix));
        
        public void setNativeMethodPrefixes(final int prefix_count, final PointerByReference prefixes) throws LastErrorException = checkJVMTIError(functions.SetNativeMethodPrefixes.invoke(getPointer(), prefix_count, prefixes));
        
        public void getBytecodes(final Pointer method, final IntByReference bytecode_count_ptr, final PointerByReference bytecodes_ptr) throws LastErrorException
                = checkJVMTIError(functions.GetBytecodes.invoke(getPointer(), method, bytecode_count_ptr, bytecodes_ptr));
        
        public boolean isMethodNative(final Pointer method) throws LastErrorException {
            final IntByReference is_native_ptr = { };
            checkJVMTIError(functions.IsMethodNative.invoke(getPointer(), method, is_native_ptr));
            return is_native_ptr.getValue() != 0;
        }
        
        public boolean isMethodSynthetic(final Pointer method) throws LastErrorException {
            final IntByReference is_synthetic_ptr = { };
            checkJVMTIError(functions.IsMethodSynthetic.invoke(getPointer(), method, is_synthetic_ptr));
            return is_synthetic_ptr.getValue() != 0;
        }
        
        public void getLoadedClasses(final IntByReference class_count_ptr, final PointerByReference classes_ptr) throws LastErrorException = checkJVMTIError(functions.GetLoadedClasses.invoke(getPointer(), class_count_ptr, classes_ptr));
        
        public void getClassLoaderClasses(final Object initiating_loader, final IntByReference class_count_ptr, final PointerByReference classes_ptr) throws LastErrorException
                = checkJVMTIError(functions.GetClassLoaderClasses.invoke(getPointer(), initiating_loader, class_count_ptr, classes_ptr));
        
        public void popFrame(final Thread thread) throws LastErrorException = checkJVMTIError(functions.PopFrame.invoke(getPointer(), thread));
        
        public void forceEarlyReturnObject(final Thread thread, final Object value) throws LastErrorException = checkJVMTIError(functions.ForceEarlyReturnObject.invoke(getPointer(), thread, value));
        
        public void forceEarlyReturnInt(final Thread thread, final int value) throws LastErrorException = checkJVMTIError(functions.ForceEarlyReturnInt.invoke(getPointer(), thread, value));
        
        public void forceEarlyReturnLong(final Thread thread, final long value) throws LastErrorException = checkJVMTIError(functions.ForceEarlyReturnLong.invoke(getPointer(), thread, value));
        
        public void forceEarlyReturnFloat(final Thread thread, final float value) throws LastErrorException = checkJVMTIError(functions.ForceEarlyReturnFloat.invoke(getPointer(), thread, value));
        
        public void forceEarlyReturnDouble(final Thread thread, final double value) throws LastErrorException = checkJVMTIError(functions.ForceEarlyReturnDouble.invoke(getPointer(), thread, value));
        
        public void forceEarlyReturnVoid(final Thread thread) throws LastErrorException = checkJVMTIError(functions.ForceEarlyReturnVoid.invoke(getPointer(), thread));
        
        public void redefineClasses(final int class_count, final ClassDefinition.ByReference class_definitions) throws LastErrorException = checkJVMTIError(functions.RedefineClasses.invoke(getPointer(), class_count, class_definitions));
        
        public int getVersionNumber() throws LastErrorException {
            final IntByReference version_ptr = { };
            checkJVMTIError(functions.GetVersionNumber.invoke(getPointer(), version_ptr));
            return version_ptr.getValue();
        }
        
        public Capabilities.ByReference getCapabilities() throws LastErrorException {
            final Capabilities.ByReference capabilities_ptr = { };
            checkJVMTIError(functions.GetCapabilities.invoke(getPointer(), capabilities_ptr));
            return capabilities_ptr;
        }
        
        public String getSourceDebugExtension(final Class<?> klass) throws LastErrorException {
            final PointerByReference source_debug_extension_ptr = { };
            checkJVMTIError(functions.GetSourceDebugExtension.invoke(getPointer(), klass, source_debug_extension_ptr));
            return source_debug_extension_ptr.getValue().getString(0L);
        }
        
        public boolean isMethodObsolete(final Pointer method) throws LastErrorException {
            final IntByReference is_obsolete_ptr = { };
            checkJVMTIError(functions.IsMethodObsolete.invoke(getPointer(), method, is_obsolete_ptr));
            return is_obsolete_ptr.getValue() != 0;
        }
        
        public void suspendThreadList(final int request_count, final PointerByReference request_list, final IntByReference results) throws LastErrorException
                = checkJVMTIError(functions.SuspendThreadList.invoke(getPointer(), request_count, request_list, results));
        
        public void resumeThreadList(final int request_count, final PointerByReference request_list, final IntByReference results) throws LastErrorException
                = checkJVMTIError(functions.ResumeThreadList.invoke(getPointer(), request_count, request_list, results));
        
        public void addModuleReads(final Object module, final Object to_module) throws LastErrorException = checkJVMTIError(functions.AddModuleReads.invoke(getPointer(), module, to_module));
        
        public void addModuleExports(final Object module, final String pkg_name, final Object to_module) throws LastErrorException = checkJVMTIError(functions.AddModuleExports.invoke(getPointer(), module, pkg_name, to_module));
        
        public void addModuleOpens(final Object module, final String pkg_name, final Object to_module) throws LastErrorException = checkJVMTIError(functions.AddModuleOpens.invoke(getPointer(), module, pkg_name, to_module));
        
        public void addModuleUses(final Object module, final Class<?> service) throws LastErrorException = checkJVMTIError(functions.AddModuleUses.invoke(getPointer(), module, service));
        
        public void addModuleProvides(final Object module, final Class<?> service, final Class<?> impl_class) throws LastErrorException = checkJVMTIError(functions.AddModuleProvides.invoke(getPointer(), module, service, impl_class));
        
        public boolean isModifiableModule(final Object module) throws LastErrorException {
            final IntByReference is_modifiable_module_ptr = { };
            checkJVMTIError(functions.IsModifiableModule.invoke(getPointer(), module, is_modifiable_module_ptr));
            return is_modifiable_module_ptr.getValue() != 0;
        }
        
        public void getAllStackTraces(final int max_frame_count, final PointerByReference stack_info_ptr, final IntByReference thread_count_ptr) throws LastErrorException
                = checkJVMTIError(functions.GetAllStackTraces.invoke(getPointer(), max_frame_count, stack_info_ptr, thread_count_ptr));
        
        public void getThreadListStackTraces(final int thread_count, final PointerByReference thread_list, final int max_frame_count, final PointerByReference stack_info_ptr) throws LastErrorException
                = checkJVMTIError(functions.GetThreadListStackTraces.invoke(getPointer(), thread_count, thread_list, max_frame_count, stack_info_ptr));
        
        public void getThreadLocalStorage(final Thread thread, final PointerByReference data_ptr) throws LastErrorException = checkJVMTIError(functions.GetThreadLocalStorage.invoke(getPointer(), thread, data_ptr));
        
        public void setThreadLocalStorage(final Thread thread, final Pointer data) throws LastErrorException = checkJVMTIError(functions.SetThreadLocalStorage.invoke(getPointer(), thread, data));
        
        public void getStackTrace(final Thread thread, final int start_depth, final int max_frame_count, final PointerByReference frame_buffer, final IntByReference count_ptr) throws LastErrorException
                = checkJVMTIError(functions.GetStackTrace.invoke(getPointer(), thread, start_depth, max_frame_count, frame_buffer, count_ptr));
        
        public long getTag(final Object object) throws LastErrorException {
            final LongByReference tag_ptr = { };
            checkJVMTIError(functions.GetTag.invoke(getPointer(), object, tag_ptr));
            return tag_ptr.getValue();
        }
        
        public void setTag(final Object object, final long tag) throws LastErrorException = checkJVMTIError(functions.SetTag.invoke(getPointer(), object, tag));
        
        public void forceGarbageCollection() throws LastErrorException = checkJVMTIError(functions.ForceGarbageCollection.invoke(getPointer()));
        
        public void iterateOverObjectsReachableFromObject(final Object object, final ObjectReferenceCallback object_reference_callback, final Pointer user_data) throws LastErrorException
                = checkJVMTIError(functions.IterateOverObjectsReachableFromObject.invoke(getPointer(), object, object_reference_callback, user_data));
        
        public void iterateOverReachableObjects(final HeapRootCallback heap_root_callback, final StackReferenceCallback stack_ref_callback, final ObjectReferenceCallback object_ref_callback,
                final Pointer user_data) throws LastErrorException
                = checkJVMTIError(functions.IterateOverReachableObjects.invoke(getPointer(), heap_root_callback, stack_ref_callback, object_ref_callback, user_data));
        
        public void iterateOverHeap(final int object_filter, final HeapObjectCallback heap_object_callback, final Pointer user_data) throws LastErrorException
                = checkJVMTIError(functions.IterateOverHeap.invoke(getPointer(), object_filter, heap_object_callback, user_data));
        
        public void iterateOverInstancesOfClass(final Class<?> klass, final int object_filter, final HeapObjectCallback heap_object_callback, final Pointer user_data) throws LastErrorException
                = checkJVMTIError(functions.IterateOverInstancesOfClass.invoke(getPointer(), klass, object_filter, heap_object_callback, user_data));
        
        public void getObjectsWithTags(final int tag_count, final LongByReference tags, final IntByReference count_ptr, final PointerByReference object_result_ptr, final PointerByReference tag_result_ptr) throws LastErrorException
                = checkJVMTIError(functions.GetObjectsWithTags.invoke(getPointer(), tag_count, tags, count_ptr, object_result_ptr, tag_result_ptr));
        
        public void followReferences(final int heap_filter, final Class<?> klass, final Object initial_object, final HeapCallbacks.ByReference callbacks, final Pointer user_data) throws LastErrorException
                = checkJVMTIError(functions.FollowReferences.invoke(getPointer(), heap_filter, klass, initial_object, callbacks, user_data));
        
        public void iterateThroughHeap(final int heap_filter, final Class<?> klass, final HeapCallbacks.ByReference callbacks, final Pointer user_data) throws LastErrorException
                = checkJVMTIError(functions.IterateThroughHeap.invoke(getPointer(), heap_filter, klass, callbacks, user_data));
        
        public void setJNIFunctionTable(final JNI.NativeInterface.ByReference function_table) throws LastErrorException = checkJVMTIError(functions.SetJNIFunctionTable.invoke(getPointer(), function_table));
        
        public void getJNIFunctionTable(final PointerByReference function_table) throws LastErrorException = checkJVMTIError(functions.GetJNIFunctionTable.invoke(getPointer(), function_table));
        
        public void setEventCallbacks(final EventCallbacks.ByReference callbacks, final int size_of_callbacks = callbacks.size()) throws LastErrorException
                = checkJVMTIError(functions.SetEventCallbacks.invoke(getPointer(), callbacks, size_of_callbacks));
        
        public void generateEvents(final int event_type) throws LastErrorException = checkJVMTIError(functions.GenerateEvents.invoke(getPointer(), event_type));
        
        public void getExtensionFunctions(final IntByReference extension_count_ptr, final PointerByReference extensions) throws LastErrorException
                = checkJVMTIError(functions.GetExtensionFunctions.invoke(getPointer(), extension_count_ptr, extensions));
        
        public void getExtensionEvents(final IntByReference extension_count_ptr, final PointerByReference extensions) throws LastErrorException
                = checkJVMTIError(functions.GetExtensionEvents.invoke(getPointer(), extension_count_ptr, extensions));
        
        public void setExtensionEventCallback(final int extension_event_index, final ExtensionEvent callback) throws LastErrorException
                = checkJVMTIError(functions.SetExtensionEventCallback.invoke(getPointer(), extension_event_index, callback));
        
        public void disposeEnvironment() throws LastErrorException = checkJVMTIError(functions.DisposeEnvironment.invoke(getPointer()));
        
        public String getErrorName(final int error) throws LastErrorException {
            final PointerByReference name_ptr = { };
            checkJVMTIError(functions.GetErrorName.invoke(getPointer(), error, name_ptr));
            return name_ptr.getValue().getString(0L);
        }
        
        public int getJLocationFormat() throws LastErrorException {
            final IntByReference format_ptr = { };
            checkJVMTIError(functions.GetJLocationFormat.invoke(getPointer(), format_ptr));
            return format_ptr.getValue();
        }
        
        public void getSystemProperties(final IntByReference count_ptr, final PointerByReference property_ptr) throws LastErrorException = checkJVMTIError(functions.GetSystemProperties.invoke(getPointer(), count_ptr, property_ptr));
        
        public String getSystemProperty(final String property) throws LastErrorException {
            final PointerByReference value_ptr = { };
            return GCLock.getCritical(() -> {
                checkJVMTIError(functions.GetSystemProperty.invoke(getPointer(), property, value_ptr));
                return JNI.INSTANCE.resolveReference(value_ptr);
            });
        }
        
        public void setSystemProperty(final String property, final String value) throws LastErrorException = checkJVMTIError(functions.SetSystemProperty.invoke(getPointer(), property, value));
        
        public int getPhase() throws LastErrorException {
            final IntByReference phase_ptr = { };
            checkJVMTIError(functions.GetPhase.invoke(getPointer(), phase_ptr));
            return phase_ptr.getValue();
        }
        
        public TimerInfo.ByReference getCurrentThreadCpuTimerInfo() throws LastErrorException {
            final TimerInfo.ByReference info_ptr = { };
            checkJVMTIError(functions.GetCurrentThreadCpuTimerInfo.invoke(getPointer(), info_ptr));
            return info_ptr;
        }
        
        public long getCurrentThreadCpuTime() throws LastErrorException {
            final LongByReference nanos_ptr = { };
            checkJVMTIError(functions.GetCurrentThreadCpuTime.invoke(getPointer(), nanos_ptr));
            return nanos_ptr.getValue();
        }
        
        public TimerInfo.ByReference getThreadCpuTimerInfo() throws LastErrorException {
            final TimerInfo.ByReference info_ptr = { };
            checkJVMTIError(functions.GetThreadCpuTimerInfo.invoke(getPointer(), info_ptr));
            return info_ptr;
        }
        
        public long getThreadCpuTime(final Thread thread) throws LastErrorException {
            final LongByReference nanos_ptr = { };
            checkJVMTIError(functions.GetThreadCpuTime.invoke(getPointer(), thread, nanos_ptr));
            return nanos_ptr.getValue();
        }
        
        public TimerInfo.ByReference getTimerInfo() throws LastErrorException {
            final TimerInfo.ByReference info_ptr = { };
            checkJVMTIError(functions.GetTimerInfo.invoke(getPointer(), info_ptr));
            return info_ptr;
        }
        
        public long getTime() throws LastErrorException {
            final LongByReference nanos_ptr = { };
            checkJVMTIError(functions.GetTime.invoke(getPointer(), nanos_ptr));
            return nanos_ptr.getValue();
        }
        
        public Capabilities.ByReference getPotentialCapabilities() throws LastErrorException {
            final Capabilities.ByReference capabilities_ptr = { };
            checkJVMTIError(functions.GetPotentialCapabilities.invoke(getPointer(), capabilities_ptr));
            return capabilities_ptr;
        }
        
        public void addCapabilities(final Capabilities.ByReference capabilities_ptr) throws LastErrorException = checkJVMTIError(functions.AddCapabilities.invoke(getPointer(), capabilities_ptr));
        
        public void addPotentialCapabilities() throws LastErrorException = addCapabilities(getPotentialCapabilities());
        
        public void relinquishCapabilities(final Capabilities.ByReference capabilities_ptr) throws LastErrorException = checkJVMTIError(functions.RelinquishCapabilities.invoke(getPointer(), capabilities_ptr));
        
        public void relinquishPotentialCapabilities() throws LastErrorException = relinquishCapabilities(getPotentialCapabilities());
        
        public void getAvailableProcessors(final IntByReference processor_count_ptr) throws LastErrorException = checkJVMTIError(functions.GetAvailableProcessors.invoke(getPointer(), processor_count_ptr));
        
        public void getClassVersionNumbers(final Class<?> klass, final IntByReference minor_version_ptr, final IntByReference major_version_ptr) throws LastErrorException
                = checkJVMTIError(functions.GetClassVersionNumbers.invoke(getPointer(), klass, minor_version_ptr, major_version_ptr));
        
        public void getConstantPool(final Class<?> klass, final IntByReference constant_pool_count_ptr, final IntByReference constant_pool_byte_count_ptr, final PointerByReference constant_pool_bytes_ptr) throws LastErrorException
                = checkJVMTIError(functions.GetConstantPool.invoke(getPointer(), klass, constant_pool_count_ptr, constant_pool_byte_count_ptr, constant_pool_bytes_ptr));
        
        public void getEnvironmentLocalStorage(final PointerByReference data_ptr) throws LastErrorException = checkJVMTIError(functions.GetEnvironmentLocalStorage.invoke(getPointer(), data_ptr));
        
        public void setEnvironmentLocalStorage(final Pointer data) throws LastErrorException = checkJVMTIError(functions.SetEnvironmentLocalStorage.invoke(getPointer(), data));
        
        public void addToBootstrapClassLoaderSearch(final String segment) throws LastErrorException = checkJVMTIError(functions.AddToBootstrapClassLoaderSearch.invoke(getPointer(), segment));
        
        public void setVerboseFlag(final int flag, final boolean value) throws LastErrorException = checkJVMTIError(functions.SetVerboseFlag.invoke(getPointer(), flag, value));
        
        public void addToSystemClassLoaderSearch(final String segment) throws LastErrorException = checkJVMTIError(functions.AddToSystemClassLoaderSearch.invoke(getPointer(), segment));
        
        public void retransformClasses(final int class_count, final PointerByReference classes) throws LastErrorException = checkJVMTIError(functions.RetransformClasses.invoke(getPointer(), class_count, classes));
        
        public void getOwnedMonitorStackDepthInfo(final Thread thread, final IntByReference monitor_info_count_ptr, final PointerByReference monitor_info_ptr) throws LastErrorException
                = checkJVMTIError(functions.GetOwnedMonitorStackDepthInfo.invoke(getPointer(), thread, monitor_info_count_ptr, monitor_info_ptr));
        
        public long getObjectSize(final Object object) throws LastErrorException {
            final LongByReference size_ptr = { };
            checkJVMTIError(functions.GetObjectSize.invoke(getPointer(), object, size_ptr));
            return size_ptr.getValue();
        }
        
        public Object getLocalInstance(final Thread thread, final int depth) throws LastErrorException {
            final PointerByReference value_ptr = { };
            return GCLock.getCritical(() -> {
                checkJVMTIError(functions.GetLocalInstance.invoke(getPointer(), thread, depth, value_ptr));
                return JNI.INSTANCE.resolveReference(value_ptr);
            });
        }
        
        public void setHeapSamplingInterval(final int sampling_interval) throws LastErrorException = checkJVMTIError(functions.SetHeapSamplingInterval.invoke(getPointer(), sampling_interval));
        
    }
    
    static String jvmtiReturnCodeName(final int jvmtiReturnCode) = lookup.lookupFieldName(jvmtiReturnCode, name -> name.startsWith("JVMTI_ERROR_"));
    
    static void checkJVMTIError(final int jvmtiReturnCode) throws LastErrorException {
        if (jvmtiReturnCode != JVMTI_ERROR_NONE)
            throw new LastErrorException(STR."\{jvmtiReturnCodeName(jvmtiReturnCode)}(\{jvmtiReturnCode})");
    }
    
}
