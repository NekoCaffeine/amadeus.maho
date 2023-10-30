package amadeus.maho.vm.tools.hotspot;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.MemoryUsage;
import java.lang.reflect.Array;
import java.lang.reflect.Executable;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import amadeus.maho.core.Maho;
import amadeus.maho.core.MahoBridge;
import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.transform.AOTTransformer;
import amadeus.maho.transform.mark.Erase;
import amadeus.maho.transform.mark.Init;
import amadeus.maho.transform.mark.Patch;
import amadeus.maho.transform.mark.Remap;
import amadeus.maho.transform.mark.Share;
import amadeus.maho.transform.mark.TransformTarget;
import amadeus.maho.transform.mark.base.TransformMetadata;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.bytecode.context.TransformContext;
import amadeus.maho.util.bytecode.generator.MethodGenerator;
import amadeus.maho.util.bytecode.remap.ClassNameRemapHandler;
import amadeus.maho.util.bytecode.remap.RemapHandler;
import amadeus.maho.util.runtime.ArrayHelper;
import amadeus.maho.util.runtime.MethodHandleHelper;
import amadeus.maho.vm.reflection.hotspot.HotSpot;
import amadeus.maho.vm.tools.hotspot.parser.DiagnosticCommand;

import static amadeus.maho.core.extension.DynamicLookupHelper.makeSiteByNameWithBoot;
import static java.util.Objects.requireNonNull;
import static org.objectweb.asm.Opcodes.*;

@SuppressWarnings("unused")
@Share(target = WhiteBox.Names.WhiteBox, remap = @Remap(mapping = {
        WhiteBox.Names.DiagnosticCommand_Shadow, WhiteBox.Names.DiagnosticCommand
}), required = {
        WhiteBox.Names.DiagnosticCommand
}, erase = @Erase(innerClass = true), init = @Init(initialized = true, invokeMethod = "ready"))
@NoArgsConstructor(AccessLevel.PRIVATE)
public enum WhiteBox {
    
    @Getter
    instance;
    
    public interface Names {
        
        String
                WhiteBoxAPI                   = "WhiteBoxAPI",
                getWhiteBox                   = "getWhiteBox",
                WhiteBox                      = "jdk.test.whitebox.WhiteBox",
                WhiteBox_Shadow               = "amadeus.maho.vm.tools.hotspot.WhiteBox",
                DiagnosticCommand             = "jdk.test.whitebox.parser.DiagnosticCommand",
                DiagnosticCommand_Shadow      = "amadeus.maho.vm.tools.hotspot.parser.DiagnosticCommand",
                DiagnosticArgumentType        = "jdk.test.whitebox.parser.DiagnosticCommand$DiagnosticArgumentType",
                DiagnosticArgumentType_Shadow = "amadeus.maho.vm.tools.hotspot.parser.DiagnosticCommand$DiagnosticArgumentType";
        
    }
    
    @SneakyThrows
    private interface Bridge {
        
        Class<?>
                DiagnosticCommand      = Class.forName(Names.DiagnosticCommand),
                DiagnosticArgumentType = Class.forName(Names.DiagnosticArgumentType);
        
        MethodHandle constructor = MethodHandleHelper.lookup().findConstructor(DiagnosticCommand, MethodType.methodType(void.class, String.class, String.class, DiagnosticArgumentType, boolean.class, String.class, boolean.class));
        
        static Object map(final DiagnosticCommand command)
                = constructor.invoke(command.name, command.desc, Enum.valueOf((Class<? extends Enum>) DiagnosticArgumentType, command.type.name()), command.mandatory, command.defaultValue, command.argument);
        
        static Object map(final DiagnosticCommand commands[]) {
            final Object mappedArray = Array.newInstance(DiagnosticCommand, commands.length);
            for (int i = 0; i < commands.length; i++)
                Array.set(mappedArray, i, map(commands[i]));
            return mappedArray;
        }
        
    }
    
    @TransformProvider
    private interface Proxy {
        
