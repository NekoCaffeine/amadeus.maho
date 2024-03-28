package amadeus.maho.util.bytecode;

import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Collectors;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.FrameNode;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.util.misc.ConstantLookup;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FrameNodeMergeException extends IllegalArgumentException {
    
    private static final ConstantLookup
            valueTypeLookup = new ConstantLookup().recording(field -> field.getType() == Integer.class, Opcodes.class),
            frameTypeLookup = new ConstantLookup().recording(field -> field.getName().startsWith("F_"), Opcodes.class);
    
    FrameNode a, b;
    
    public FrameNodeMergeException(final String message, final FrameNode a, final FrameNode b) {
        super(STR."""
            \{message}
            a: \{node(a)}
            b: \{node(b)}""");
        this.a = a;
        this.b = b;
    }
    
    public static String node(final FrameNode frame) = STR."""
            type: \{frameTypeLookup.lookupFieldName(frame.type)}
            \tlocal: \{objects(frame.local)}
            \tstack: \{objects(frame.stack)}""";
    
    public static String objects(final List<Object> list)
            = list == null ? "<empty>" : list.stream().map(object -> object == null ? "null" : valueTypeLookup.lookupFields(object).findFirst().map(Field::getName).orElse(object.toString())).collect(Collectors.joining(", ", "[", "]"));
    
}
