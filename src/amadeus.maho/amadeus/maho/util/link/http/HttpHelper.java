package amadeus.maho.util.link.http;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import amadeus.maho.lang.Extension;

public interface HttpHelper {
    
    @Extension
    interface Ext {
        
        static <R> HttpResponse<R> sendMayThrow(final HttpClient client, final HttpRequest request, final HttpResponse.BodyHandler<R> responseBodyHandler) throws Throwable {
            final Throwable p_throwable[] = { null };
            final HttpResponse<R> response = client.send(request, info -> {
                try {
                    return responseBodyHandler.apply(info);
                } catch (final Throwable throwable) {
                    p_throwable[0] = throwable;
                    return (HttpResponse.BodySubscriber<R>) HttpResponse.BodySubscribers.discarding();
                }
            });
            if (p_throwable[0] != null)
                throw p_throwable[0];
            return response;
        }
        
    }
    
    interface RequestType {
        
        String
                GET      = "GET",
                HEAD     = "HEAD",
                POST     = "POST",
                PUT      = "PUT",
                DELETE   = "DELETE",
                CONNECT  = "CONNECT",
                OPTIONS  = "OPTIONS",
                TRACE    = "TRACE",
                PATCH    = "PATCH",
                BREW     = "BREW",
                PROPFIND = "PROPFIND",
                WHEN     = "WHEN";
        
    }
    
    interface StatesCode {
        
        int
                CONTINUE                        = 100,
                SWITCHING_PROTOCOLS             = 101,
                PROCESSING                      = 102,
                EARLY_HINTS                     = 103,
                OK                              = 200,
                CREATED                         = 201,
                ACCEPTED                        = 202,
                NON_AUTHORITATIVE_INFORMATION   = 203,
                NO_CONTENT                      = 204,
                RESET_CONTENT                   = 205,
                PARTIAL_CONTENT                 = 206,
                MULTI_STATUS                    = 207,
                ALREADY_REPORTED                = 208,
                IM_USED                         = 226,
                POLICE_ON_THE_WAY               = 251,
                MULTIPLE_CHOICES                = 300,
                MOVED_PERMANENTLY               = 301,
                FOUND                           = 302,
                SEE_OTHER                       = 303,
                NOT_MODIFIED                    = 304,
                USE_PROXY                       = 305,
                SWITCH_PROXY                    = 306,
                TEMPORARY_REDIRECT              = 307,
                PERMANENT_REDIRECT              = 308,
                BAD_REQUEST                     = 400,
                UNAUTHORIZED                    = 401,
                PAYMENT_REQUIRED                = 402,
                FORBIDDEN                       = 403,
                NOT_FOUND                       = 404,
                METHOD_NOT_ALLOWED              = 405,
                NOT_ACCEPTABLE                  = 406,
                PROXY_AUTHENTICATION_REQUIRED   = 407,
                REQUEST_TIMEOUT                 = 408,
                CONFLICT                        = 409,
                GONE                            = 410,
                LENGTH_REQUIRED                 = 411,
                PRECONDITION_FAILED             = 412,
                REQUEST_ENTITY_TOO_LARGE        = 413,
                URI_TOO_LONG                    = 414,
                UNSUPPORTED_MEDIA_TYPE          = 415,
                RANGE_NOT_SATISFIABLE           = 416,
                EXPECTATION_FAILED              = 417,
                IM_A_TEAPOT                     = 418,
                MISDIRECTED_REQUEST             = 421,
                UNPROCESSABLE_ENTITY            = 422,
                LOCKED                          = 423,
                FAILED_DEPENDENCY               = 424,
                TOO_EARLY                       = 425,
                UPGRADE_REQUIRED                = 426,
                PRECONDITION_REQUIRED           = 428,
                TOO_MANY_REQUESTS               = 429,
                REQUEST_HEADER_FIELDS_TOO_LARGE = 431,
                UNAVAILABLE_FOR_LEGAL_REASONS   = 451,
                INTERNAL_SERVER_ERROR           = 500,
                NOT_IMPLEMENTED                 = 501,
                BAD_GATEWAY                     = 502,
                SERVICE_UNAVAILABLE             = 503,
                GATEWAY_TIMEOUT                 = 504,
                HTTP_VERSION_NOT_SUPPORTED      = 505,
                VARIANT_ALSO_NEGOTIATES         = 506,
                INSUFFICIENT_STORAGE            = 507,
                LOOP_DETECTED                   = 508,
                NOT_EXTENDED                    = 510,
                NETWORK_AUTHENTICATION_REQUIRED = 511;
        
        interface Apache {
            
            int
                    THIS_IS_FINE             = 218,
                    BANDWIDTH_LIMIT_EXCEEDED = 509;
            
        }
        
        interface Nginx {
            
            int
                    NO_RESPONSE                     = 444,
                    REQUEST_HEADER_TOO_LARGE        = 494,
                    SSL_CERTIFICATE_ERROR           = 495,
                    SSL_CERTIFICATE_REQUIRED        = 496,
                    HTTP_REQUEST_SENT_TO_HTTPS_PORT = 497,
                    CLIENT_CLOSED_REQUEST           = 499;
            
        }
        
        interface IIS {
            
            int
                    LOGIN_TIMEOUT = 440,
                    RETRY_WITH    = 449;
            
        }
        
        interface Cloudflare {
            
