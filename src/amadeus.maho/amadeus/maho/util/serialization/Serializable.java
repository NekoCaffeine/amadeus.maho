package amadeus.maho.util.serialization;

import java.io.IOException;
import java.io.OutputStream;

import amadeus.maho.lang.Default;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;

public interface Serializable {
    
    @Getter
    @RequiredArgsConstructor
    class Output extends OutputStream implements BinaryMapper.OffsetMark {
    
        private final OutputStream output;
        
        @Default
        protected long offset = 0;
        
        @Override
        public void write(final int b) throws IOException {
            output.write(b);
            offset = offset + 1;
        }
        
        @Override
        public void write(final byte buffer[], final int offset, final int length) throws IOException {
            output().write(buffer, offset, length);
            this.offset = this.offset + length;
        }
        
        @Override
        public void flush() throws IOException = output().flush();
        
        @Override
        public void close() throws IOException {
            final OutputStream output = output();
            @Nullable Throwable flushThrowable = null;
            try {
                output.flush();
            } catch (final Throwable throwable) {
                flushThrowable = throwable;
                throw throwable;
            } finally {
                try {
                    output.close();
                } catch (final Throwable closeThrowable) {
                    if (flushThrowable instanceof ThreadDeath death && !(closeThrowable instanceof ThreadDeath)) {
                        death.addSuppressed(closeThrowable);
                        throw death;
                    }
                    if (flushThrowable != closeThrowable)
                        closeThrowable.addSuppressed(flushThrowable);
                    throw closeThrowable;
                }
            }
        }
        
    }
    
    self serialization(Output output) throws IOException;
    
}
