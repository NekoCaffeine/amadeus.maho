package amadeus.maho.util.build;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.MODULE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Dependencies {

    String[] value() default { };

}
