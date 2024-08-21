package amadeus.maho.util.data;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.VisitorChain;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.dynamic.DynamicObject;
import amadeus.maho.util.dynamic.FieldsMap;
import amadeus.maho.util.language.parsing.ParseException;
import amadeus.maho.util.runtime.TypeHelper;

import static amadeus.maho.util.language.parsing.Tokenizer.*;

/*
 * JSON (JavaScript Object Notation)
 * See also: https://www.json.org/json-en.html
 * See also: https://www.ietf.org/rfc/rfc4627.txt
 */
public interface JSON {
    
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
    class Writer extends Visitor implements CharSequence {
        
        final StringBuilder builder = { 1 << 12 };
        
        @Override
        public void beginObject() = builder.append('{');
        
        @Override
        public void endObject() = builder.append('}');
        
        @Override
        public void beginArray() = builder.append('[');
        
        @Override
        public void endArray() = builder.append(']');
        
        @Override
        public void visitKey(final String key) = builder.append('"').append(key).append('"').append(':');
        
        @Override
        @SneakyThrows
        public void visitValue(final @Nullable Object value) = switch (value) {
            case null                  -> builder.append("null");
            case DynamicObject dynamic -> {
                switch (dynamic) {
                    case DynamicObject.NullUnit ignored      -> builder.append("null");
                    case DynamicObject.StringUnit stringUnit -> builder.append('"').append(stringUnit.asString()).append('"');
                    case DynamicObject.ArrayUnit arrayUnit   -> {
                        beginArray();
                        final List<DynamicObject> list = arrayUnit.asList();
                        final int length = list.size();
                        if (length > 0) {
                            int index = 0;
                            visitValue(list[0]);
                            while (++index < length) {
                                builder.append(',');
                                visitValue(list[index]);
                            }
                        }
                        endArray();
                    }
                    case DynamicObject.MapUnit mapUnit       -> {
                        beginObject();
                        final boolean p_flag[] = { false };
                        mapUnit.asMap().forEach((name, object) -> {
                            if (p_flag[0])
                                builder.append(',');
                            else
                                p_flag[0] = true;
                            visitKey(name);
                            visitValue(object);
                        });
                        endObject();
                    }
                    case DynamicObject.ObjectUnit objectUnit -> visitValue(objectUnit.as());
                    default                                  -> builder.append(dynamic);
                }
            }
            default                    -> {
                final Class<?> type = value.getClass();
                if (TypeHelper.isSimple(type) || Number.class.isAssignableFrom(type))
                    builder.append(value);
                else if (CharSequence.class.isAssignableFrom(type))
                    builder.append('"').append(value).append('"');
                else if (type.isArray()) {
                    beginArray();
                    final int length = Array.getLength(value);
                    if (length > 0) {
                        int index = 0;
                        visitValue(Array.get(value, 0));
                        while (++index < length) {
                            builder.append(',');
                            visitValue(Array.get(value, index));
                        }
                    }
                    endArray();
                } else if (List.class.isAssignableFrom(type)) {
                    beginArray();
                    final List<?> list = (List<?>) value;
                    final int length = list.size();
                    if (length > 0) {
                        int index = 0;
                        visitValue(list[0]);
                        while (++index < length) {
                            builder.append(',');
                            visitValue(list[index]);
                        }
                    }
                    endArray();
                } else if (Map.class.isAssignableFrom(type)) {
                    beginObject();
                    final boolean p_flag[] = { false };
                    ((Map<Object, Object>) value).forEach((name, object) -> {
                        if (p_flag[0])
                            builder.append(',');
                        else
                            p_flag[0] = true;
                        visitKey(name.toString());
                        visitValue(object);
                    });
                    endObject();
                } else {
                    beginObject();
                    final boolean p_flag[] = { false };
                    FieldsMap.fieldsMapLocal()[type].forEach((name, info) -> {
                        if (p_flag[0])
                            builder.append(',');
                        else
                            p_flag[0] = true;
                        visitKey(name);
                        visitValue(info.getter().invoke(value));
                    });
                    endObject();
                }
            }
        };
        
        @Override
        public int length() = builder.length();
        
        @Override
        public char charAt(final int index) = builder.charAt(index);
        
        @Override
        public CharSequence subSequence(final int start, final int end) = builder.substring(start, end);
        
        @Override
        public IntStream chars() = builder.chars();
        
        @Override
        public IntStream codePoints() = builder.codePoints();
        
        @Override
        public String toString() = builder.toString();
        
