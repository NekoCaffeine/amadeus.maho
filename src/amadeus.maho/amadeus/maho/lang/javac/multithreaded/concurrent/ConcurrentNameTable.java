package amadeus.maho.lang.javac.multithreaded.concurrent;

import com.sun.tools.javac.util.Convert;
import com.sun.tools.javac.util.InvalidUtfException;
import com.sun.tools.javac.util.Name;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.RequiredArgsConstructor;

@NoArgsConstructor
public class ConcurrentNameTable extends Name.Table {
    
    @Getter
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class NameImpl extends Name {
        
        int index;
        
        String name;
        
        byte utf[] = name.getBytes(StandardCharsets.UTF_8);
        
        @Override
        public int getIndex() = index;
        
        @Override
        public int getByteLength() = utf.length;
        
        @Override
        public byte getByteAt(final int i) = utf[i];
        
        @Override
        public byte[] getByteArray() = utf;
        
        @Override
        public int getByteOffset() = 0;
        
        @Override
        public String toString() = name;
        
        @Override
        public byte[] toUtf() = utf;
        
    }
    
    AtomicInteger index = { };
    
    ConcurrentHashMap<String, NameImpl> cache = { };
    
    @Override
    public Name fromString(final String string) = cache.computeIfAbsent(string, it -> new NameImpl(this, index.getAndIncrement(), it));
    
    @Override
    public Name fromChars(final char[] cs, final int start, final int len) {
        final String string = { cs, start, len };
        return fromString(string);
    }
    
    @Override
    public Name fromUtf(final byte[] cs, final int start, final int len, final Convert.Validation validation) throws InvalidUtfException {
        if (validation != Convert.Validation.NONE)
            Convert.utfValidate(cs, start, len, validation);
        final String string = { cs, start, len, StandardCharsets.UTF_8 };
        return fromString(string);
    }
    
    @Override
    public void dispose() = cache.clear();
    
}
