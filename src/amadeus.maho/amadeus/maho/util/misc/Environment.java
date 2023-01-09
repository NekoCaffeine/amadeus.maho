package amadeus.maho.util.misc;

import java.lang.management.CompilationMXBean;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.MonitorInfo;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.sun.management.GarbageCollectorMXBean;
import com.sun.management.GcInfo;
import com.sun.management.OperatingSystemMXBean;
import com.sun.management.ThreadMXBean;
import com.sun.management.UnixOperatingSystemMXBean;

import amadeus.maho.lang.Getter;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.vm.JDWP;

@FunctionalInterface
public interface Environment {

    @Getter
    Environment local = System::getProperties;

    Map<Object, Object> mapping();

    default @Nullable String lookup(final String key) {
        @Nullable String result = (String) mapping().get(key);
        if (result == null) {
            result = System.getenv(key);
            if (result != null)
                value(key, result);
        }
        return result;
    }

    default String lookup(final String key, final String defaultValue) {
        @Nullable String result = (String) mapping().get(key);
        if (result == null) {
            result = System.getenv(key);
            if (result != null)
                value(key, result);
            else
                value(key, result = defaultValue);
        }
        return result;
    }

    default boolean lookup(final String key, final boolean defaultValue) = Boolean.parseBoolean(lookup(key, String.valueOf(defaultValue)));

    default int lookup(final String key, final int defaultValue) = Integer.parseInt(lookup(key, String.valueOf(defaultValue)));

    default long lookup(final String key, final long defaultValue) = Long.parseLong(lookup(key, String.valueOf(defaultValue)));

    default float lookup(final String key, final float defaultValue) = Float.parseFloat(lookup(key, String.valueOf(defaultValue)));

    default void value(final String key, final Object value) = mapping().put(key, value.toString());

    default void enable(final String key) = value(key, Boolean.TRUE);

    default void disable(final String key) = value(key, Boolean.FALSE);

    static boolean assertState() {
        boolean state = false;
        // noinspection AssertWithSideEffects
        assert state = true;
        return state;
    }

    String UNSUPPORTED = "<Unsupported>", UNKNOWN = "<Unknown>";

