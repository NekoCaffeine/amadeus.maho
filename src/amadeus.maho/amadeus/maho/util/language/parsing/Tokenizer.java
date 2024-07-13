package amadeus.maho.util.language.parsing;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;
import java.util.stream.IntStream;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.Delegate;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.container.MapTable;

import static java.lang.Character.getType;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Tokenizer {
    
    @FunctionalInterface
    public interface EscapeFunction {
        
        int escape(Context context, int codePoint);
        
    }
    
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PUBLIC)
    public static class Context {
        
        @Delegate
        final Tokenizer tokenizer;
        
        final @Nullable Context parent;
        
        int startPos, nowPos;
        
        final MapTable<Integer, Parser<?>, Object> cache;
        
        @Default
        final StringBuilder builder = { };
    
        public StringBuilder builder() {
            builder.setLength(0);
            return builder;
        }
    
        public Context dup() = { tokenizer, this, nowPos, nowPos, cache };
    
        public self rollback(final int length = 1) = nowPos -= length;
    
        public boolean withinRange(final int range = codePoints().length) = nowPos < range;
    
        public int nowChar() = withinRange() ? codePoints()[nowPos] : '\0';
    
        public boolean hasNext() = withinRange(codePoints().length - 1);
    
        public int nextChar() = hasNext() ? codePoints()[++nowPos] : '\0';
    
        public int scanChar(final EscapeFunction escape) throws ParseException {
            final int nextChar = nextChar();
            return nextChar == '\\' ? escape.escape(this, nextChar()) : nextChar;
        }
        
        public String scanString(final IntPredicate checker = javaIdentifierChecker(), final int min = 0, final @Nullable String end = null, final IntSupplier nextChar = this::nextChar, final int max = -1) {
            final StringBuilder builder = builder();
            int count = 0, endChar = '\0';
            boolean next = false;
            if (hasNext()) {
                next = true;
                while (hasNext() && (max == -1 || count < max) && checker.test(endChar = nextChar.getAsInt())) {
                    builder.appendCodePoint(endChar);
                    if (end != null && builder.endsWith(end))
                        break;
                    ++count;
                }
            }
            if (end != null && !builder.endsWith(end))
                throw checkEOF().invalid();
            if (next && !checker.test(endChar))
                rollback();
            if (builder.length() < min)
                throw invalid();
            return builder.toString();
        }
        
        public int skip(final IntPredicate checker = Character::isWhitespace, final IntSupplier nextChar = this::nextChar, final int max = -1) {
            if (!hasNext())
                return 0;
            int count = 0, endChar = '\0';
            while (hasNext() && (max == -1 || count < max) && checker.test(endChar = nextChar.getAsInt()))
                ++count;
            if (!checker.test(endChar))
                rollback();
            return count;
        }
        
        public self skipAtLeast(final IntPredicate checker = Character::isWhitespace, final int min = 1, final IntSupplier nextChar = this::nextChar, final int max = -1) {
            if (skip(checker, nextChar, max) < min)
                throw invalid();
        }
        
        public self skipAny(final IntPredicate checker = Character::isWhitespace, final IntSupplier nextChar = this::nextChar) = skip(checker, nextChar);
        
        public self skip(final String string, final IntSupplier nextChar = this::nextChar) { if (!string.codePoints().allMatch(c -> c == nextChar())) throw new ParseException(debugInformation(), nowPos); }
        
        public self skipAfter(final IntPredicate end = Character::isWhitespace, final IntSupplier nextChar = this::nextChar) { while (hasNext() && !end.test(nextChar.getAsInt())); }
        
        public self skipAfter(final int end, final IntSupplier nextChar = this::nextChar) = skipAfter(it -> it == end, nextChar);
    
        public boolean match(final String string, final IntSupplier nextChar = this::nextChar) {
            final int now = nowPos;
            try { return string.codePoints().allMatch(c -> c == nextChar()); } finally { nowPos = now; }
        }
        
        public String scanAfter(final IntPredicate end = Character::isWhitespace, final IntSupplier nextChar = this::nextChar) {
            final StringBuilder builder = builder();
            int next;
            while (hasNext() && !end.test(next = nextChar.getAsInt()))
                builder.appendCodePoint(next);
            return builder.toString();
        }
        
        public String scanAfter(final int end, final IntSupplier nextChar = this::nextChar) = scanAfter(it -> it == end, nextChar);
        
        public int offset(final int max = 1) {
            final int result = Integer.min(codePoints().length - nowPos - 1, max);
            if (result > 0)
                nowPos += result;
            return result;
        }
        
        public <R> R scan(final Parser<R> parser) throws ParseException {
            @Nullable R result = (R) cache.get(nowPos, parser);
            if (result == null)
                cache.put(nowPos, parser, result = parser.parse(dup()));
            return result;
        }
    
            public ParseException eof() = { "Unexpected end of file.", debugInformation(), nowPos };
    
            public self checkEOF() { if (!hasNext()) throw eof(); }
    
            public ParseException invalid(final boolean next = false) = { STR."Invalid character: '\{next ? nextChar() : nowChar()}'", debugInformation(), nowPos };
    
            public ParseException invalid(final String string) = { STR."Invalid string: '\{string}'", debugInformation(), nowPos };
    
            public void assertState(final boolean state) {
            if (!state)
                throw invalid();
        }
        
            public void assertNonnull(final @Nullable Object object) = assertState(object != null);
        
    }
    
    /*
         Use 4 bytes to represent a single character, which will consume more space in exchange for a lower offset cost (random access).
         For details, see: https://en.wikipedia.org/wiki/Unicode
         Since emoji with color marking is not supported, these 4 bytes will be enough.
         I think it's a stupid thing to support emoji in the lexical flow, especially emoji is a stupid design in itself.
         I don't understand why they put a color mark behind emoji to indicate different skin tones for political correctness,
          which directly leads to the inability to express a character with 4 bytes.
         If you want to support this feature, you need at least a byte array of equal length to achieve random access.
         Just like: `byte marks[] = new byte[chars.length];`
    */
    int codePoints[];
    
    @Nullable String debugInformation;
    
    Function<Class<?>, Parser<?>> parserProvider;
    
    public <R> Parser<R> parser(final Class<R> type) = (Parser<R>) parserProvider().apply(type);
    
    public Context root() = { this, null, -1, -1, MapTable.of(new HashMap<>(), _ -> new IdentityHashMap<>()) };
    
    public <R> R parsing(final Parser<R> parser, final Context context = root()) throws ParseException = context.scan(parser);
    
    public <R> R parsing(final Class<R> type, final Context context = root()) throws ParseException = parsing(parser(type), context);
    
    public static Tokenizer tokenization(final String source, final @Nullable String debugInformation = null, final Function<Class<?>, Parser<?>> parserProvider = type -> null)
            = { source.codePoints().filter(it -> it != '\r').toArray(), debugInformation, parserProvider };
    
    public static Tokenizer tokenization(final IntStream codePoints, final @Nullable String debugInformation = null, final Function<Class<?>, Parser<?>> parserProvider = type -> null)
            = { codePoints.toArray(), debugInformation, parserProvider };
    
    public static Tokenizer tokenization(final int codePoints[], final @Nullable String debugInformation = null, final Function<Class<?>, Parser<?>> parserProvider = type -> null)
            = { codePoints, debugInformation, parserProvider };
    
    public static boolean isNumber(final int c) = c >= '0' && c < '9';
    
    public static boolean isNumberOrDot(final int c) = c == '.' || isNumber(c);
    
    public static boolean isLowerLetter(final int c) = c >= 'a' && c <= 'z';
    
    public static boolean isUpperLetter(final int c) = c >= 'A' && c <= 'Z';
    
    public static boolean isLetter(final int c) = isLowerLetter(c) || isUpperLetter(c);
    
    public static boolean isLetterOrNumber(final int c) = isLetter(c) || isNumber(c);
    
    public static boolean isEncodingStart(final int c) = isLetter(c);
    
    public static boolean isEncodingPart(final int c) = switch (c) {
        case '-', '_', '.' -> true;
        default -> isLetter(c) || isNumber(c);
    };
    
    public static IntPredicate typeChecker(final int... types) {
        final int type = IntStream.of(types).map(i -> 1 << i).reduce(0, (a, b) -> a | b);
        return codePoint -> isType(type, codePoint);
    }
    
    public static boolean isType(final int type, final int codePoint) = (type >> getType(codePoint) & 1) != 0;
    
    public static IntPredicate firstThen(final IntPredicate first, final IntPredicate then) {
        final boolean p_flag[] = { false };
        return c -> p_flag[0] ? then.test(c) : (p_flag[0] = true) && first.test(c);
    }
    
    public static IntPredicate javaIdentifierChecker() = firstThen(Character::isJavaIdentifierStart, Character::isJavaIdentifierPart);
    
    public static IntPredicate encodingChecker() = firstThen(Tokenizer::isEncodingStart, Tokenizer::isEncodingPart);
    
}
