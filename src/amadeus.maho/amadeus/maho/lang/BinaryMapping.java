package amadeus.maho.lang;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface BinaryMapping {
    
    // The fields marked by this annotation will be checked for consistency during deserialization and an illegalArgumentException will be thrown if they fail.
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Constant { }
    
    // Use this annotation to override the default Endian when a field does not match the default Endian.
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Mark {
        
        Endian value();
        
    }
    
    // Use this annotation when the field is unsigned and is not unsigned by default.
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Unsigned { }
    
    // The fields marked by this annotation will cause init expressions to be generate in the deserialization code. Will NOT act with @Constant
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface ForWrite { }
    
    // Use the `transient` modifier if you need to skip both reads and writes.
    
    // The fields marked by this annotation will only generate serialization code.
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface SkipRead { }
    
    // The fields marked by this annotation will only generate deserialization code.
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface SkipWrite { }
    
    // The first byte of the field marked by this annotation will be checked for EOF and will be returned if it reaches the end of the stream.
    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Optional { }
    
    enum Endian {BIG, LITTLE}
    
    // Default Endian.
    Endian value();
    
    // Using a larger type to represent an unsigned number of a smaller type, the actual serialization will write the number of bytes of the smaller type.
    // e.g. `long` to represent `unsigned int`
    boolean unsigned() default false;
    
    // inject field: transient boolean eofMark;
    // if (first byte eof) { eof = true; return; }
    boolean eofMark() default false;
    
    // inject field: transient long offsetMark;
    // read(); offsetMark++;
    boolean offsetMark() default false;
    
    // Metadata for deserialization only; if true, no serialisation-related methods will be generated.
    boolean metadata() default false;
    
    // If the parent class is the implementation of BinaryMapper, the parent class implementation is called first in read/write.
    boolean callSuper() default true;
    
}