    default void dump(final List<String> list, final String subHead) {
        final String subHead2 = subHead + subHead, subHead3 = subHead2 + subHead, subHead4 = subHead3 + subHead;
        final OperatingSystemMXBean os = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        final RuntimeMXBean rt = ManagementFactory.getRuntimeMXBean();
        final CompilationMXBean jitc = ManagementFactory.getCompilationMXBean();
        final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        final MemoryUsage heapUsage = memory.getHeapMemoryUsage(), directUsage = memory.getNonHeapMemoryUsage();
        final List<MemoryPoolMXBean> memoryPools = ManagementFactory.getMemoryPoolMXBeans();
        final ThreadMXBean thread = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        final ThreadInfo threadInfos[] = thread.dumpAllThreads(true, true);
        final List<GarbageCollectorMXBean> gcs = ManagementFactory.getPlatformMXBeans(GarbageCollectorMXBean.class);
        list += "Java Runtime VersionInfo: %s".formatted(Runtime.version());
        list += "Operating System: %s(%s) %s".formatted(os.getName(), os.getVersion(), os.getArch());
        list += "Available Processors: %d".formatted(os.getAvailableProcessors());
        final long usedMemory = os.getTotalMemorySize() - os.getFreeMemorySize();
        list += "Memory: %s / %s (%s)".formatted(memoryString(usedMemory), memoryString(os.getTotalMemorySize()), usedMemory < 0L || os.getTotalMemorySize() < 1L ? UNKNOWN :
                "%.2f%%".formatted(BigDecimal.valueOf(usedMemory)
                        .divide(BigDecimal.valueOf(os.getTotalMemorySize()), MathContext.DECIMAL32)
                        .multiply(BigDecimal.valueOf(100L)).doubleValue()));
        list += "Committed Virtual Memory: %s".formatted(memoryString(os.getCommittedVirtualMemorySize()));
        final long usedSwap = os.getTotalSwapSpaceSize() - os.getFreeSwapSpaceSize();
        list += "Swap Space: %s / %s (%s)".formatted(memoryString(usedSwap), memoryString(os.getTotalSwapSpaceSize()), usedSwap < 0L || os.getTotalSwapSpaceSize() < 1L ? UNKNOWN :
                "%.2f%%".formatted(BigDecimal.valueOf(usedSwap)
                        .divide(BigDecimal.valueOf(os.getTotalSwapSpaceSize()), MathContext.DECIMAL32)
                        .multiply(BigDecimal.valueOf(100L)).doubleValue()));
        if (os instanceof UnixOperatingSystemMXBean unix)
            list += "Unix Open File Descriptor Count: %d/%d".formatted(unix.getOpenFileDescriptorCount(), unix.getMaxFileDescriptorCount());
        list += "Management Specification VersionInfo: %s".formatted(rt.getManagementSpecVersion());
        list += "Java Specification: %s(%s) %s".formatted(rt.getSpecName(), rt.getSpecVersion(), rt.getSpecVendor());
        list += "Java Virtual Machine: %s(%s) %s".formatted(rt.getVmName(), rt.getVmVersion(), rt.getVmVendor());
        list += "JIT Compiler: %s".formatted(jitc.getName());
        list += "Assert State: %s".formatted(booleanString(assertState()));
        list += "JDWP Debugger State: %s".formatted(booleanString(JDWP.isJDWPEnable()));
        list += "Heap Memory Usage:";
        dump(list, subHead, heapUsage);
        list += "Non-heap Memory Usage:";
        dump(list, subHead, directUsage);
        list += "Memory Usage:";
        memoryPools.forEach(memoryPool -> {
            list += "%s%s (%s) [Valid: %s, UsageThreshold: %s, CollectionUsageThreshold: %s]".formatted(subHead, memoryPool.getName(), memoryPool.getType(), memoryPool.isValid(), memoryPool.isUsageThresholdSupported() ?
                    "%s(%d)".formatted(memoryPool.isUsageThresholdExceeded(), memoryPool.getUsageThresholdCount()) : UNSUPPORTED, memoryPool.isCollectionUsageThresholdSupported() ?
                    "%s(%d)".formatted(memoryPool.isCollectionUsageThresholdExceeded(), memoryPool.getCollectionUsageThresholdCount()) : UNSUPPORTED);
            list += "%sMemoryManagerNames: %s".formatted(subHead2, Arrays.toString(memoryPool.getMemoryManagerNames()));
            list += "%s%s".formatted(subHead2, "Usage:");
            dump(list, subHead3, memoryPool.getUsage());
            list += "%s%s".formatted(subHead2, "PeakUsage:");
            dump(list, subHead3, memoryPool.getPeakUsage());
            list += "%s%s".formatted(subHead2, "CollectionUsage:");
            dump(list, subHead3, memoryPool.getCollectionUsage());
        });
        list += "GC Info:";
        System.gc();
        gcs.forEach(gc -> {
            final @Nullable GcInfo gcInfo = gc.getLastGcInfo();
            if (gcInfo != null) {
                final Map<String, MemoryUsage> before = gcInfo.getMemoryUsageBeforeGc(), after = gcInfo.getMemoryUsageAfterGc();
                list += "%s%s [Valid: %s, Duration: %dms]".formatted(subHead, gc.getName(), gc.isValid(), gcInfo.getDuration());
                before.forEach((name, beforeUsage) -> {
                    if (beforeUsage.getMax() != 0) {
                        final MemoryUsage afterUsage = after.get(name);
                        list += "%s%s:".formatted(subHead2, name);
                        if (beforeUsage.getUsed() != afterUsage.getUsed() ||
                                beforeUsage.getCommitted() != afterUsage.getCommitted() ||
                                beforeUsage.getMax() != afterUsage.getMax()) {
                            list += "%sBefore:".formatted(subHead3);
                            dump(list, subHead4, afterUsage);
                            list += "%sAfter:".formatted(subHead3);
                        } else
                            list += "%sUnchanged:".formatted(subHead3);
                        dump(list, subHead4, beforeUsage);
                    }
                });
            } else
                list += "%s%s [Valid: %s, Duration: %s]".formatted(subHead, gc.getName(), gc.isValid(), UNKNOWN);
        });
        list += "Thread Info:";
        Stream.of(threadInfos).forEach(threadInfo -> {
            final StringBuilder builder = { 1 << 8 };
            builder.append(threadInfo.getThreadName());
            if (threadInfo.isDaemon())
                builder.append(" (daemon)");
            builder.append(" [prio: %d, id: %d] %s".formatted(threadInfo.getPriority(), threadInfo.getThreadId(), threadInfo.getThreadState()));
            if (threadInfo.getLockName() != null)
                builder.append(" on ").append(threadInfo.getLockName());
            if (threadInfo.getLockOwnerName() != null)
                builder.append(" owned by \"").append(threadInfo.getLockOwnerName()).append("\" id: ").append(threadInfo.getLockOwnerId());
            if (threadInfo.isSuspended())
                builder.append(" (suspended)");
            if (threadInfo.isInNative())
                builder.append(" (in native)");
            list += "%s%s".formatted(subHead, builder);
            final StackTraceElement stackTrace[] = threadInfo.getStackTrace();
            final int MAX_FRAMES = 64;
            int i = 0;
            for (; i < stackTrace.length && i < MAX_FRAMES; i++) {
                final StackTraceElement element = stackTrace[i];
                list += "%sat %s".formatted(subHead2, element);
                if (i == 0 && threadInfo.getLockInfo() != null) {
                    switch (threadInfo.getThreadState()) {
                        case BLOCKED                -> list += "%s-  blocked on %s".formatted(subHead2, threadInfo.getLockInfo());
                        case WAITING, TIMED_WAITING -> list += "%s-  waiting on %s".formatted(subHead2, threadInfo.getLockInfo());
                    }
                }
                for (final MonitorInfo monitorInfo : threadInfo.getLockedMonitors())
                    if (monitorInfo.getLockedStackDepth() == i)
                        list += "%s-  locked on %s".formatted(subHead2, monitorInfo);
            }
            if (i < stackTrace.length)
                list += "%s...".formatted(subHead2);
            final LockInfo[] locks = threadInfo.getLockedSynchronizers();
            if (locks.length > 0) {
                list += subHead2;
                list += "%sLocked synchronizers count: %d".formatted(subHead2, locks.length);
                for (final LockInfo lockInfo : locks)
                    list += "%s- %s".formatted(subHead3, lockInfo);
            }
        });
        list += "JVM Args:";
        dump(list, subHead, rt.getInputArguments());
        list += "BootClassPath:";
        dump(list, subHead, rt.isBootClassPathSupported() ? rt.getBootClassPath() : UNSUPPORTED);
        list += "ClassPath:";
        dump(list, subHead, rt.getClassPath());
        list += "LibraryPath:";
        dump(list, subHead, rt.getLibraryPath());
        list += "SystemProperties:";
        final Map<String, String> systemProperties = rt.getSystemProperties();
        systemProperties.remove("java.class.path");
        systemProperties.remove("java.library.path");
        systemProperties.compute("line.separator", (key, line) -> (line ?? "\n")
                .replace("\r", "\\r")
                .replace("\n", "\\n"));
        dump(list, subHead, systemProperties.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e, $) -> e, LinkedHashMap::new)));
    }

    private static void dump(final List<String> list, final String subHead, final @Nullable Object object) {
        switch (object) {
            case null                     -> list += "%s%s".formatted(subHead, UNSUPPORTED);
            case String[] array           -> Stream.of(array)
                    .map(element -> subHead + element)
                    .forEach(list::add);
            case Collection<?> collection -> collection.stream()
                    .map(element -> subHead + element)
                    .forEach(list::add);
            case Map<?, ?> map            -> map.entrySet().stream()
                    .map(entry -> subHead + entry.getKey() + " = " + entry.getValue())
                    .forEach(list::add);
            case CharSequence c           -> Stream.of(c.toString().split(";"))
                    .map(element -> subHead + element)
                    .forEach(list::add);
            case MemoryUsage memoryUsage  -> list += "%s%s / %s (%s), init: %s, committed: %s".formatted(subHead, memoryString(memoryUsage.getUsed()), memoryString(memoryUsage.getMax()),
                    memoryUsage.getUsed() < 0L || memoryUsage.getMax() < 1L ? UNKNOWN :
                            "%.2f%%".formatted(BigDecimal.valueOf(memoryUsage.getUsed())
                                    .divide(BigDecimal.valueOf(memoryUsage.getMax()), MathContext.DECIMAL32)
                                    .multiply(BigDecimal.valueOf(100L)).doubleValue()), memoryString(memoryUsage.getInit()), memoryString(memoryUsage.getCommitted()));
            default                       -> throw new IllegalStateException("Unexpected value: " + object);
        }
    }

    int STORAGE_DECIMAL = 10, MASK = (1 << STORAGE_DECIMAL) - 1;
    String STORAGE_UNIT[] = { "B", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "BB" };

    static String memoryString(final long size) {
        if (size < 0L)
            return UNKNOWN;
        int layer = 0, surplus = 0;
        long top = size;
        while (top > 1 << STORAGE_DECIMAL) {
            surplus = (int) (top & MASK);
            top >>= STORAGE_DECIMAL;
            layer++;
        }
        return "%d %s (%.3f %s)".formatted(size, STORAGE_UNIT[0], top + surplus / (float) (1 << STORAGE_DECIMAL), STORAGE_UNIT[layer]);
    }

    static String booleanString(final boolean value) = value ? "Enable" : "Disable";

}
