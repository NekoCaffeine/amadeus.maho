package amadeus.maho.util.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import amadeus.maho.lang.Extension;
import amadeus.maho.util.math.MathHelper;

@Extension
public interface ChecksumHelper {
    
    char hex[] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };
    
    static String hex(final MessageDigest digest) {
        final StringBuilder builder = { 1 << 5 };
        for (final byte b : digest.digest())
            builder.append(hex[(b & 0xF0) >> 4]).append(hex[b & 0x0F]);
        return builder.toString();
    }
    
    static String checksum(final InputStream input, final String algorithm, final int bufferSize = 1 << 16) throws NoSuchAlgorithmException, IOException {
        final var complete = MessageDigest.getInstance(algorithm);
        final var buffer = new byte[bufferSize];
        int nRead;
        while ((nRead = input.read(buffer)) != -1)
            complete.update(buffer, 0, nRead);
        return complete.hex();
    }
    
    static String checksum(final Path path, final String algorithm, final int bufferSize = 1 << 16) throws NoSuchAlgorithmException, IOException {
        try (final var channel = Files.newByteChannel(path)) {
            if (channel instanceof FileChannel fileChannel) {
                try {
                    final var complete = MessageDigest.getInstance(algorithm);
                    long pos = 0L;
                    final long size = channel.size();
                    while (pos < size) {
                        final long min = MathHelper.min(size - pos, 1 << 30);
                        complete.update(fileChannel.map(FileChannel.MapMode.READ_ONLY, pos, min));
                        pos += min;
                    }
                    return complete.hex();
                } catch (final OutOfMemoryError e) {
                    System.gc();
                    return checksum(channel, algorithm, bufferSize);
                }
            } else
                return checksum(channel, algorithm, bufferSize);
        }
    }
    
    static String checksum(final SeekableByteChannel channel, final String algorithm, final int bufferSize) throws NoSuchAlgorithmException, IOException {
        final var complete = MessageDigest.getInstance(algorithm);
        final var buffer = ByteBuffer.allocate(bufferSize);
        while (channel.read(buffer.clear()) != -1)
            complete.update(buffer.flip());
        return complete.hex();
    }
    
    static String checksum(final ByteBuffer buffer, final String algorithm) throws NoSuchAlgorithmException, IOException {
        final var complete = MessageDigest.getInstance(algorithm);
        complete.update(buffer);
        return complete.hex();
    }
    
}