        RemapHandler remapHandler = ClassNameRemapHandler.of(Map.of(ASMHelper.className(Names.DiagnosticCommand_Shadow), ASMHelper.className(Names.DiagnosticCommand)));
        
        RemapHandler.ASMRemapper asmRemapper = remapHandler.remapper();
        
        Type
                WhiteBox = Type.getObjectType(ASMHelper.className(Names.WhiteBox)),
                DiagnosticCommandShadowArray = ASMHelper.arrayType(Type.getObjectType(ASMHelper.className(Names.DiagnosticCommand_Shadow))),
                DiagnosticCommandArray = ASMHelper.arrayType(Type.getObjectType(ASMHelper.className(Names.DiagnosticCommand)));
        
        Handle getWhiteBox = {
                H_INVOKESTATIC,
                ASMHelper.className(Proxy.class),
                Names.getWhiteBox,
                Type.getMethodDescriptor(
                        ASMHelper.TYPE_OBJECT,
                        ASMHelper.TYPE_METHOD_HANDLES_LOOKUP,
                        ASMHelper.TYPE_STRING,
                        ASMHelper.TYPE_CLASS
                ),
                true
        };
        
        @TransformTarget(target = Names.WhiteBox_Shadow, metadata = @TransformMetadata(aotLevel = AOTTransformer.Level.RUNTIME))
        private static void proxyMethod(final TransformContext context, final ClassNode node) {
            context.markModified();
            for (final MethodNode methodNode : node.methods)
                if (ASMHelper.anyMatch(methodNode.access, ACC_NATIVE)) {
                    methodNode.maxLocals = methodNode.maxStack = 1 + (Type.getArgumentsAndReturnSizes(methodNode.desc) >> 2);
                    methodNode.access &= ~ACC_NATIVE;
                    final String
                            mappedDesc = asmRemapper.mapMethodDesc(methodNode.desc),
                            desc = Type.getMethodDescriptor(Type.getReturnType(mappedDesc), ArrayHelper.insert(Type.getArgumentTypes(mappedDesc), WhiteBox));
                    final MethodGenerator generator = MethodGenerator.fromMethodNode(methodNode);
                    generator.push(new ConstantDynamic("INSTANCE", ASMHelper.classDesc(Names.WhiteBox), getWhiteBox));
                    generator.loadArgs();
                    switch (methodNode.name) {
                        case "parseCommandLine0" -> {
                            generator.invokeStatic(Type.getType(Bridge.class), new Method("map", ASMHelper.TYPE_OBJECT, new Type[]{ DiagnosticCommandShadowArray }));
                            generator.checkCast(DiagnosticCommandArray);
                        }
                    }
                    generator.invokeDynamic(methodNode.name, desc, makeSiteByNameWithBoot, INVOKESPECIAL, Names.WhiteBox, "");
                    generator.returnValue();
                    generator.endMethod();
                }
        }
        
        @SneakyThrows
        private static Object getWhiteBox(final MethodHandles.Lookup lookup, final String name, final Class<?> type) = MethodHandleHelper.lookup().findStatic(type, Names.getWhiteBox, MethodType.methodType(type)).invoke();
        
    }
    
    @SneakyThrows
    @NoArgsConstructor(on = @Patch.Exception)
    @Patch(target = Names.WhiteBox, remap = @Remap(mapping = {
            Names.WhiteBox_Shadow, Names.WhiteBox
    }))
    private static abstract class Patcher {
        
        private static native void registerNatives();
        
        @Patch.Remove
        public static native void openJVMFlag();
        
        public static void ready() = MahoBridge.bridgeClassLoader().loadClass(Names.WhiteBox_Shadow).getDeclaredMethod("ready").invoke(null);
        
        @Patch.Spare
        @SuppressWarnings("DataFlowIssue")
        public static Patcher getWhiteBox() = (Patcher) (Object) instance;
        
        static { MahoBridge.bridgeClassLoader().loadClass(Names.WhiteBox_Shadow).getDeclaredMethod("openJVMFlag").invoke(null); }
        
        static { registerNatives(); }
        
    }
    
