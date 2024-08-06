package amadeus.maho.lang.javac.handler.base;

import java.util.Set;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.util.Context;

import amadeus.maho.lang.Extension;
import amadeus.maho.lang.javac.JavacContext;
import amadeus.maho.util.concurrent.ConcurrentWeakIdentityHashMap;

public record MethodIdentitiesCache(ConcurrentWeakIdentityHashMap<Symbol.MethodSymbol, String> identitiesCache = { }) {
    
    public static final Set<String> objectMethodIdentities = Set.of("getClass()", "equals(java.lang.Object)", "hashCode()", "toString()", "clone()", "notify()", "notifyAll()", "wait()", "wait(long)", "wait(long,int)");
    
    public static MethodIdentitiesCache instance(final Context context) = context.get(MethodIdentitiesCache.class) ?? new MethodIdentitiesCache().let(it -> context.put(MethodIdentitiesCache.class, it));
    
    @Extension.Operator("GET")
    public String identity(final Symbol.MethodSymbol symbol) = identitiesCache().computeIfAbsent(symbol, JavacContext::methodIdentity);
    
    public boolean isSameMethod(final Symbol.MethodSymbol a, final Symbol.MethodSymbol b) = identity(a).equals(identity(b));
    
    public boolean isObjectMethod(final Symbol.MethodSymbol symbol) = objectMethodIdentities[identity(symbol)];
    
}
