package amadeus.maho.vm.tools.hotspot.code;

import java.util.stream.Stream;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.vm.tools.hotspot.WhiteBox;

@ToString
@EqualsAndHashCode
@FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
public class CodeBlob {
    
    private static final WhiteBox WB = WhiteBox.instance();
    
    public static @Nullable CodeBlob[] getCodeBlobs(final BlobType type) {
        final @Nullable Object entries[] = WB.getCodeHeapEntries(type.id);
        return entries == null ? null : Stream.of(entries).map(Object[].class::cast).map(CodeBlob::new).toArray(CodeBlob[]::new);
    }
    
    public static @Nullable CodeBlob getCodeBlob(final long addr) {
        final @Nullable Object[] obj = WB.getCodeBlob(addr);
        return obj == null ? null : new CodeBlob(obj);
    }
    
    protected CodeBlob(final Object obj[]) {
        assert obj.length == 4;
        name = (String) obj[0];
        size = (Integer) obj[1];
        code_blob_type = BlobType.values()[(Integer) obj[2]];
        assert code_blob_type.id == (Integer) obj[2];
        address = (Long) obj[3];
    }
    
    String   name;
    int      size;
    BlobType code_blob_type;
    long     address;
    
}