    public static synchronized void openJVMFlag() {
        Maho.debug("WhiteBox: Open JVM Flag -> " + Names.WhiteBoxAPI);
        HotSpot.instance().flag(Names.WhiteBoxAPI).enable();
    }
    
    public static synchronized void ready() {
        try {
            Maho.debug("WhiteBox: Ready!");
            JIT.instance().ready();
        } catch (final NoClassDefFoundError e) {
            try {
                MahoBridge.bridgeClassLoader().loadClass("amadeus.maho.util.runtime.DebugHelper").getDeclaredMethod("breakpoint").invoke(null);
            } catch (final ReflectiveOperationException ignored) { }
            throw new IllegalStateException("The reason for this error is because Patcher is not working as expected, please check the behavior of TransformerManager", e);
        }
    }
    
    // Get the maximum heap size supporting COOPs
    public native long getCompressedOopsMaxHeapSize();
    
    // Arguments
    public native void printHeapSizes();
    
    // Memory
    public native long getObjectAddress0(Object o);
    
    public long getObjectAddress(final Object o) = getObjectAddress0(requireNonNull(o));
    
    public native int getHeapOopSize();
    
    public native int getVMPageSize();
    
    public native long getVMAllocationGranularity();
    
    public native long getVMLargePageSize();
    
    public native long getHeapSpaceAlignment();
    
    public native long getHeapAlignment();
    
    public native boolean isObjectInOldGen0(Object o);
    
    public boolean isObjectInOldGen(final Object o) = isObjectInOldGen0(requireNonNull(o));
    
    public native long getObjectSize0(Object o);
    
    public long getObjectSize(final Object o) = getObjectSize0(requireNonNull(o));
    
    // Runtime
    // Make sure class name is in the correct format
    public int countAliveClasses(final String name) = countAliveClasses0(name.replace('.', '/'));
    
    private native int countAliveClasses0(String name);
    
    public boolean isClassAlive(final String name) = isClassAlive0(name.replace('.', '/'));
    
    public native boolean isClassAlive0(String name);
    
    public native int getSymbolRefcount(String name);
    
    public native boolean deflateIdleMonitors();
    
    public native boolean isMonitorInflated0(Object obj);
    
    public boolean isMonitorInflated(final Object obj) = isMonitorInflated0(requireNonNull(obj));
    
    public native void forceSafepoint();
    
    public native long getConstantPool0(Class<?> aClass);
    
    public long getConstantPool(final Class<?> aClass) = getConstantPool0(requireNonNull(aClass));
    
    public native int getConstantPoolCacheIndexTag0();
    
    public int getConstantPoolCacheIndexTag() = getConstantPoolCacheIndexTag0();
    
    public native int getConstantPoolCacheLength0(Class<?> aClass);
    
    public int getConstantPoolCacheLength(final Class<?> aClass) = getConstantPoolCacheLength0(requireNonNull(aClass));
    
    public native int remapInstructionOperandFromCPCache0(final Class<?> aClass, int index);
    
    public int remapInstructionOperandFromCPCache(final Class<?> aClass, final int index) = remapInstructionOperandFromCPCache0(requireNonNull(aClass), index);
    
    public native int encodeConstantPoolIndyIndex0(int index);
    
    public int encodeConstantPoolIndyIndex(final int index) = encodeConstantPoolIndyIndex0(index);
    
    // JVMTI
    public native void addToBootstrapClassLoaderSearch0(String segment);
    
    public void addToBootstrapClassLoaderSearch(final String segment) = addToBootstrapClassLoaderSearch0(requireNonNull(segment));
    
    public native void addToSystemClassLoaderSearch0(String segment);
    
    public void addToSystemClassLoaderSearch(final String segment) = addToSystemClassLoaderSearch0(requireNonNull(segment));
    
    // G1
    public native boolean g1InConcurrentMark();
    
    public native boolean g1HasRegionsToUncommit();
    
    public native boolean g1IsHumongous0(Object o);
    
    public boolean g1IsHumongous(final Object o) = g1IsHumongous0(requireNonNull(o));
    
