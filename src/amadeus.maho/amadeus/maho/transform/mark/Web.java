package amadeus.maho.transform.mark;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.http.HttpClient;

import amadeus.maho.transform.handler.WebRequestTransformer;
import amadeus.maho.transform.mark.base.TransformMark;
import amadeus.maho.transform.mark.base.TransformMetadata;
import amadeus.maho.util.annotation.mark.IgnoredDefaultValue;
import amadeus.maho.util.link.http.HttpHelper;

public @interface Web {
    
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Query { }
    
    @TransformMark(WebRequestTransformer.class)
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Request {
        
        String value();
        
        String context() default HttpHelper.DEFAULT_DOMAIN;
        
        String method() default HttpHelper.RequestType.GET;
        
        String[] headers() default { };
        
        int timeout() default 3 * 1000;
        
        HttpClient.Version version() default HttpClient.Version.HTTP_2;
        
        boolean expectContinue() default false;
        
        int[] expectStateCode() default { HttpHelper.StatesCode.OK };
        
        HttpHelper.QueryType queryType() default HttpHelper.QueryType.URLENCODED;
    
        @IgnoredDefaultValue
        TransformMetadata metadata() default @TransformMetadata;
        
    }
    
}
