package amadeus.maho.vm.reflection.hotspot;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Stream;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.runtime.ObjectHelper;

public class HotSpotType {
    
    private static final HotSpotField NO_FIELDS[] = new HotSpotField[0];
    
    public final String name;
    public final String superName;
    public final int size;
    public final boolean isOop;
    public final boolean isInt;
    public final boolean isUnsigned;
    public final HotSpotField fields[];
    
    public HotSpotType(final String name, final @Nullable String superName, final int size, final boolean isOop,
            final boolean isInt, final boolean isUnsigned, final Set<HotSpotField> fields) {
        this.name = name;
        this.superName = superName;
        this.size = size;
        this.isOop = isOop;
        this.isInt = isInt;
        this.isUnsigned = isUnsigned;
        this.fields = fields == null ? NO_FIELDS : fields.toArray(HotSpotField[]::new);
    }
    
    public HotSpotField field(final String name) {
        for (final HotSpotField field : fields) {
            if (field.name.equals(name))
                return field;
        }
        throw new NoSuchElementException("No such field: " + name);
    }
    
    public long global(final String name) {
        final HotSpotField field = field(name);
        if (field.isStatic)
            return field.offset;
        throw new IllegalArgumentException("Static field expected");
    }
    
    public long offset(final String name) {
        final HotSpotField field = field(name);
        if (!field.isStatic)
            return field.offset;
        throw new IllegalArgumentException("Instance field expected");
    }
    
    @Override
    public String toString() {
        final StringBuilder builder = { name };
        if (superName != null)
            builder.append(" extends ").append(superName);
        builder.append(" @ ").append(size);
        for (final HotSpotField field : fields)
            builder.append('\n').append("  ").append(field);
        return builder.toString();
    }
    
    public void dump(final List<String> list, final String subHead) {
        final String subHead2 = subHead + subHead, subHead3 = subHead2 + subHead;
        list += "%s%s%s @ %d".formatted(subHead2, name, superName != null ? " : " + superName : "", size);
        Stream.of(fields)
                .map(ObjectHelper::toString)
                .map(subHead3::concat)
                .forEach(list::add);
    }
    
}