    public native boolean g1BelongsToHumongousRegion0(long adr);
    
    public boolean g1BelongsToHumongousRegion(final long adr) {
        if (adr == 0)
            throw new IllegalArgumentException("adr argument should not be null");
        return g1BelongsToHumongousRegion0(adr);
    }
    
    public native boolean g1BelongsToFreeRegion0(long adr);
    
    public boolean g1BelongsToFreeRegion(final long adr) {
        if (adr == 0)
            throw new IllegalArgumentException("adr argument should not be null");
        return g1BelongsToFreeRegion0(adr);
    }
    
    public native long g1NumMaxRegions();
    
    public native long g1NumFreeRegions();
    
    public native int g1RegionSize();
    
    public native MemoryUsage g1AuxiliaryMemoryUsage();
    
    public native int g1ActiveMemoryNodeCount();
    
    public native int[] g1MemoryNodeIds();
    
    public native Object[] parseCommandLine0(String commandline, char delim, DiagnosticCommand... args);
    
    public Object[] parseCommandLine(final String commandline, final char delim, final DiagnosticCommand... args) = parseCommandLine0(commandline, delim, args);
    
    // Parallel GC
    public native long psVirtualSpaceAlignment();
    
    public native long psHeapGenerationAlignment();
    
    /**
     * Enumerates old regions with liveness less than specified and produces some statistics
     * @param liveness percent of region's liveness (live_objects / total_region_size * 100).
     * @return long[3] array where long[0] - total count of old regions
     * long[1] - total memory of old regions
     * long[2] - lowest estimation of total memory of old regions to be freed (non-full regions are not included)
     */
    public native long[] g1GetMixedGCInfo(int liveness);
    
    // NMT
    public native long NMTMalloc(long size);
    
    public native void NMTFree(long mem);
    
    public native long NMTReserveMemory(long size);
    
    public native long NMTAttemptReserveMemoryAt(long addr, long size);
    
    public native void NMTCommitMemory(long addr, long size);
    
    public native void NMTUncommitMemory(long addr, long size);
    
    public native void NMTReleaseMemory(long addr, long size);
    
    public native long NMTMallocWithPseudoStack(long size, int index);
    
    public native long NMTMallocWithPseudoStackAndType(long size, int index, int type);
    
    public native boolean NMTChangeTrackingLevel();
    
    public native int NMTGetHashSize();
    
    public native long NMTNewArena(long initSize);
    
    public native void NMTFreeArena(long arena);
    
    public native void NMTArenaMalloc(long arena, long size);
    
    // Compiler
    public native boolean isC2OrJVMCIIncluded();
    
    public native boolean isJVMCISupportedByGC();
    
    public native int matchesMethod(Executable method, String pattern);
    
    public native int matchesInline(Executable method, String pattern);
    
    public native boolean shouldPrintAssembly(Executable method, int comp_level);
    
    public native int deoptimizeFrames(boolean makeNotEntrant);
    
    public native boolean isFrameDeoptimized(int depth);
    
    public native void deoptimizeAll();
    
    public boolean isMethodCompiled(final Executable method) = isMethodCompiled(method, false /*not osr*/);
    
    public native boolean isMethodCompiled0(Executable method, boolean isOsr);
    
    public boolean isMethodCompiled(final Executable method, final boolean isOsr) = isMethodCompiled0(requireNonNull(method), isOsr);
    
    public boolean isMethodCompilable(final Executable method) = isMethodCompilable(method, -2 /*any*/);
    
    public boolean isMethodCompilable(final Executable method, final int compLevel) = isMethodCompilable(method, compLevel, false /*not osr*/);
    
    public native boolean isMethodCompilable0(Executable method, int compLevel, boolean isOsr);
    
    public boolean isMethodCompilable(final Executable method, final int compLevel, final boolean isOsr) = isMethodCompilable0(requireNonNull(method), compLevel, isOsr);
    
    public native boolean isMethodQueuedForCompilation0(Executable method);
    
    public boolean isMethodQueuedForCompilation(final Executable method) = isMethodQueuedForCompilation0(requireNonNull(method));
    
