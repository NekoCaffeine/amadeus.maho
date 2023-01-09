package amadeus.maho.util.logging;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.function.Consumer;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.vm.transform.mark.HotSpotJIT;

@HotSpotJIT
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WrapperPrintStream extends PrintStream {
    
    Consumer<String> logger;
    Charset charset;
    ThreadLocal<StringBuilder> builder = ThreadLocal.withInitial(StringBuilder::new);
    
    public StringBuilder builder() = builder.get();
    
    public void append(final String string) = builder().append(string);
    
    public String pull() {
        final var builder = builder();
        if (builder.length() == 0)
            return "";
        try { return builder.toString(); } finally { builder.setLength(0); }
    }
    
    public OutputStream source() = out;
    
    @SneakyThrows
    public WrapperPrintStream(final OutputStream out, final Consumer<String> logger, final Charset charset = Charset.defaultCharset()) {
        super(out, true, charset);
        this.charset = charset;
        this.logger = line -> {
            if (out == null)
                throw new IOException("Stream closed");
            logger.accept(pull() + line);
        };
    }
    
    @Override
    public void write(final int b) = builder().append((char) b);
    
    @Override
    public void write(final byte[] buf, final int off, final int len) = builder().append(new String(buf, off, len, charset));
    
    @Override
    public void print(final boolean b) = builder().append(b);
    
    @Override
    public void print(final char c) = builder().append(c);
    
    @Override
    public void print(final int i) = builder().append(i);
    
    @Override
    public void print(final long l) = builder().append(l);
    
    @Override
    public void print(final float f) = builder().append(f);
    
    @Override
    public void print(final double d) = builder().append(d);
    
    @Override
    public void print(final char[] s) = builder().append(s);
    
    @Override
    public void print(final String s) = builder().append(s);
    
    @Override
    public void print(final Object obj) = builder().append(obj);
    
    @Override
    public void println(final boolean b) = logger.accept(String.valueOf(b));
    
    @Override
    public void println(final char c) = logger.accept(String.valueOf(c));
    
    @Override
    public void println(final int i) = logger.accept(String.valueOf(i));
    
    @Override
    public void println(final long l) = logger.accept(String.valueOf(l));
    
    @Override
    public void println(final float f) = logger.accept(String.valueOf(f));
    
    @Override
    public void println(final double d) = logger.accept(String.valueOf(d));
    
    @Override
    public void println(final char[] s) = logger.accept(String.valueOf(s));
    
    @Override
    public void println(final String s) = logger.accept(s);
    
    @Override
    public void println(final Object obj) = logger.accept(String.valueOf(obj));
    
    @Override
    public void println() {
        final String pull = pull();
        if (!pull.isEmpty())
            logger.accept(pull);
    }
    
    @Override
    public void flush() { }
    
}
