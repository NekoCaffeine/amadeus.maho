package amadeus.maho.util.data;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.VisitorChain;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.function.FunctionHelper;
import amadeus.maho.util.language.parsing.Tokenizer;
import amadeus.maho.util.tuple.Tuple;
import amadeus.maho.util.tuple.Tuple2;

import static amadeus.maho.util.language.parsing.Tokenizer.*;

/*
 * XML (Extensible Markup Language)
 * - 1.0 ?
 *     > Schemas ×
 *     > Mixed Content √
 *     > Attribute √
 *     > CDATA √
 *     > Character References √
 *     > Comments √
 *     > Declaration √
 *     > DocType ×
 *     > Document √
 *     > Entity Refs √
 *     > Processing Instruction √
 *     > Whitespace √
 *       | Syntactical √
 *       | Insignificant √
 *       | Significant √
 * - 1.1 ×
 * DTD ×
 * See also: https://www.w3.org/TR/xml11
 * See also: https://www.liquid-technologies.com/XML/
 */
public interface XML {
    
    @VisitorChain
    @AllArgsConstructor
    class Visitor {
    
        public void visitDeclaration(String version, @Nullable String encoding, @Nullable String standalone);
    
        public void visitProcessingInstructions(String target, String instructions);
    
        public void visitComment(String comment);
    
        public void visitEmptyTag(String tag, Map<String, String> attr);
    
        public void visitTagBegin(String tag, Map<String, String> attr);
    
        public void visitTagEnd(String tag);
    
        public void visitCharData(String data);
        
    }
    
    @FieldDefaults(level = AccessLevel.PROTECTED)
    class Writer extends Visitor implements CharSequence {
        
        final StringBuilder builder = { 1 << 12 };
        
        final String indent = "  ";
        
        int layer;
        
        @Override
        public void visitDeclaration(final String version = "1.0", final @Nullable String encoding = "UTF-8", final @Nullable String standalone = null) {
            builder.append("<?xml version=\"").append(version).append('"');
            if (encoding != null)
                builder.append(" encoding=\"").append(encoding).append('"');
            if (standalone != null)
                builder.append(" standalone=\"").append(standalone).append('"');
            builder.append("?>\n");
        }
        
        @Override
        public void visitProcessingInstructions(final String target, final String instructions) {
            pushIndent();
            builder.append("<?").append(target).append(" ").append(instructions).append(" ?>\n");
        }
        
        @Override
        public void visitComment(final String comment) {
            pushIndent();
            builder.append("<!--").append(comment).append("-->\n");
        }
        
        @Override
        public void visitEmptyTag(final String tag, final Map<String, String> attr = Map.of()) {
            pushIndent();
            builder.append("<").append(tag);
            attr.forEach((name, value) -> builder.append(" ").append(name).append("=\"").append(escape(value)).append('"'));
            builder.append(" />\n");
        }
        
        @Override
        public void visitTagBegin(final String tag, final Map<String, String> attr = Map.of()) {
            pushIndent();
            builder.append("<").append(tag);
            attr.forEach((name, value) -> builder.append(" ").append(name).append("=\"").append(escape(value)).append('"'));
            builder.append(">\n");
            layer++;
        }
        
        public void visitTag(final String tag, final Runnable runnable = FunctionHelper.nothing(), final Map<String, String> attr = Map.of()) {
            visitTagBegin(tag, attr);
            ~runnable;
            visitTagEnd(tag);
        }
        
        @Override
        public void visitTagEnd(final String tag) {
            layer--;
            pushIndent();
            builder.append("</").append(tag).append(">\n");
        }
        
        @Override
        public void visitCharData(final String data) = builder.append(data.replace("&", "&amp;").replace("<", "&lt;"));
        
        public String escape(final String string) = string;
        
        public void pushIndent() = builder.append(indent.repeat(layer));
        
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
        
    }
    
    String SUFFIX = ".xml";
    
    // @formatter:off
    // eg: &it; => <, &amp; => &
    Map<String, Integer> predefined = Map.of(
            "lt"  , 0x3C, // <
            "gt"  , 0x3E, // >
            "amp" , 0x26, // &
            "apos", 0x27, // '
            "quot", 0x22  // "
    );
    