    // Determine if the compiler corresponding to the compilation level 'compLevel'
    // and to the compilation context 'compilation_context' provides an intrinsic
    // for the method 'method'. An intrinsic is available for method 'method' if:
    //  - the intrinsic is enabled (by using the appropriate command-line flag) and
    //  - the platform on which the VM is running provides the instructions necessary
    //    for the compiler to generate the intrinsic code.
    //
    // The compilation context is related to using the DisableIntrinsic flag on a
    // per-method level, see hotspot/src/share/vm/compiler/abstractCompiler.hpp
    // for more details.
    public boolean isIntrinsicAvailable(final Executable method, final Executable compilationContext, final int compLevel) = isIntrinsicAvailable0(requireNonNull(method), compilationContext, compLevel);
    
    // If usage of the DisableIntrinsic flag is not expected (or the usage can be ignored),
    // use the below method that does not require the compilation context as argument.
    @SuppressWarnings("DataFlowIssue")
    public boolean isIntrinsicAvailable(final Executable method, final int compLevel) = isIntrinsicAvailable(method, null, compLevel);
    
    public native boolean isIntrinsicAvailable0(Executable method, Executable compilationContext, int compLevel);
    
    public int deoptimizeMethod(final Executable method) = deoptimizeMethod(method, false /*not osr*/);
    
    public native int deoptimizeMethod0(Executable method, boolean isOsr);
    
    public int deoptimizeMethod(final Executable method, final boolean isOsr) = deoptimizeMethod0(requireNonNull(method), isOsr);
    
    public void makeMethodNotCompilable(final Executable method) = makeMethodNotCompilable(method, -2 /*any*/);
    
    public void makeMethodNotCompilable(final Executable method, final int compLevel) = makeMethodNotCompilable(method, compLevel, false /*not osr*/);
    
    public native void makeMethodNotCompilable0(Executable method, int compLevel, boolean isOsr);
    
    public void makeMethodNotCompilable(final Executable method, final int compLevel, final boolean isOsr) = makeMethodNotCompilable0(requireNonNull(method), compLevel, isOsr);
    
    public int getMethodCompilationLevel(final Executable method) = getMethodCompilationLevel(method, false /*not ost*/);
    
    public native int getMethodCompilationLevel0(Executable method, boolean isOsr);
    
    public int getMethodCompilationLevel(final Executable method, final boolean isOsr) = getMethodCompilationLevel0(requireNonNull(method), isOsr);
    
    public native boolean testSetDontInlineMethod0(Executable method, boolean value);
    
    public boolean testSetDontInlineMethod(final Executable method, final boolean value) = testSetDontInlineMethod0(requireNonNull(method), value);
    
    public int getCompileQueuesSize() = getCompileQueueSize(-2 /*any*/);
    
    public native int getCompileQueueSize(int compLevel);
    
    public native boolean testSetForceInlineMethod0(Executable method, boolean value);
    
    public boolean testSetForceInlineMethod(final Executable method, final boolean value) = testSetForceInlineMethod0(requireNonNull(method), value);
    
    public boolean enqueueMethodForCompilation(final Executable method, final int compLevel) = enqueueMethodForCompilation(method, compLevel, -1 /*InvocationEntryBci*/);
    
    public native boolean enqueueMethodForCompilation0(Executable method, int compLevel, int entry_bci);
    
    public boolean enqueueMethodForCompilation(final Executable method, final int compLevel, final int entry_bci) = enqueueMethodForCompilation0(requireNonNull(method), compLevel, entry_bci);
    
    public native boolean enqueueInitializerForCompilation0(Class<?> aClass, int compLevel);
    
    public boolean enqueueInitializerForCompilation(final Class<?> aClass, final int compLevel) = enqueueInitializerForCompilation0(requireNonNull(aClass), compLevel);
    
    public native void clearMethodState0(Executable method);
    
    public native void markMethodProfiled(Executable method);
    
    public void clearMethodState(final Executable method) = clearMethodState0(requireNonNull(method));
    
    public native void lockCompilation();
    
