package amadeus.maho.util.tuple;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.Cloneable;
import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Setter;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.inspection.Nullable;

@Setter
@Getter
@ToString
@Cloneable
@EqualsAndHashCode
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PUBLIC)
public class Tuple8<T1, T2, T3, T4, T5, T6, T7, T8> implements Tuple {
    
    @Nullable T1 v1;
    @Nullable T2 v2;
    @Nullable T3 v3;
    @Nullable T4 v4;
    @Nullable T5 v5;
    @Nullable T6 v6;
    @Nullable T7 v7;
    @Nullable T8 v8;
    
    @Override
    public Object[] array() = { v1, v2, v3, v4, v5, v6, v7, v8 };
    
}
