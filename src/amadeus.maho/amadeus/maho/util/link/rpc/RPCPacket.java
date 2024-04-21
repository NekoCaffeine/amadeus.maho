package amadeus.maho.util.link.rpc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.stream.Stream;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.BinaryMapping;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.util.serialization.BinaryMapper;
import amadeus.maho.util.serialization.Deserializable;
import amadeus.maho.util.serialization.Serializable;
import amadeus.maho.util.serialization.base.ByteArray;
import amadeus.maho.util.serialization.base.LengthFirstString;

public sealed interface RPCPacket extends BinaryMapper {
    
    @BinaryMapping(value = BinaryMapping.Endian.LITTLE)
    @FieldDefaults(level = AccessLevel.PUBLIC) final class Sync implements RPCPacket {
        
        @BinaryMapping(value = BinaryMapping.Endian.LITTLE)
        @FieldDefaults(level = AccessLevel.PUBLIC)
        public static class Itf {
            
            LengthFirstString name = { };
            
            @BinaryMapping.ForWrite
            int methods = identities.length;
            
            LengthFirstString identities[] = Stream.generate(LengthFirstString::new).limit(methods).toArray(LengthFirstString[]::new);
            
        }
        
        @BinaryMapping.ForWrite
        int count = interfaces.length;
        
        Itf interfaces[] = Stream.generate(Itf::new).limit(count).toArray(Itf[]::new);
        
    }
    
    @BinaryMapping(value = BinaryMapping.Endian.LITTLE)
    @FieldDefaults(level = AccessLevel.PUBLIC) final class Request implements RPCPacket {
        
        int id, interfaceIndex, methodIndex;
        
        ByteArray data = { };
        
    }
    
    @BinaryMapping(value = BinaryMapping.Endian.LITTLE)
    @FieldDefaults(level = AccessLevel.PUBLIC) final class Response implements RPCPacket {
        
        int id, code;
        
        ByteArray data = { };
        
    }
    
    @BinaryMapping(value = BinaryMapping.Endian.LITTLE)
    @FieldDefaults(level = AccessLevel.PUBLIC) final class Close implements RPCPacket {
        
        LengthFirstString message = { };
        
    }
    
    byte
            SYNC     = 0,
            REQUEST  = 1,
            RESPONSE = 2,
            ERROR    = 3,
            CLOSE    = 4;
    
    byte
            ERROR_INVALID_INTERFACE    = 0,
            ERROR_MISSING_INSTANCE     = 1,
            ERROR_INVALID_METHOD       = 2,
            ERROR_INVALID_DATA         = 3,
            ERROR_INVOKE_EXCEPTION     = 4,
            ERROR_SERIALIZATION_RESULT = 5;
    
    static RPCPacket readPacket(final Deserializable.Input input) throws IOException {
        final int type = input.read();
        return (switch (type) {
            case SYNC     -> new Sync();
            case REQUEST  -> new Request();
            case RESPONSE -> new Response();
            case CLOSE    -> new Close();
            default       -> throw new IllegalArgumentException(STR."Invalid packet type: \{type}");
        }).deserialization(input);
    }
    
    static RPCPacket readPacket(final RPCContext context, final InputStream input) throws IOException = readPacket(new Input(input, context.chunkLimit()));
    
    static void writePacket(final Serializable.Output output, final RPCPacket packet) throws IOException {
        output.write(switch (packet) {
            case Sync _     -> SYNC;
            case Request _  -> REQUEST;
            case Response _ -> RESPONSE;
            case Close _    -> CLOSE;
        });
        packet.serialization(output);
    }
    
    static void writePacket(final RPCContext context, final OutputStream output, final RPCPacket packet) throws IOException = writePacket(new Output(output, context.chunkLimit()), packet);
    
}