    public native void unlockCompilation();
    
    public native int getMethodEntryBci0(Executable method);
    
    public int getMethodEntryBci(final Executable method) = getMethodEntryBci0(requireNonNull(method));
    
    public native Object[] getNMethod0(Executable method, boolean isOsr);
    
    public Object[] getNMethod(final Executable method, final boolean isOsr) = getNMethod0(requireNonNull(method), isOsr);
    
    public native long allocateCodeBlob(int size, int type);
    
    public long allocateCodeBlob(final long size, final int type) {
        final int intSize = (int) size;
        if ((long) intSize != size || size < 0)
            throw new IllegalArgumentException("size argument has illegal value " + size);
        return allocateCodeBlob(intSize, type);
    }
    
    public native void freeCodeBlob(long addr);
    
    public native void forceNMethodSweep();
    
    public native Object[] getCodeHeapEntries(int type);
    
    public native int getCompilationActivityMode();
    
    public native long getMethodData0(Executable method);
    
    public long getMethodData(final Executable method) = getMethodData0(requireNonNull(method));
    
    public native Object[] getCodeBlob(long addr);
    
    public native void clearInlineCaches0(boolean preserve_static_stubs);
    
    public void clearInlineCaches() = clearInlineCaches0(false);
    
    public void clearInlineCaches(final boolean preserve_static_stubs) = clearInlineCaches0(preserve_static_stubs);
    
    // Intered strings
    public native boolean isInStringTable(String str);
    
    // Memory
    public native void readReservedMemory();
    
    public native long allocateMetaspace(ClassLoader classLoader, long size);
    
    public native long incMetaspaceCapacityUntilGC(long increment);
    
    public native long metaspaceCapacityUntilGC();
    
    public native long metaspaceSharedRegionAlignment();
    
    public native boolean metaspaceShouldConcurrentCollect();
    
    public native long metaspaceReserveAlignment();
    
    // Metaspace Arena Tests
    public native long createMetaspaceTestContext(long commit_limit, long reserve_limit);
    
    public native void destroyMetaspaceTestContext(long context);
    
    public native void purgeMetaspaceTestContext(long context);
    
    public native void printMetaspaceTestContext(long context);
    
    public native long getTotalCommittedWordsInMetaspaceTestContext(long context);
    
    public native long getTotalUsedWordsInMetaspaceTestContext(long context);
    
    public native long createArenaInTestContext(long context, boolean is_micro);
    
    public native void destroyMetaspaceTestArena(long arena);
    
    public native long allocateFromMetaspaceTestArena(long arena, long word_size);
    
    public native void deallocateToMetaspaceTestArena(long arena, long p, long word_size);
    
    public native long maxMetaspaceAllocationSize();
    
    // Don't use these methods directly
    // Use GC class instead.
    public native boolean isGCSupported(int name);
    
    public native boolean isGCSupportedByJVMCICompiler(int name);
    
    public native boolean isGCSelected(int name);
    
    public native boolean isGCSelectedErgonomically();
    
    // Force Young GC
    public native void youngGC();
    
    // Force Full GC
    public native void fullGC();
    
    // Returns true if the current GC supports concurrent collection control.
    public native boolean supportsConcurrentGCBreakpoints();
    
    private void checkConcurrentGCBreakpointsSupported() {
        if (!supportsConcurrentGCBreakpoints())
            throw new UnsupportedOperationException("Concurrent GC breakpoints not supported");
    }
    
    private native void concurrentGCAcquireControl0();
    
    private native void concurrentGCReleaseControl0();
    
    private native void concurrentGCRunToIdle0();
    
    private native boolean concurrentGCRunTo0(String breakpoint);
    
    private static boolean concurrentGCIsControlled = false;
    
    private void checkConcurrentGCIsControlled() {
        if (!concurrentGCIsControlled) {
            throw new IllegalStateException("Not controlling concurrent GC");
        }
    }
    
    // TODO breakpoints
    
