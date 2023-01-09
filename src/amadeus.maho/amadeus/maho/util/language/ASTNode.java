package amadeus.maho.util.language;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.ToString;

@ToString
@FieldDefaults(level = AccessLevel.PUBLIC)
public class ASTNode {
    
    int startPos, endPos;
    
}