    IntPredicate
    // S (white space) consists of one or more space (#x20) characters, carriage returns, line feeds, or tabs.
    // [3] S ::= (#x20 | #x9 | #xD | #xA)+
    S = c -> switch (c) {
        case 0x20, 0x09, 0x0D, 0x0A -> true;
        default -> false;
    },
    // [4] NameStartChar ::= ":" | [A-Z] | "_" | [a-z] | [#xC0-#xD6] | [#xD8-#xF6] | [#xF8-#x2FF] | [#x370-#x37D] | [#x37F-#x1FFF] | [#x200C-#x200D] | [#x2070-#x218F] |
    //  [#x2C00-#x2FEF] | [#x3001-#xD7FF] | [#xF900-#xFDCF] | [#xFDF0-#xFFFD] | [#x10000-#xEFFFF]
    NameStartChar = c -> switch (c) {
        case ':', '_' -> true;
        default -> isLetter(c) ||
                c >= 0xC0    && c <= 0xD6   ||
                c >= 0xD8    && c <= 0xF6   ||
                c >= 0xF8    && c <= 0x2FF  ||
                c >= 0x370   && c <= 0x37D  ||
                c >= 0x37F   && c <= 0x1FFF ||
                c >= 0x200C  && c <= 0x200D ||
                c >= 0x2070  && c <= 0x218F ||
                c >= 0x2C00  && c <= 0x2FEF ||
                c >= 0x3001  && c <= 0xD7FF ||
                c >= 0xF900  && c <= 0xFDCF ||
                c >= 0xFDF0  && c <= 0xFFFD ||
                c >= 0x10000 && c <= 0xEFFFF;
    },
    // [4a] NameChar ::= NameStartChar | "-" | "." | [0-9] | #xB7 | [#x0300-#x036F] | [#x203F-#x2040]
    NameChar = NameStartChar.or(c -> switch (c) {
        case '-',
             '.',
             0xB7 -> true;
        default   -> isNumber(c) || c >= 0x0300 && c <= 0x036F || c >= 0x203F && c <= 0x2040;
    });
    // @formatter:on
    
    private static IntPredicate nameChecker() = firstThen(NameStartChar, NameChar);
    
    static void read(final String source, final Visitor visitor, final String debugInfo) throws IOException {
        final Context context = tokenization(source, debugInfo).root();
        if (context.match("<?xml "))
            scanXMLDeclaration(visitor, context);
        scanXMLBody(visitor, context);
    }
    
    static void read(final Path path, final Visitor visitor, final String debugInfo = path.toString()) throws IOException { try (final var input = Files.newInputStream(path)) { read(input, visitor, debugInfo); } }
    
    @SneakyThrows
    static void read(final InputStream input, final Visitor visitor, final String debugInfo) throws IOException {
        final Charset charset;
        int c;
        String rollback = "";
        {
            final StringBuilder builder = { 1 << 6 };
            while ((c = input.read()) != '\n' && c != -1)
                builder.append((char) c);
            final Context context = tokenization(builder.toString(), debugInfo).root();
            if (context.match("<?xml "))
                charset = scanXMLDeclaration(visitor, context);
            else {
                charset = StandardCharsets.UTF_8;
                rollback = builder + (c == -1 ? "" : "\n");
            }
        }
        scanXMLBody(visitor, tokenization(rollback + input.readString(charset), debugInfo).root());
    }
    
    private static Charset scanXMLDeclaration(final Visitor visitor, final Context context) {
        Charset charset = StandardCharsets.UTF_8;
        final String version = scanKeyValue(context.skip("<?xml "), "version", Tokenizer::isNumberOrDot);
        String encoding = null;
        String standalone = null;
        switch (version) {
            case "1.0" -> {
                if (context.skip(S) > 0) {
                    if (context.match("encoding="))
                        try {
                            context.offset("encoding=".length());
                            charset = Charset.forName(encoding = scanValue(context, encodingChecker()));
                        } catch (final IllegalCharsetNameException | UnsupportedCharsetException e) { throw context.invalid(STR."encoding='\{encoding}'"); }
                    if (context.skip(S) > 0) {
                        if (context.match("standalone=")) {
                            context.offset("standalone=".length());
                            standalone = scanValue(context, Tokenizer::isLowerLetter);
                            if (!standalone.equals("yes") && !standalone.equals("no"))
                                throw context.invalid(STR."standalone='\{standalone}'");
                            context.skip(S);
                        }
                    }
                }
                context.skip("?>");
            }
            default    -> throw new UnsupportedOperationException(STR."version: \{version}");
        }
        visitor.visitDeclaration(version, encoding, standalone);
        return charset;
    }
    