    // All collectors supporting concurrent GC breakpoints are expected
    // to provide at least the following breakpoints.
    public final String AFTER_MARKING_STARTED    = "AFTER MARKING STARTED";
    public final String BEFORE_MARKING_COMPLETED = "BEFORE MARKING COMPLETED";
    
    // Collectors supporting concurrent GC breakpoints that do reference
    // processing concurrently should provide the following breakpoint.
    public final String AFTER_CONCURRENT_REFERENCE_PROCESSING_STARTED =
            "AFTER CONCURRENT REFERENCE PROCESSING STARTED";
    
    public void concurrentGCAcquireControl() {
        checkConcurrentGCBreakpointsSupported();
        if (concurrentGCIsControlled)
            throw new IllegalStateException("Already controlling concurrent GC");
        concurrentGCAcquireControl0();
        concurrentGCIsControlled = true;
    }
    
    public void concurrentGCReleaseControl() {
        checkConcurrentGCBreakpointsSupported();
        concurrentGCReleaseControl0();
        concurrentGCIsControlled = false;
    }
    
    // Keep concurrent GC idle.  Release from breakpoint.
    public void concurrentGCRunToIdle() {
        checkConcurrentGCBreakpointsSupported();
        checkConcurrentGCIsControlled();
        concurrentGCRunToIdle0();
    }
    
    // Allow concurrent GC to run to breakpoint.
    // Throws IllegalStateException if reached end of cycle first.
    public void concurrentGCRunTo(final String breakpoint) = concurrentGCRunTo(breakpoint, true);
    
    // Allow concurrent GC to run to breakpoint.
    // Returns true if reached breakpoint.  If reached end of cycle first,
    // then throws IllegalStateException if errorIfFail is true, returning
    // false otherwise.
    public boolean concurrentGCRunTo(final String breakpoint, final boolean errorIfFail) {
        checkConcurrentGCBreakpointsSupported();
        checkConcurrentGCIsControlled();
        if (breakpoint == null)
            throw new NullPointerException("null breakpoint");
        else if (concurrentGCRunTo0(breakpoint))
            return true;
        else if (errorIfFail)
            throw new IllegalStateException("Missed requested breakpoint \"" + breakpoint + "\"");
        else
            return false;
    }
    
    // Method tries to start concurrent mark cycle.
    // It returns false if CM Thread is always in concurrent cycle.
    public native boolean g1StartConcMarkCycle();
    
    // Tests on ReservedSpace/VirtualSpace classes
    public native int stressVirtualSpaceResize(long reservedSpaceSize, long magnitude, long iterations);
    
    public native void runMemoryUnitTests();
    
    public native void readFromNoaccessArea();
    
    public native long getThreadStackSize();
    
    public native long getThreadRemainingStackSize();
    
    // CPU features
    public native String getCPUFeatures();
    
    // VM flags
    public native boolean isConstantVMFlag(String name);
    
    public native boolean isLockedVMFlag(String name);
    
    public native void setBooleanVMFlag(String name, boolean value);
    
    public native void setIntVMFlag(String name, long value);
    
    public native void setUintVMFlag(String name, long value);
    
    public native void setIntxVMFlag(String name, long value);
    
    public native void setUintxVMFlag(String name, long value);
    
    public native void setUint64VMFlag(String name, long value);
    
    public native void setSizeTVMFlag(String name, long value);
    
    public native void setStringVMFlag(String name, String value);
    
    public native void setDoubleVMFlag(String name, double value);
    
    public native Boolean getBooleanVMFlag(String name);
    
    public native Long getIntVMFlag(String name);
    
    public native Long getUintVMFlag(String name);
    
    public native Long getIntxVMFlag(String name);
    
    public native Long getUintxVMFlag(String name);
    
    public native Long getUint64VMFlag(String name);
    
    public native Long getSizeTVMFlag(String name);
    
    public native String getStringVMFlag(String name);
    
    public native Double getDoubleVMFlag(String name);
    
    public final List<Function<String, Object>> flagsGetters = List.of(
            this::getBooleanVMFlag, this::getIntVMFlag, this::getUintVMFlag, this::getIntxVMFlag, this::getUintxVMFlag, this::getUint64VMFlag, this::getSizeTVMFlag, this::getStringVMFlag, this::getDoubleVMFlag);
    
