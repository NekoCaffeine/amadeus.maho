package amadeus.maho.vm.reflection.hotspot;

import java.util.stream.Stream;

import static amadeus.maho.vm.reflection.hotspot.HotSpotFlag.Flags.*;

public record HotSpotFlag(long address, String name, int flags, int type) implements Comparable<HotSpotFlag>, HotSpotBase {
    
    private interface Flags {
        
        int
                KIND_PRODUCT            = 1 << 4,
                KIND_MANAGEABLE         = 1 << 5,
                KIND_DIAGNOSTIC         = 1 << 6,
                KIND_EXPERIMENTAL       = 1 << 7,
                KIND_NOT_PRODUCT        = 1 << 8,
                KIND_DEVELOP            = 1 << 9,
                KIND_PLATFORM_DEPENDENT = 1 << 10,
                KIND_C1                 = 1 << 11,
                KIND_C2                 = 1 << 12,
                KIND_ARCH               = 1 << 13,
                KIND_LP64_PRODUCT       = 1 << 14,
                KIND_JVMCI              = 1 << 15,
                DEFAULT                 = jvm.intConstant("JVMFlagOrigin::DEFAULT"),
                COMMAND_LINE            = jvm.intConstant("JVMFlagOrigin::COMMAND_LINE"),
                ENVIRON_VAR             = jvm.intConstant("JVMFlagOrigin::ENVIRON_VAR"),
                CONFIG_FILE             = jvm.intConstant("JVMFlagOrigin::CONFIG_FILE"),
                MANAGEMENT              = jvm.intConstant("JVMFlagOrigin::MANAGEMENT"),
                ERGONOMIC               = jvm.intConstant("JVMFlagOrigin::ERGONOMIC"),
                ATTACH_ON_DEMAND        = jvm.intConstant("JVMFlagOrigin::ATTACH_ON_DEMAND"),
                INTERNAL                = jvm.intConstant("JVMFlagOrigin::INTERNAL"),
                JIMAGE_RESOURCE         = jvm.intConstant("JVMFlagOrigin::JIMAGE_RESOURCE"),
                VALUE_ORIGIN_MASK       = jvm.intConstant("JVMFlag::VALUE_ORIGIN_MASK"),
                WAS_SET_ON_COMMAND_LINE = jvm.intConstant("JVMFlag::WAS_SET_ON_COMMAND_LINE");
        
        HotSpotType types[] = Stream.of("bool", "int", "uint", "intx", "uintx", "uint64_t", "size_t", "double", "void*", "void*").map(HotSpot.instance()::type).toArray(HotSpotType[]::new);
        
    }
    
    @Override
    public int compareTo(final HotSpotFlag o) = name.compareTo(o.name);
    
    public long resoleAddress() = unsafe.getAddress(address());
    
    public boolean asBool() = unsafe.getByte(resoleAddress()) != 0;
    
    public void asBool(final boolean flag) = unsafe.putByte(resoleAddress(), (byte) (flag ? 1 : 0));
    
    public void enable() = asBool(true);
    
    public void disable() = asBool(false);
    
    public String value() {
        if (type >= types.length)
            return "<unknown>";
        final HotSpotType type = types[this.type];
        final long address = resoleAddress();
        return switch (type.name) {
            case "bool"   -> Boolean.toString(unsafe.getByte(address) != 0);
            case "double" -> Double.toString(unsafe.getDouble(address));
            case "void*"  -> jvm.getStringRef(address);
            default       -> switch (type.size) {
                case 1  -> type.isUnsigned ? Long.toUnsignedString(unsafe.getByte(address)) : Long.toString(unsafe.getByte(address));
                case 2  -> type.isUnsigned ? Long.toUnsignedString(unsafe.getShort(address)) : Long.toString(unsafe.getShort(address));
                case 4  -> type.isUnsigned ? Long.toUnsignedString(unsafe.getInt(address)) : Long.toString(unsafe.getInt(address));
                case 8  -> type.isUnsigned ? Long.toUnsignedString(unsafe.getLong(address)) : Long.toString(unsafe.getLong(address));
                default -> "<unknown int:%d>".formatted(type.size);
            };
        };
    }
    
    public String origin() {
        final int origin = flags & VALUE_ORIGIN_MASK;
        if (origin == DEFAULT)
            return "default";
        else if (origin == COMMAND_LINE)
            return "command line";
        else if (origin == ENVIRON_VAR)
            return "environment";
        else if (origin == CONFIG_FILE)
            return "config file";
        else if (origin == MANAGEMENT)
            return "management";
        else if (origin == ERGONOMIC)
            return "ergonomic";
        else if (origin == (ERGONOMIC | WAS_SET_ON_COMMAND_LINE))
            return "command line, ergonomic";
        else if (origin == ATTACH_ON_DEMAND)
            return "attach";
        else if (origin == INTERNAL)
            return "internal";
        else if (origin == JIMAGE_RESOURCE)
            return "jimage";
        else
            return "<unknown>";
    }
    
    public String kind() = switch (flags & ~(VALUE_ORIGIN_MASK | WAS_SET_ON_COMMAND_LINE)) {
        case KIND_PRODUCT            -> "product";
        case KIND_MANAGEABLE         -> "manageable";
        case KIND_DIAGNOSTIC         -> "diagnostic";
        case KIND_EXPERIMENTAL       -> "experimental";
        case KIND_NOT_PRODUCT        -> "not_product";
        case KIND_DEVELOP            -> "develop";
        case KIND_PLATFORM_DEPENDENT -> "platform_dependent";
        case KIND_C1                 -> "c1";
        case KIND_C2                 -> "c2";
        case KIND_ARCH               -> "arch";
        case KIND_LP64_PRODUCT       -> "lp64_product";
        case KIND_JVMCI              -> "jvmci";
        default                      -> "<unknown>";
    };
    
}