    @SneakyThrows
    private static void scanXMLBody(final Visitor visitor, final Context context) {
        final LinkedList<Tuple2<String, StringBuilder>> stack = { };
        final StringBuilder outerData = { };
        int c;
        context.skip(S);
        while (context.hasNext()) {
            if ((c = context.nextChar()) == '<') {
                switch (context.nextChar()) {
                    case '?' -> {
                        final String target = context.scanString(nameChecker(), 1);
                        if (target.equalsIgnoreCase("xml"))
                            throw context.invalid(STR."target: \{target}");
                        visitor.visitProcessingInstructions(target, context.scanString(it -> it != '?'));
                        context.skip("?>");
                    }
                    case '!' -> {
                        if (context.match("--")) {
                            context.offset(2);
                            visitor.visitComment(context.scanString(it -> true, 0, "--"));
                            context.skip(">");
                        } else if (context.match("<![CDATA["))
                            (!stack.isEmpty() ? stack.getFirst().v2 : outerData).append(context.scanString(it -> true, 0, "]]>"));
                        else
                            throw new UnsupportedOperationException();
                    }
                    case '/' -> {
                        if (stack.isEmpty())
                            throw context.invalid();
                        final Tuple2<String, StringBuilder> tag = stack.pop();
                        context.skip(tag.v1);
                        context.skip(">");
                        visitor.visitCharData(tag.v2.toString());
                        visitor.visitTagEnd(tag.v1);
                    }
                    default  -> {
                        final String tag = context.rollback().scanString(nameChecker(), 1);
                        final LinkedHashMap<String, String> attr = { };
                        final StringBuilder charData = { };
                        stack.push(Tuple.tuple(tag, charData));
                        while (context.skip(S) > 0) {
                            final String attrName = context.scanString(nameChecker());
                            if (!attrName.isEmpty()) {
                                context.skip("=");
                                final String attrValue = scanValue(context);
                                if (attr.put(attrName, attrValue) != null)
                                    throw context.invalid(STR."Repeated attr: \{attrName}='\{attrValue}'");
                            }
                        }
                        if (context.match("/>")) {
                            stack.pop();
                            context.offset(2);
                            visitor.visitEmptyTag(tag, attr);
                        } else {
                            context.skip(">");
                            visitor.visitTagBegin(tag, attr);
                        }
                    }
                }
                context.skip(S);
            } else if (!stack.isEmpty())
                stack.getFirst().v2.appendCodePoint(c);
            else
                outerData.appendCodePoint(c);
        }
        visitor.visitCharData(outerData.toString());
    }
    
    private static String scanKeyValue(final Context context, final String key, final IntPredicate checker) = scanValue(context.skip(key).skip("="), checker);
    
    private static String scanValue(final Context context, final IntPredicate checker) = switch (context.checkEOF().nextChar()) {
        case '"'  -> { try { yield context.scanString(checker); } finally { context.skip("\""); } }
        case '\'' -> { try { yield context.scanString(checker); } finally { context.skip("'"); } }
        default   -> throw context.invalid();
    };
    
    private static int nextChar(final Context context) {
        final int nextChar = context.nextChar();
        return switch (nextChar) {
            case '&' -> {
                final String entity = context.scanString(it -> it != ';');
                final @Nullable Integer represented = predefined[entity];
                if (represented == null)
                    throw context.invalid(STR."Entity: '\{entity}'");
                context.skip(";");
                yield represented;
            }
            case '<' -> throw context.invalid();
            default  -> nextChar;
        };
    }
    
    private static String scanValue(final Context context) = switch (context.checkEOF().nextChar()) {
        case '"'  -> { try { yield context.scanString(it -> it != '"' && it != '<', 0, null, () -> nextChar(context)); } finally { context.skip("\""); } }
        case '\'' -> { try { yield context.scanString(it -> it != '\'' && it != '<', 0, null, () -> nextChar(context)); } finally { context.skip("'"); } }
        default   -> throw context.invalid();
    };
    
}
