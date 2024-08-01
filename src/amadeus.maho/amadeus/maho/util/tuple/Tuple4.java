package amadeus.maho.util.tuple;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.Cloneable;
import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.ToString;

@Setter
@Getter
@ToString
@Cloneable
@EqualsAndHashCode
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PUBLIC)
public class Tuple4<T1, T2, T3, T4> implements Tuple {
    
    T1 v1;
    T2 v2;
    T3 v3;
    T4 v4;
    
    @Override
    public Object[] array() = { v1, v2, v3, v4 };
    
}
