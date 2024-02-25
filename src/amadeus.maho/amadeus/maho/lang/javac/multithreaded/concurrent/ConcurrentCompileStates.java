package amadeus.maho.lang.javac.multithreaded.concurrent;

import java.util.concurrent.ConcurrentHashMap;

import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.CompileStates;
import com.sun.tools.javac.comp.Env;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;

@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConcurrentCompileStates extends CompileStates {
    
    ConcurrentHashMap<Env<AttrContext>, CompileStates.CompileState> record = { };
    
    @Override
    public CompileState get(final Object key) = record.get(key);
    
    @Override
    public CompileState put(final Env<AttrContext> key, final CompileState value) = record.put(key, value);
    
}