        public static String write(final @Nullable Object value) {
            if (value == null)
                return "null";
            if ((value instanceof Character character ? character.toString() : value) instanceof String string)
                return STR."\"\{string}\"";
            if (TypeHelper.isSimple(value.getClass()))
                return value.toString();
            final Writer writer = { };
            writer.visitValue(value);
            return writer.toString();
        }
        
    }
    
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PROTECTED)
    class Dynamic extends Visitor {
        
        final ArrayDeque<DynamicObject> deque = { };
        
        final ArrayDeque<String> keys = { };
        
        @Nullable
        DynamicObject root;
        
        public DynamicObject root() {
            assert root != null;
            assert deque.isEmpty();
            assert keys.isEmpty();
            return root;
        }
        
        @Override
        public void beginObject() = deque += new DynamicObject.MapUnit();
        
        @Override
        public void endObject() = end(deque.removeLast());
        
        @Override
        public void beginArray() = deque += new DynamicObject.ArrayUnit();
        
        @Override
        public void endArray() = end(deque.removeLast());
        
        @Override
        public void visitKey(final String key) {
            keys += key;
            assert deque.peekLast() instanceof DynamicObject.MapUnit;
        }
        
        @Override
        public void visitValue(final @Nullable Object value) = end(switch (value) {
            case null                  -> DynamicObject.NullUnit.instance();
            case DynamicObject dynamic -> dynamic;
            case Character character   -> new DynamicObject.StringUnit(character.toString());
            case String string         -> new DynamicObject.StringUnit(string);
            case Number decimal        -> new DynamicObject.DecimalUnit(decimal);
            case Boolean bool          -> new DynamicObject.BooleanUnit(bool);
            default                    -> throw new IllegalStateException(STR."Unexpected value: \{value}");
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
        
        public static DynamicObject read(final String source, final String debugInfo = source) {
            if (source.equals("null"))
                return DynamicObject.NullUnit.instance();
            final Dynamic dynamic = { };
            JSON.read(source, dynamic);
            return dynamic.root();
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
        case '\\',
             '/',
             '"' -> codePoint;
        case 'b' -> '\b';
        case 'f' -> '\f';
        case 'n' -> '\n';
        case 'r' -> '\r';
        case 't' -> '\t';
        case 'u' -> {
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
        default  -> throw context.invalid(STR."invalid escape codePoint: \{codePoint}");
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
            // noinspection lossy-conversions
            result |= result << (i << 3);
        }
        return result;
    }
    
    static void read(final String source, final Visitor visitor, final @Nullable String debugInfo = source) = scanJsonBody(visitor, tokenization(source, debugInfo).root());
    
    static void read(final Path path, final Charset charset = StandardCharsets.UTF_8, final Visitor visitor, final @Nullable String debugInfo = path.toString()) throws IOException {
        try (final var input = Files.newInputStream(path)) { read(input, charset, visitor, debugInfo); }
    }
    
    @SneakyThrows
    static void read(final InputStream input, final Charset charset = StandardCharsets.UTF_8, final Visitor visitor, final @Nullable String debugInfo)
        = scanJsonBody(visitor, tokenization(input.readString(charset), debugInfo).root());
    
    static void scanJsonBody(final Visitor visitor, final Context context) {
        scanValue(visitor, context);
        if (context.hasNext())
            throw context.invalid();
    }
    
    private static void scanValue(final Visitor visitor, final Context context) {
        context.skip(ws);
        switch (context.checkEOF().nextChar()) {
            case '"' -> visitor.visitValue(scanString(context, true));
            case '-',
                 '+',
                 '0',
                 '1',
                 '2',
                 '3',
                 '4',
                 '5',
                 '6',
                 '7',
                 '8',
                 '9' -> visitor.visitValue(scanNumber(context.rollback()));
            case '{' -> {
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
            case '[' -> {
                visitor.beginArray();
                context.skip(ws);
                while (!context.checkEOF().match("]")) {
                    scanValue(visitor, context);
                    context.skip(c -> c == ',', context::nextChar);
                }
                context.nextChar();
                visitor.endArray();
            }
            case 't' -> {
                context.skip("true".substring(1));
                visitor.visitValue(true);
            }
            case 'f' -> {
                context.skip("false".substring(1));
                visitor.visitValue(false);
            }
            case 'n' -> {
                context.skip("null".substring(1));
                visitor.visitValue(null);
            }
            default  -> throw context.invalid(STR."invalid json value start with: \{context.nowChar()}");
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
    
    static String stringify(final @Nullable Object value) = Writer.write(value);
    
    static DynamicObject parse(final String source) = Dynamic.read(source);
    
}
