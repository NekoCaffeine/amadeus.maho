package amadeus.maho.util.dynamic;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.Unsupported;

public sealed interface DynamicObject {
    
    @Getter
    @Unsupported
    @EqualsAndHashCode
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    final class MapUnit implements DynamicObject {
        
        @Default
        Map<String, DynamicObject> asMap = new LinkedHashMap<>();
        
        @Override
        public DynamicObject GET(final String key) = asMap[key];
        
        @Override
        public String toString() = asMap.toString();
        
    }
    
    @Getter
    @Unsupported
    @EqualsAndHashCode
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    final class ArrayUnit implements DynamicObject {
        
        @Default
        List<DynamicObject> asList = new ArrayList<>();
        
        @Override
        public DynamicObject GET(final int index) = asList[index];
        
        @Override
        public String toString() = asList.toString();
        
    }
    
    @Getter
    @Unsupported
    @EqualsAndHashCode
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    final class StringUnit implements DynamicObject {
        
        String asString;
        
        @Override
        public String toString() = asString;
        
    }
    
    @Getter
    @Unsupported
    @EqualsAndHashCode
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    final class DecimalUnit implements DynamicObject {
        
        BigDecimal asDecimal;
        
        @Override
        public String asString() = asDecimal.toString();
        
        @Override
        public int asInt() = asDecimal.intValue();
        
        @Override
        public long asLong() = asDecimal.longValue();
        
        @Override
        public float asFloat() = asDecimal.floatValue();
        
        @Override
        public double asDouble() = asDecimal.doubleValue();
        
        @Override
        public String toString() = asDecimal.toString();
        
    }
    
    @Getter
    @Unsupported
    @EqualsAndHashCode
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    final class BooleanUnit implements DynamicObject {
        
        boolean asBoolean;
        
        @Override
        public String toString() = Boolean.toString(asBoolean);
        
    }
    
    @Getter
    @ToString
    @Unsupported
    @NoArgsConstructor(AccessLevel.PRIVATE)
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    final class NullUnit implements DynamicObject {
        
        public static final NullUnit instance = { };
        
        @Override
        public boolean isNull() = true;
        
        @Override
        public String toString() = "null";
        
    }
    
    non-sealed interface Export extends DynamicObject { }
    
    DynamicObject GET(int index);
    
    DynamicObject GET(String key);
    
    List<DynamicObject> asList();
    
    Map<String, DynamicObject> asMap();
    
    String asString();
    
    default BigDecimal asDecimal() = { asString() };
    
    default Number asNumber() = asDecimal();
    
    default int asInt() = Integer.parseInt(asString());
    
    default long asLong() = Long.parseLong(asString());
    
    default float asFloat() = Float.parseFloat(asString());
    
    default double asDouble() = Double.parseDouble(asString());
    
    boolean asBoolean();
    
    boolean isNull();
    
}
