package amadeus.maho.util.container;

import java.util.LinkedList;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.inspection.Nullable;

@Setter
@Getter
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PROTECTED)
public class Node<T> {
    
    @Default
    final @Nullable Node<T> parent = null;
    
    T value;
    
    final LinkedList<Node<T>> nodes = { };
    
    public Node<T> sub(final T value) = { this, value };
    
    public int depth() {
        int result = 0;
        @Nullable self self = parent();
        while (self != null) {
            result++;
            self = self.parent;
        }
        return result;
    }
    
}
