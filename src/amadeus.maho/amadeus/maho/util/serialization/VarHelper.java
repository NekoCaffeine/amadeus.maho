package amadeus.maho.util.serialization;

public interface VarHelper {
    
    static int varIntSize(int i) {
        int result = 0;
        do {
            result++;
            i >>>= 7;
        } while (i != 0);
        return result;
    }
    
    static int varLongSize(long v) {
        int result = 0;
        do {
            result++;
            v >>>= 7;
        } while (v != 0);
        return result;
    }
    
}
