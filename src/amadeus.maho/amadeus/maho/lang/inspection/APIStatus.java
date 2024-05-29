package amadeus.maho.lang.inspection;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ PACKAGE, TYPE, ANNOTATION_TYPE, CONSTRUCTOR, METHOD, FIELD })
public @interface APIStatus {
    
    enum Stage {
        
        α, // prototype or early version
        β, // partially available
        γ, // release candidate
        δ, // general availability
    
    }
    
    Stage design();
    
    Stage implement();
    
}
