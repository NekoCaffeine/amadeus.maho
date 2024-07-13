package amadeus.maho.util.serialization.base;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import amadeus.maho.lang.NoArgsConstructor;

@NoArgsConstructor
public class TrustedByteArrayOutputStream extends ByteArrayOutputStream implements ByteBufferHandler {
    
    public byte[] array() = buf;
    
    public int count() = count;
    
    public ByteBuffer toByteBuffer() = ByteBuffer.wrap(buf, 0, count);
    
    public ByteArrayInputStream toInputStream() = { buf, 0, count };
    
    @Override
    public void handle(final byte[] buffer, final int offset, final int length) = write(buffer, offset, length);
    
}
