package amadeus.maho.vm;

import java.util.List;
import java.util.stream.Stream;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.ToString;
import amadeus.maho.transform.mark.Proxy;
import amadeus.maho.transform.mark.base.InvisibleType;
import amadeus.maho.transform.mark.base.TransformProvider;

import com.sun.management.VMOption;

import static org.objectweb.asm.Opcodes.*;

@TransformProvider
public abstract class JVMFlags {
    
    @ToString
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
    public static class Flag {
        
        private Object flag;
        String name;
        VMOption.Origin origin;
        boolean writeable;
        boolean external;
        
        public Object value() = JVMFlags.value(flag);
        
        public void setLongValue(final long value) = JVMFlags.setLongValue(name, value);
    
        public void setDoubleValue(final double value) = JVMFlags.setDoubleValue(name, value);
    
        public void setBooleanValue(final boolean value) = JVMFlags.setBooleanValue(name, value);
    
        public void setStringValue(final String value) = JVMFlags.setStringValue(name, value);
    
    }
    
    public static Stream<Flag> stream() = getAllFlags().stream().map(flag -> new Flag(flag, name(flag), origin(flag), writeable(flag), external(flag)));
    
    private static final String Flag = "com.sun.management.internal.Flag";
    
    @Proxy(target = Flag, value = INVOKESTATIC)
    private static native List<@InvisibleType(Flag) Object> getAllFlags();
    
    @Proxy(target = Flag, value = INVOKESTATIC)
    private static native void setLongValue(String name, long value);
    
    @Proxy(target = Flag, value = INVOKESTATIC)
    private static native void setDoubleValue(String name, double value);
    
    @Proxy(target = Flag, value = INVOKESTATIC)
    private static native void setBooleanValue(String name, boolean value);
    
    @Proxy(target = Flag, value = INVOKESTATIC)
    private static native void setStringValue(String name, String value);
    
    @Proxy(GETFIELD)
    private static native String name(final @InvisibleType(Flag) Object $this);
    
    @Proxy(GETFIELD)
    private static native Object value(final @InvisibleType(Flag) Object $this);
    
    @Proxy(GETFIELD)
    private static native VMOption.Origin origin(final @InvisibleType(Flag) Object $this);
    
    @Proxy(GETFIELD)
    private static native boolean writeable(final @InvisibleType(Flag) Object $this);
    
    @Proxy(GETFIELD)
    private static native boolean external(final @InvisibleType(Flag) Object $this);
    
}
