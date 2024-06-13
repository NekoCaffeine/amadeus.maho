package amadeus.maho.vm.reflection.hotspot;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import jdk.internal.misc.Unsafe;

import amadeus.maho.core.Maho;
import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.runtime.UnsafeHelper;
import amadeus.maho.vm.reflection.JVMReflectiveOperationException;
import amadeus.maho.vm.reflection.JVMSymbols;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum HotSpot {
    
    @Getter
    instance;
    
    Unsafe unsafe = UnsafeHelper.unsafe();
    
    Map<String, HotSpotType> types = new LinkedHashMap<>();
    
    Map<String, Number> constants = new LinkedHashMap<>();
    
    Map<String, HotSpotFlag> flags = new LinkedHashMap<>();
    
    { initVMData(); }
    
    private synchronized void initVMData() {
        readVmTypes(readVmStructs());
        readVmIntConstants();
        readVmLongConstants();
        readVmFlags();
    }
    
    private Map<String, Set<HotSpotField>> readVmStructs() {
        long entry = getSymbol("gHotSpotVMStructs");
        final long typeNameOffset = getSymbol("gHotSpotVMStructEntryTypeNameOffset");
        final long fieldNameOffset = getSymbol("gHotSpotVMStructEntryFieldNameOffset");
        final long typeStringOffset = getSymbol("gHotSpotVMStructEntryTypeStringOffset");
        final long isStaticOffset = getSymbol("gHotSpotVMStructEntryIsStaticOffset");
        final long offsetOffset = getSymbol("gHotSpotVMStructEntryOffsetOffset");
        final long addressOffset = getSymbol("gHotSpotVMStructEntryAddressOffset");
        final long arrayStride = getSymbol("gHotSpotVMStructEntryArrayStride");
        final Map<String, Set<HotSpotField>> structs = new HashMap<>();
        for (; ; entry += arrayStride) {
            final @Nullable String fieldName = getStringRef(entry + fieldNameOffset);
            if (fieldName == null)
                break;
            structs.computeIfAbsent(getStringRef(entry + typeNameOffset), _ -> new TreeSet<>()) +=
                    new HotSpotField(fieldName, getStringRef(entry + typeStringOffset), unsafe.getLong(entry + (unsafe.getInt(entry + isStaticOffset) != 0 ? addressOffset : offsetOffset)), unsafe.getInt(entry + isStaticOffset) != 0);
        }
        return structs;
    }
    
    private void readVmTypes(final Map<String, Set<HotSpotField>> structs) {
        long entry = getSymbol("gHotSpotVMTypes");
        final long typeNameOffset = getSymbol("gHotSpotVMTypeEntryTypeNameOffset");
        final long superclassNameOffset = getSymbol("gHotSpotVMTypeEntrySuperclassNameOffset");
        final long isOopTypeOffset = getSymbol("gHotSpotVMTypeEntryIsOopTypeOffset");
        final long isIntegerTypeOffset = getSymbol("gHotSpotVMTypeEntryIsIntegerTypeOffset");
        final long isUnsignedOffset = getSymbol("gHotSpotVMTypeEntryIsUnsignedOffset");
        final long sizeOffset = getSymbol("gHotSpotVMTypeEntrySizeOffset");
        final long arrayStride = getSymbol("gHotSpotVMTypeEntryArrayStride");
        for (; ; entry += arrayStride) {
            final @Nullable String typeName = getStringRef(entry + typeNameOffset);
            if (typeName == null)
                break;
            types[typeName] = {
                    typeName,
                    getStringRef(entry + superclassNameOffset),
                    unsafe.getInt(entry + sizeOffset),
                    unsafe.getInt(entry + isOopTypeOffset) != 0,
                    unsafe.getInt(entry + isIntegerTypeOffset) != 0,
                    unsafe.getInt(entry + isUnsignedOffset) != 0,
                    structs[typeName]
            };
        }
    }
    
    private void readVmIntConstants() {
        long entry = getSymbol("gHotSpotVMIntConstants");
        final long nameOffset = getSymbol("gHotSpotVMIntConstantEntryNameOffset");
        final long valueOffset = getSymbol("gHotSpotVMIntConstantEntryValueOffset");
        final long arrayStride = getSymbol("gHotSpotVMIntConstantEntryArrayStride");
        for (; ; entry += arrayStride) {
            final @Nullable String name = getStringRef(entry + nameOffset);
            if (name == null)
                break;
            constants[name] = unsafe.getInt(entry + valueOffset);
        }
    }
    
    private void readVmLongConstants() {
        long entry = getSymbol("gHotSpotVMLongConstants");
        final long nameOffset = getSymbol("gHotSpotVMLongConstantEntryNameOffset");
        final long valueOffset = getSymbol("gHotSpotVMLongConstantEntryValueOffset");
        final long arrayStride = getSymbol("gHotSpotVMLongConstantEntryArrayStride");
        for (; ; entry += arrayStride) {
            final @Nullable String name = getStringRef(entry + nameOffset);
            if (name == null)
                break;
            constants[name] = unsafe.getLong(entry + valueOffset);
        }
    }
    
    private void readVmFlags() {
        final HotSpotType flagType = type("JVMFlag");
        final int flagSize = flagType.size;
        final int numFlagsValue = unsafe.getInt(flagType.field("numFlags").offset); // numFlagsValue - 1 => null
        final HotSpotField _addr = flagType.field("_addr"), _name = flagType.field("_name"), _flags = flagType.field("_flags"), _type = flagType.field("_type");
        long address = unsafe.getAddress(flagType.field("flags").offset);
        for (int i = 0; i < numFlagsValue - 1; i++) {
            final String flagName = getString(unsafe.getAddress(address + _name.offset));
            flags[flagName] = { address + _addr.offset, flagName, unsafe.getInt(address + _flags.offset), unsafe.getInt(address + _type.offset) };
            address += flagSize;
        }
    }
    
    public @Nullable String getString(final long addr) {
        if (addr == 0)
            return null;
        char chars[] = new char[40];
        int offset = 0;
        for (byte b; (b = unsafe.getByte(addr + offset)) != 0; ) {
            if (offset >= chars.length)
                chars = Arrays.copyOf(chars, offset * 2);
            chars[offset++] = (char) b;
        }
        return { chars, 0, offset };
    }
    
    public @Nullable String getStringRef(final long addr) = getString(unsafe.getAddress(addr));
    
    @SneakyThrows
    public long getSymbol(final String name) {
        final long address = JVMSymbols.lookup(name);
        if (address == 0)
            throw new JVMReflectiveOperationException(STR."No such symbol: \{name}");
        return unsafe.getLong(address);
    }
    
    HotSpotType symbolType = type("Symbol");
    long _body = symbolType.offset("_body"), _length = symbolType.offset("_length");
    
    public String getSymbol(final long symbolAddress) {
        final long symbol = unsafe.getAddress(symbolAddress);
        final long body = symbol + _body;
        final int length = unsafe.getShort(symbol + _length);
        final byte buffer[] = new byte[length];
        for (int i = 0; i < length; i++)
            buffer[i] = unsafe.getByte(body + i);
        return { buffer, StandardCharsets.UTF_8 };
    }
    
    @SneakyThrows
    public HotSpotType type(final String name) {
        final HotSpotType type = types[name];
        if (type == null)
            throw new JVMReflectiveOperationException(STR."No such type: \{name}");
        return type;
    }
    
    @SneakyThrows
    public Number constant(final String name) {
        final Number constant = constants[name];
        if (constant == null)
            throw new JVMReflectiveOperationException(STR."No such constant: \{name}");
        return constant;
    }
    
    public int intConstant(final String name) = constant(name).intValue();
    
    public long longConstant(final String name) = constant(name).longValue();
    
    @SneakyThrows
    public HotSpotFlag flag(final String name) {
        final HotSpotFlag flag = flags[name];
        if (flag == null)
            throw new JVMReflectiveOperationException(STR."No such flag: \{name}");
        return flag;
    }
    
    public void dump(final List<String> list, final String subHead) {
        list += "HotSpot Constants:";
        constants.forEach((key, value) -> list += STR."\{subHead} const(\{value instanceof Long ? "long" : "int"}) \{key} -> \{value}");
        list += "HotSpot Types:";
        types.values().forEach(type -> type.dump(list, subHead));
    }
    
}