    public Object getVMFlag(final String name) = flagsGetters.stream()
            .map(f -> f.apply(name))
            .filter(Objects::nonNull)
            .findAny()
            .orElse(null);
    
    // Jigsaw
    public native void DefineModule(Object module, boolean is_open, String version, String location, Object[] packages);
    
    public native void AddModuleExports(Object from_module, String pkg, Object to_module);
    
    public native void AddReadsModule(Object from_module, Object source_module);
    
    public native void AddModuleExportsToAllUnnamed(Object module, String pkg);
    
    public native void AddModuleExportsToAll(Object module, String pkg);
    
    public native int getOffsetForName0(String name);
    
    public int getOffsetForName(final String name) throws Exception {
        final int offset = getOffsetForName0(name);
        if (offset == -1)
            throw new RuntimeException(name + " not found");
        return offset;
    }
    
    public native Boolean getMethodBooleanOption(Executable method, String name);
    
    public native Long getMethodIntxOption(Executable method, String name);
    
    public native Long getMethodUintxOption(Executable method, String name);
    
    public native Double getMethodDoubleOption(Executable method, String name);
    
    public native String getMethodStringOption(Executable method, String name);
    
    public final List<BiFunction<Executable, String, Object>> methodOptionGetters = List.of(this::getMethodBooleanOption, this::getMethodIntxOption, this::getMethodUintxOption, this::getMethodDoubleOption, this::getMethodStringOption);
    
    public Object getMethodOption(final Executable method, final String name) = methodOptionGetters.stream()
            .map(f -> f.apply(method, name))
            .filter(Objects::nonNull)
            .findAny()
            .orElse(null);
    
    // Sharing & archiving
    public native String getDefaultArchivePath();
    
    public native boolean cdsMemoryMappingFailed();
    
    public native boolean isSharingEnabled();
    
    public native boolean isShared(Object o);
    
    public native boolean isSharedClass(Class<?> c);
    
    public native boolean areSharedStringsIgnored();
    
    public native boolean isSharedInternedString(String s);
    
    public native boolean isCDSIncluded();
    
    public native boolean isJFRIncluded();
    
    public native boolean isDTraceIncluded();
    
    public native boolean isJavaHeapArchiveSupported();
    
    public native Object getResolvedReferences(Class<?> c);
    
    public native void linkClass(Class<?> c);
    
    public native boolean areOpenArchiveHeapObjectsMapped();
    
    // Compiler Directive
    public native int addCompilerDirective(String compDirect);
    
    public native void removeCompilerDirective(int count);
    
    // Handshakes
    public native int handshakeWalkStack(Thread t, boolean all_threads);
    
    public native void asyncHandshakeWalkStack(Thread t);
    
    public native void lockAndBlock(boolean suspender);
    
    // Returns true on linux if library has the noexecstack flag set.
    public native boolean checkLibSpecifiesNoexecstack(String libfilename);
    
    // Container testing
    public native boolean isContainerized();
    
    public native int validateCgroup(String procCgroups, String procSelfCgroup, String procSelfMountinfo);
    
    public native void printOsInfo();
    
    // Decoder
    public native void disableElfSectionCache();
    
    // Resolved Method Table
    public native long resolvedMethodItemsCount();
    
    // Protection Domain Table
    public native int protectionDomainRemovedCount();
    
    // Number of loaded AOT libraries
    public native int aotLibrariesCount();
    
    public native int getKlassMetadataSize(Class<?> c);
    
    // ThreadSMR GC safety check for threadObj
    public native void checkThreadObjOfTerminatingThread(Thread target);
    
    // libc name
    public native String getLibcName();
    
    // Walk stack frames of current thread
    public native void verifyFrames(boolean log, boolean updateRegisterMap);
    
    public native boolean isJVMTIIncluded();
    
    public native void waitUnsafe(int time_ms);
    
    public native void lockCritical();
    
    public native void unlockCritical();
    
}
