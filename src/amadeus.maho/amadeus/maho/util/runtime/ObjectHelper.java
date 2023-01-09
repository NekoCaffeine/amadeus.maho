package amadeus.maho.util.runtime;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import amadeus.maho.lang.inspection.Nullable;

public interface ObjectHelper {
    
    Set<Method> OBJECT_METHODS = Stream.of(Object.class.getDeclaredMethods()).collect(Collectors.toSet());
    
    static Set<Method> objectMethods() = OBJECT_METHODS;
    
    @SuppressWarnings("EqualsReplaceableByObjectsCall")
    static boolean equals(final @Nullable Object a, final @Nullable Object b) = a == b || a != null && a.equals(b);
    
    @SuppressWarnings("EqualsReplaceableByObjectsCall")
    static boolean valueBasedEquals(final @Nullable Object a, final @Nullable Object b) = a == null ? b == null : a.equals(b);
    
    @SuppressWarnings("EqualsReplaceableByObjectsCall")
    static boolean valueBasedNotEquals(final @Nullable Object a, final @Nullable Object b) = a == null ? b != null : !a.equals(b);
    
    static int hashCode(final @Nullable Object o) = o != null ? o.hashCode() : 0;
    
    static int hashCode(final Object... values) = Arrays.hashCode(values);
    
    static <T> int compare(final T a, final T b, final Comparator<? super T> c) = a == b ? 0 : c.compare(a, b);
    
    static String toString(final @Nullable Object object) = object == null ? "<null>" : object.toString();
    
    static <T> T requireNonNull(final @Nullable T obj) {
        if (obj != null)
            return obj;
        throw DebugHelper.breakpointBeforeThrow(new NullPointerException());
    }
    
    static boolean isNull(final @Nullable Object object) = object == null;
    
    static boolean nonNull(final @Nullable Object object) = object != null;
    
}
