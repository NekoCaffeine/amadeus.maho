package amadeus.maho.util.data;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.function.IntPredicate;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.VisitorChain;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.dynamic.DynamicObject;
import amadeus.maho.util.language.parsing.ParseException;

import static amadeus.maho.util.language.parsing.Tokenizer.*;

/*
 * JSON (JavaScript Object Notation)
 * See also: https://www.json.org/json-en.html
 * See also: https://www.ietf.org/rfc/rfc4627.txt
 */
public interface Json {
    
    @VisitorChain
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PUBLIC)
    class Visitor {
        
        public void beginObject();
        
        public void endObject();
        
        public void beginArray();
        
        public void endArray();
        
        public void visitKey(final String key);
        
        public void visitValue(final @Nullable Object value);
        
    }
    
    @FieldDefaults(level = AccessLevel.PROTECTED)
    class Dynamic extends Visitor {
        
        final ArrayDeque<DynamicObject> deque = { };
        
        final ArrayDeque<String> keys = { };
        
        @Nullable DynamicObject root;
        
        public DynamicObject root() {
            assert root != null;
            assert deque.isEmpty();
            assert keys.isEmpty();
            return root;
        }
        
        @Override
        public void beginObject() = deque << new DynamicObject.MapUnit();
        
        @Override
        public void endObject() = end(deque.removeLast());
        
        @Override
        public void beginArray() = deque << new DynamicObject.ArrayUnit();
        
        @Override
        public void endArray() = end(deque.removeLast());
        
        @Override
        public void visitKey(final String key) {
            keys << key;
            assert deque.peekLast() instanceof DynamicObject.MapUnit;
        }
        
        @Override
        public void visitValue(final @Nullable Object value) = end(switch (value) {
            case String string      -> new DynamicObject.StringUnit(string);
            case BigDecimal decimal -> new DynamicObject.DecimalUnit(decimal);
            case Boolean bool       -> new DynamicObject.BooleanUnit(bool);
            case null               -> DynamicObject.NullUnit.instance();
            default                 -> throw new IllegalStateException("Unexpected value: " + value);
        });
        
        protected void end(final DynamicObject object) {
            assert root == null;
            final @Nullable DynamicObject prev = deque.peekLast();
            switch (prev) {
                case DynamicObject.ArrayUnit arrayUnit -> arrayUnit.asList() += object;
                case DynamicObject.MapUnit mapUnit     -> mapUnit.asMap()[keys.removeLast()] = object;
                case null                              -> root = object;
                default                                -> { assert false; }
            }
        }
        
    }
    
    String SUFFIX = ".json";
    
    // @formatter:off
    IntPredicate
    ws = c -> switch (c) {
        case 0x20, 0x09, 0x0D, 0x0A -> true;
        default -> false;
    },
    token = c -> switch (c) {
        case ',', ':', '"', '{', '}', '[', ']' -> true;
        default -> ws.test(c);
    };
    // @formatter:on
    
    int hexTable[] = new int[256].let(table -> {
        Arrays.fill(table, -1);
        final int offset_0 = '0', numberLength = '9' - '0' + 1;
        for (int i = 0; i < numberLength; i++)
            table[offset_0 + i] = i;
        final int offset_a = 'a', offset_A = 'A', letterLength = 'f' - 'a' + 1;
        for (int i = 0; i < letterLength; i++) {
            table[offset_a + i] = numberLength + i;
            table[offset_A + i] = numberLength + i;
        }
    });
    
    EscapeFunction escape = (context, codePoint) -> switch (codePoint) {
        case '\\', '/', '"' -> codePoint;
        case 'b'            -> '\b';
        case 'f'            -> '\f';
        case 'n'            -> '\n';
        case 'r'            -> '\r';
        case 't'            -> '\t';
        case 'u'            -> {
            final char high = scan4Hex(context);
            if (Character.isHighSurrogate(high) && context.match("\\u")) {
                context.offset(2);
                final char low = scan4Hex(context);
                if (Character.isLowSurrogate(low))
                    yield Character.toCodePoint(high, low);
                context.rollback(6);
            }
            yield high;
        }
        default             -> throw context.invalid("invalid escape codePoint: " + codePoint);
    };
    
    private static char scan4Hex(final Context context) {
        char result = 0;
        for (int i = 3; i > -1; i--) {
            final int c = context.checkEOF().nextChar();
            if (c > hexTable.length)
                throw context.invalid();
            final int v = hexTable[c];
            if (v == -1)
                throw context.invalid();
            result |= result << (i << 3);
        }
        return result;
    }
    
    static void read(final String source, final Visitor visitor, final @Nullable String debugInfo = source) throws IOException = scanJsonBody(visitor, tokenization(source, debugInfo).root());
    
    static void read(final Path path, final Charset charset = StandardCharsets.UTF_8, final Visitor visitor, final @Nullable String debugInfo = path.toString()) throws IOException {
        try (final var input = Files.newInputStream(path)) { read(input, visitor, debugInfo); }
    }
    
    @SneakyThrows
    static void read(final InputStream input, final Charset charset = StandardCharsets.UTF_8, final Visitor visitor, final @Nullable String debugInfo) = scanJsonBody(visitor, tokenization(input.readString(charset), debugInfo).root());
    
    static void scanJsonBody(final Visitor visitor, final Context context) {
        scanValue(visitor, context);
        if (context.hasNext())
            throw context.invalid();
    }
    
    private static void scanValue(final Visitor visitor, final Context context) {
        context.skip(ws);
        switch (context.checkEOF().nextChar()) {
            case '"'                                                        -> visitor.visitValue(scanString(context, true));
            case '-', '+', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> visitor.visitValue(scanNumber(context.rollback()));
            case '{'                                                        -> {
                visitor.beginObject();
                context.skip(ws);
                while (!context.checkEOF().match("}")) {
                    visitor.visitKey(scanString(context, false));
                    context.skip(ws);
                    scanValue(visitor, context.skip(":"));
                    if (context.skip(c -> c == ',', context::nextChar) > 0)
                        context.skip(ws);
                }
                context.nextChar();
                visitor.endObject();
            }
            case '['                                                        -> {
                visitor.beginArray();
                context.skip(ws);
                while (!context.checkEOF().match("]")) {
                    scanValue(visitor, context);
                    context.skip(c -> c == ',', context::nextChar);
                }
                context.nextChar();
                visitor.endArray();
            }
            case 't'                                                        -> {
                context.skip("true".substring(1));
                visitor.visitValue(true);
            }
            case 'f'                                                        -> {
                context.skip("false".substring(1));
                visitor.visitValue(false);
            }
            case 'n'                                                        -> {
                context.skip("null".substring(1));
                visitor.visitValue(null);
            }
            default                                                         -> throw context.invalid("invalid json value start with: " + context.nowChar());
        }
        context.skip(ws);
    }
    
    private static int scanChar(final Context context) throws ParseException {
        final int nextChar = context.checkEOF().nextChar();
        return switch (nextChar) {
            case '"'  -> -1; // break
            case '\\' -> escape.escape(context, context.checkEOF().nextChar());
            default   -> nextChar;
        };
    }
    
    private static String scanString(final Context context, final boolean inner) {
        final String result = (!inner ? context.skip("\"") : context).scanString(c -> c > 0, 0, null, () -> scanChar(context));
        context.skip("\"");
        return result;
    }
    
    private static BigDecimal scanNumber(final Context context) = { context.scanString(token.negate()) };
    
}
