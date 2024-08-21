package amadeus.maho.util.link.http;

import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.ToString;
import amadeus.maho.lang.inspection.Nullable;

@ToString
@EqualsAndHashCode
public record MediaType(String mainType, String subType, @Nullable String tree, Map<String, String> parameters) {
    
    public static MediaType parse(final String contentType) {
        final String parts[] = contentType.split(";"), mimeTypeParts[] = parts[0].trim().split("/", 2), subTypeParts[] = mimeTypeParts[1].trim().split("\\+", 2);
        final boolean hasTree = subTypeParts.length > 1;
        final Map<String, String> parameters = Stream.of(parts).skip(1).map(part -> part.trim().split("=", 2)).collect(Collectors.toMap(part -> part[0], part -> part[1]));
        return { mimeTypeParts[0], hasTree ? subTypeParts[1] : subTypeParts[0], hasTree ? subTypeParts[0] : null, parameters };
    }
    
    public static List<MediaType> parse(final HttpResponse.ResponseInfo responseInfo) = responseInfo.headers().allValues(HttpHelper.Header.Content_Type).stream().map(MediaType::parse).toList();
    
    public Charset charset() = parameters()["charset"] instanceof String charset ? Charset.forName(charset) : StandardCharsets.UTF_8;
    
    @Override
    public String toString() = STR."\{mainType}/\{tree == null ? subType : STR."\{subType}+\{tree}"}\{parameters.entrySet().stream().map(entry -> STR."; \{entry.getKey()}=\{entry.getValue()}").collect(Collectors.joining())}";
    
    public static @Nullable Charset charset(final HttpResponse.ResponseInfo responseInfo, final Predicate<MediaType> predicate)
        = ~parse(responseInfo).stream().filter(predicate).map(MediaType::charset);
    
    public static @Nullable Charset charsetBySubType(final HttpResponse.ResponseInfo responseInfo, final String subType)
        = charset(responseInfo, mediaType -> mediaType.subType().equals(subType));
    
}
