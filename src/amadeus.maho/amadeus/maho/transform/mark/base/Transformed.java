package amadeus.maho.transform.mark.base;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import amadeus.maho.transform.mark.Share;

@Share
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Transformed { }
