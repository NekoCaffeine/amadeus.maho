package amadeus.maho.util.bytecode.traverser.exception;

import org.objectweb.asm.Type;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.util.bytecode.traverser.Frame;
import amadeus.maho.util.bytecode.traverser.TypeOwner;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ComputeException extends RuntimeException {
    
    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class Load extends ComputeException {
        
        Frame frame;
        int   local;
        
        public Load(final Frame frame, final int local) {
            super("at: " + local + ", in: " + frame.locals(), frame);
            this.frame = frame;
            this.local = local;
        }
        
    }
    
    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class TypeMismatch extends ComputeException {
        
        Type      expected;
        TypeOwner owner;
        
        public TypeMismatch(final Frame frame, final Type expected, final TypeOwner owner) {
            super("expected: " + expected + ", actual: " + owner.type(), frame);
            this.expected = expected;
            this.owner = owner;
        }
        
    }
    
    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class ArrayTypeMismatch extends ComputeException {
        
        TypeOwner owner;
        
        public ArrayTypeMismatch(final Frame frame, final TypeOwner owner) {
            super("expected: <any array>" + ", actual: " + owner.type(), frame);
            this.owner = owner;
        }
        
    }
    
    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class Pop extends ComputeException {
        
        public Pop(final Frame frame) = super("pop empty stack", frame);
        
    }
    
    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class Fetch extends ComputeException {
        
        int offset;
        
        public Fetch(final Frame frame, final int offset) {
            super("fetch offset: " + offset, frame);
            this.offset = offset;
        }
        
    }
    
    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class SizeMismatch extends ComputeException {
        
        int       expected;
        TypeOwner owner;
        
        public SizeMismatch(final Frame frame, final int expected, final TypeOwner owner) {
            super("expected: " + expected + ", actual: " + owner.type(), frame);
            this.expected = expected;
            this.owner = owner;
        }
        
    }
    
    @Getter
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class SizeRangeMismatch extends ComputeException {
        
        int min, max;
        TypeOwner owner;
        
        public SizeRangeMismatch(final Frame frame, final int min, final int max, final TypeOwner owner) {
            super("min: " + min + ", max: " + max + ", actual: " + owner.type(), frame);
            this.min = min;
            this.max = max;
            this.owner = owner;
        }
        
    }
    
    Frame frame;
    
    public ComputeException(final String message, final Frame frame) {
        super(message + "\nframe: " + frame.snapshot());
        this.frame = frame;
    }
    
}
