package amadeus.maho.util.runtime;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import amadeus.maho.lang.Extension;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.inspection.RegularExpression;

@Extension
public interface StringHelper {
    
    String EMPTY = "";
    
    static boolean isEmptyOrNull(final @Nullable String string) = string == null || string.isEmpty();
    
    static boolean nonEmptyOrNull(final @Nullable String string) = !isEmptyOrNull(string);
    
    static String isEmptyOr(final @Nullable String string, final String or) {
        // noinspection DataFlowIssue
        return isEmptyOrNull(string) ? or : string;
    }
    
    static String requireNonEmpty(final @Nullable String string) {
        if (isEmptyOrNull(string))
            throw new NullPointerException();
        // noinspection DataFlowIssue
        return string;
    }
    
    static boolean isJavaIdentifierPart(final String string) = string.codePoints().allMatch(Character::isJavaIdentifierPart);
    
    static String readString(final InputStream input, final Charset charset = StandardCharsets.UTF_8) = new BufferedReader(new InputStreamReader(input, charset)).lines().collect(Collectors.joining("\n"));
    
    @SneakyThrows
    static void writeString(final OutputStream output, final String string, final Charset charset = StandardCharsets.UTF_8) = new BufferedOutputStream(output).write(string.getBytes(charset));
    
    static String replaceLast(final String $this, final CharSequence target, final CharSequence replacement) {
        final int lastIndex = $this.lastIndexOf(target.toString());
        return lastIndex > -1 ? $this.substring(0, lastIndex) + replacement + $this.substring(lastIndex + target.length()) : $this;
    }
    
    static boolean endsWith(final StringBuilder $this, final String suffix) = $this.length() >= suffix.length() && $this.substring($this.length() - suffix.length()).endsWith(suffix);
    
    static boolean endsWith(final StringBuffer $this, final String suffix) = $this.length() >= suffix.length() && $this.substring($this.length() - suffix.length()).endsWith(suffix);
    
    static boolean startsWith(final StringBuilder $this, final String prefix) = $this.length() >= prefix.length() && $this.substring(0, prefix.length()).startsWith(prefix);
    
    static boolean startsWith(final StringBuffer $this, final String prefix) = $this.length() >= prefix.length() && $this.substring(0, prefix.length()).startsWith(prefix);
    
    static String dropStartsWith(final String string, final String drop) {
        final int index = string.indexOf(drop);
        return index == -1 ? string : string.substring(0, index);
    }
    
    static String _ToUpper(final String string) {
        boolean upper = false;
        final StringBuilder builder = { };
        for (final char c : string.toCharArray())
            if (c == '_')
                upper = true;
            else if (upper) {
                builder.append(Character.toUpperCase(c));
                upper = false;
            } else
                builder.append(c);
        return builder.toString();
    }
    
    static String upperTo_(final String string) {
        final StringBuilder builder = { };
        boolean upper = false, first = true, last = false;
        final char[] chars = string.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            final char c = chars[i];
            if (!Character.isLetterOrDigit(c)) {
                upper = false;
                first = true;
                last = false;
                builder.append(c);
            } else if (Character.isUpperCase(c)) {
                if (last) {
                    first = false;
                    if (i + 1 < chars.length && Character.isLowerCase(chars[i + 1]))
                        builder.append('_');
                }
                if (upper && !last) {
                    builder.append(first ? '.' : '_');
                    if (first)
                        first = false;
                } else {
                    upper = true;
                }
                builder.append(Character.toLowerCase(c));
                last = true;
            } else {
                builder.append(c);
                last = false;
            }
        }
        return builder.toString();
    }
    
    static Predicate<String> matchPredicate(final @RegularExpression String regex) = string -> string.matches(regex);
    
    static Pattern pattern(final @RegularExpression String regex) = Pattern.compile(regex);
    
    static String find(final String str, final @RegularExpression String regex) {
        final Matcher matcher = pattern(regex).matcher(str);
        return matcher.find() ? matcher.group(1) : "";
    }
    
    static List<String> findAll(final String str, final @RegularExpression String regex) {
        final Matcher matcher = pattern(regex).matcher(str);
        final List<String> result = new LinkedList<>();
        while (matcher.find())
            result += matcher.group(1);
        return result;
    }
    
    static String upper(final String string, int index, final int length = 1) {
        final char chars[] = string.toCharArray();
        for (; index < length; index++)
            chars[index] = Character.toUpperCase(chars[index]);
        return { chars };
    }
    
    static String lower(final String string, int index, final int length = 1) {
        final char chars[] = string.toCharArray();
        for (; index < length; index++)
            chars[index] = Character.toLowerCase(chars[index]);
        return { chars };
    }
    
}
