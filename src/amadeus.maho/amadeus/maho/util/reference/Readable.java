package amadeus.maho.util.reference;

import amadeus.maho.lang.Extension;
import amadeus.maho.lang.inspection.Nullable;

@FunctionalInterface
public interface Readable<T> extends Overwriter<T>, Overwriter.Replace {
    
    @FunctionalInterface
    interface Byte extends Overwriter.Byte, Overwriter.Replace {
        
        @Extension.Operator("~")
        byte get();
        
        @Override
        default byte overwrite(final byte value) = get();
        
    }
    
    @FunctionalInterface
    interface Short extends Overwriter.Short, Overwriter.Replace {
        
        @Extension.Operator("~")
        short get();
        
        @Override
        default short overwrite(final short value) = get();
        
    }
    
    @FunctionalInterface
    interface Int extends Overwriter.Int, Overwriter.Replace {
        
        @Extension.Operator("~")
        int get();
        
        @Override
        default int overwrite(final int value) = get();
    
    }
    
    @FunctionalInterface
    interface Long extends Overwriter.Long, Overwriter.Replace {
        
        @Extension.Operator("~")
        long get();
        
        @Override
        default long overwrite(final long value) = get();
    
    }
    
    @FunctionalInterface
    interface Float extends Overwriter.Float, Overwriter.Replace {
        
        @Extension.Operator("~")
        float get();
        
        @Override
        default float overwrite(final float value) = get();
    
    }
    
    @FunctionalInterface
    interface Double extends Overwriter.Double, Overwriter.Replace {
        
        @Extension.Operator("~")
        double get();
        
        @Override
        default double overwrite(final double value) = get();
    
    }
    
    @FunctionalInterface
    interface Boolean extends Overwriter.Boolean, Overwriter.Replace {
        
        @Extension.Operator("~")
        boolean get();
        
        @Override
        default boolean overwrite(final boolean value) = get();
    
    }
    
    @FunctionalInterface
    interface Char extends Overwriter.Char, Overwriter.Replace {
        
        @Extension.Operator("~")
        char get();
        
        @Override
        default char overwrite(final char value) = get();
    
    }
    
    @Extension.Operator("~")
    @Nullable T get();
    
    @Override
    default @Nullable T overwrite(@Nullable final T value) = get();
    
}