            int
                    UNKNOWN_ERROR           = 520,
                    WEB_SERVER_IS_DOWN      = 494,
                    CONNECTION_TIMED_OUT    = 494,
                    ORIGIN_IS_UNREACHABLE   = 494,
                    A_TIMEOUT_OCCURRED      = 494,
                    SSL_HANDSHAKE_FAILED    = 495,
                    INVALID_SSL_CERTIFICATE = 496,
                    RAILGUN_ERROR           = 497,
                    ORIGIN_DNS_ERROR        = 499;
            
        }
        
    }
    
    interface Header {
        
        String
                A_IM                             = "A-IM",
                Accept                           = "Accept",
                Accept_Charset                   = "Accept-Charset",
                Accept_Datetime                  = "Accept-Datetime",
                Accept_Encoding                  = "Accept-Encoding",
                Accept_Language                  = "Accept-Language",
                Access_Control_Request_Method    = "Access-Control-Request-Method",
                Access_Control_Request_Headers   = "Access-Control-Request-Headers",
                Authorization                    = "Authorization",
                Cache_Control                    = "Cache-Control",
                Connection                       = "Connection",
                Content_Length                   = "Content-Length",
                Content_MD5                      = "Content-MD5",
                Content_Type                     = "Content-Type",
                Cookie                           = "Cookie",
                Date                             = "Date",
                Expect                           = "Expect",
                Forwarded                        = "Forwarded",
                From                             = "From",
                Host                             = "Host",
                HTTP2_Settings                   = "HTTP2-Settings",
                If_Match                         = "If-Match",
                If_Modified_Since                = "If-Modified-Since",
                If_None_Match                    = "If-None-Match",
                If_Range                         = "If-Range",
                If_Unmodified_Since              = "If-Unmodified-Since",
                Max_Forwards                     = "Max-Forwards",
                Origin                           = "Origin",
                Pragma                           = "Pragma",
                Proxy_Authorization              = "Proxy-Authorization",
                Range                            = "Range",
                Referer                          = "Referer",
                TE                               = "TE",
                User_Agent                       = "User-Agent",
                Upgrade                          = "Upgrade",
                Via                              = "Via",
                Warning                          = "Warning",
                Upgrade_Insecure_Requests        = "Upgrade-Insecure-Requests",
                X_Requested_With                 = "X-Requested-With",
                DNT                              = "DNT",
                X_Forwarded_For                  = "X-Forwarded-For",
                X_Forwarded_Host                 = "X-Forwarded-Host",
                X_Forwarded_Proto                = "X-Forwarded-Proto",
                Front_End_Https                  = "Front-End-Https",
                X_Http_Method_Override           = "X-Http-Method-Override",
                X_ATT_DeviceId                   = "X-ATT-DeviceId",
                X_Wap_Profile                    = "X-Wap-Profile",
                Proxy_Connection                 = "Proxy-Connection",
                X_UIDH                           = "X-UIDH",
                X_Csrf_Token                     = "X-Csrf-Token",
                X_Request_ID                     = "X-Request-ID",
                X_Correlation_ID                 = "X-Correlation-ID",
                Save_Data                        = "Save-Data",
                Access_Control_Allow_Origin      = "Access-Control-Allow-Origin",
                Access_Control_Allow_Credentials = "Access-Control-Allow-Credentials",
                Access_Control_Expose_Headers    = "Access-Control-Expose-Headers",
                Access_Control_Max_Age           = "Access-Control-Max-Age",
                Access_Control_Allow_Methods     = "Access-Control-Allow-Methods",
                Access_Control_Allow_Headers     = "Access-Control-Allow-Headers",
                Accept_Patch                     = "Accept-Patch",
                Accept_Ranges                    = "Accept-Ranges",
                Age                              = "Age",
                Allow                            = "Allow",
                Alt_Svc                          = "Alt-Svc",
                Content_Disposition              = "Content-Disposition",
                Content_Encoding                 = "Content-Encoding",
                Content_Language                 = "Content-Language",
                Content_Location                 = "Content-Location",
                Content_Range                    = "Content-Range",
                Delta_Base                       = "Delta-Base",
                ETag                             = "ETag",
                Expires                          = "Expires",
                IM                               = "IM",
                Last_Modified                    = "Last-Modified",
                Link                             = "Link",
                Location                         = "Location",
                P3P                              = "P3P",
                Proxy_Authenticate               = "Proxy-Authenticate",
                Public_Key_Pins                  = "Public-Key-Pins",
                Retry_After                      = "Retry-After",
                Server                           = "Server",
                Set_Cookie                       = "Set-Cookie",
                Strict_Transport_Security        = "Strict-Transport-Security",
                Trailer                          = "Trailer",
                Transfer_Encoding                = "Transfer-Encoding",
                Tk                               = "Tk",
                Vary                             = "Vary",
                WWW_Authenticate                 = "WWW-Authenticate",
                X_Frame_Options                  = "X-Frame0-Options",
                Content_Security_Policy          = "Content-Security-Policy",
                X_Content_Security_Policy        = "X-Content-Security-Policy",
                X_WebKit_CSP                     = "X-WebKit-CSP",
                Refresh                          = "Refresh",
                Status                           = "Status",
                Timing_Allow_Origin              = "Timing-Allow-Origin",
                X_Content_Duration               = "X-Content-Duration",
                X_Content_Type_Options           = "X-Content-Type-Options",
                X_Powered_By                     = "X-Powered-By",
                X_UA_Compatible                  = "X-UA-Compatible",
                X_XSS_Protection                 = "X-XSS-Protection";
        
    }
    
}
