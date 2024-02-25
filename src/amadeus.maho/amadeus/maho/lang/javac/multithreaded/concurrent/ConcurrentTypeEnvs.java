package amadeus.maho.lang.javac.multithreaded.concurrent;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.TypeEnvs;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;

@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConcurrentTypeEnvs extends TypeEnvs {
    
    ConcurrentHashMap<Symbol.TypeSymbol, Env<AttrContext>> map = { };
    
    @Override
    public @Nullable Env<AttrContext> get(final @Nullable Symbol.TypeSymbol sym) = sym == null ? null : map.get(sym);
    
    @Override
    public Env<AttrContext> put(final Symbol.TypeSymbol sym, final Env<AttrContext> env) = map.put(sym, env);
    
    @Override
    public @Nullable Env<AttrContext> remove(final @Nullable Symbol.TypeSymbol sym) = sym == null ? null : map.remove(sym);
    
    @Override
    public Collection<Env<AttrContext>> values() = map.values();
    
    @Override
    public void clear() = map.clear();
    
}
