package amadeus.maho.util.reference;

import java.util.ArrayList;
import java.util.List;

public interface Overwritable<T> extends Readable<T> {
    
    interface Byte extends Readable.Byte {
        
        List<Overwriter.Byte> overwriters();
        
    }
    
    interface Short extends Readable.Short {
        
        List<Overwriter.Short> overwriters();
        
    }
    
    interface Int extends Readable.Int {
        
        List<Overwriter.Int> overwriters();
        
    }
    
    interface Long extends Readable.Long {
        
        List<Overwriter.Long> overwriters();
        
    }
    
    interface Float extends Readable.Float {
        
        List<Overwriter.Float> overwriters();
        
    }
    
    interface Double extends Readable.Double {
        
        List<Overwriter.Double> overwriters();
        
    }
    
    interface Boolean extends Readable.Boolean {
        
        List<Overwriter.Boolean> overwriters();
        
    }
    
    interface Char extends Readable.Char {
        
        List<Overwriter.Char> overwriters();
        
    }
    
    interface Template {
        
        List<Overwriter.$Any> overwriters();
        
        default Any overwriteValue(Any value) {
            final var overwriters = new ArrayList<>(overwriters());
            return switch (overwriters.size()) {
                case 0 -> value;
                case 1 -> overwriters.get(0).overwrite(value);
                default -> {
                    int replaceIndex = 0;
                    for (int i = overwriters.size() - 1; i > 0; i--)
                        if (overwriters.get(i) instanceof Overwriter.Replace) {
                            replaceIndex = i;
                            break;
                        }
                    for (final int len = overwriters.size(); replaceIndex < len; replaceIndex++)
                        value = overwriters.get(replaceIndex).overwrite(value);
                    yield value;
                }
            };
        }
        
    }
    
    List<Overwriter<T>> overwriters();
    
}
