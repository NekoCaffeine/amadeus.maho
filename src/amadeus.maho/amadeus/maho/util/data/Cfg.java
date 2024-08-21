package amadeus.maho.util.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.LinkedList;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.dynamic.FieldsMap;
import amadeus.maho.util.language.parsing.Tokenizer;
import amadeus.maho.util.runtime.TypeHelper;
import amadeus.maho.util.type.TypeInferer;

@Getter
@RequiredArgsConstructor(AccessLevel.PRIVATE)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum Cfg implements Converter {
    
    instance;
    
    @Override
    @SneakyThrows
    public void read(final String source, final Object data, final Type type, final String debugInfo) throws IOException
            = scanCfgBody(data, type, Tokenizer.tokenization(source, debugInfo).root());
    
    @Override
    @SneakyThrows
    public void read(final InputStream input, final Object data, final Type type, final String debugInfo, final Charset charset) throws IOException
            = scanCfgBody(data, type, Tokenizer.tokenization(input.readString(charset), debugInfo).root());
    
    @SneakyThrows
    public void scanCfgBody(final Object data, final Type type, final Tokenizer.Context context) {
        final LinkedList<Object> deque = { };
        final LinkedList<Type> typesContext = { };
        deque << ArrayAgent.agent(data, type);
        final LinkedList<MethodHandle> setters = { };
        @Nullable Object layer = data;
        boolean array = false;
        int nextChar;
        while (context.hasNext()) {
            context.skip();
            if (context.hasNext()) {
                switch (nextChar = context.nextChar()) {
                    case '#'      -> {
                        context.skipAfter(c -> c != '\n');
                        continue;
                    }
                    case '}', ']' -> {
                        switch (nextChar) {
                            case ']' -> context.assertState(deque.peekLast() instanceof ArrayAgent);
                            case '}' -> context.assertState(!(deque.peekLast() instanceof ArrayAgent));
                        }
                        if (deque.size() > 1) {
                            final Object end = deque.pollLast();
                            typesContext.pollLast();
                            if ((layer = deque.peekLast()) instanceof ArrayAgent parentAgent) {
                                array = true;
                                parentAgent.append(end instanceof ArrayAgent agent ? agent.result() : end);
                            } else
                                setters.pollLast()?.invoke(layer, end instanceof ArrayAgent agent ? agent.result() : end);
                            continue;
                        } else
                            throw context.invalid();
                    }
                    default       -> context.rollback();
                }
                final @Nullable String key;
                if (!array) {
                    key = context.scanString(Tokenizer.javaIdentifierChecker(), 1);
                    context.skip();
                } else
                    key = null;
                switch (nextChar = context.checkEOF().nextChar()) {
                    case '='      -> {
                        if (key == null)
                            throw context.invalid();
                        context.skip();
                        final String value = (switch (context.checkEOF().nextChar()) {
                            case '"' -> {
                                final boolean p_flag[] = { false };
                                final String string = context.checkEOF().scanString(c -> c == '"' ? p_flag[0] : c == '\\' ? p_flag[0] = true : !(p_flag[0] = false));
                                context.skip("\"");
                                yield string;
                            }
                            default  -> context.rollback().scanString(c -> !Character.isWhitespace(c));
                        }).translateEscapes();
                        if (layer != null)
                            Converter.visitKeyValue(layer, key, value);
                    }
                    case '{', '[' -> {
                        final @Nullable Type nextType = switch (layer) {
                            case null             -> null;
                            case ArrayAgent agent -> agent.innerGenericType();
                            default               -> {
                                final @Nullable FieldsMap.Info info = FieldsMap.uniqueFieldsMapLocal()[layer.getClass()][key];
                                if (info == null)
                                    yield null;
                                setters << info.setter();
                                yield TypeInferer.infer(info.field().getGenericType(), typesContext.peekLast()!);
                            }
                        };
                        typesContext << nextType;
                        deque << (layer = nextType == null ? null : switch (nextChar) {
                            case '[' -> ArrayAgent.agent(nextType).let(context::assertNonnull);
                            case '{' -> TypeHelper.erase(nextType).tryInstantiationOrAllocate();
                            default  -> throw new IllegalStateException(STR."Unexpected value: \{nextChar}");
                        });
                    }
                    default       -> throw context.invalid();
                }
            } else
                break;
        }
        if (!(deque--).isEmpty())
            throw context.eof();
    }
    
    @Override
    public void write(final OutputStream output, final @Nullable Object data, final Charset charset) throws IOException { try (final OutputStreamWriter writer = { output, charset }) { write(writer, data, 0); } }
    
    @SneakyThrows
    private void write(final OutputStreamWriter writer, final @Nullable Object data, final int layer) throws IOException {
        if (data == null)
            writer.append("null");
        else {
            FieldsMap.uniqueFieldsMapLocal()[data.getClass()].forEach((name, info) -> {
                write(writer, info.field(), layer);
                final @Nullable Object value = info.getter().invoke(data);
                if (TypeHelper.isBasics(info.field().getType())) {
                    final String text = value.toString();
                    writer
                            .append("\t".repeat(layer))
                            .append(name)
                            .append(" = ")
                            .append(text.codePoints().anyMatch(Character::isWhitespace) || info.field().getType() == String.class ? '"' + text + '\"' : text)
                            .append("\n\n");
                } else {
                    writer
                            .append("\t".repeat(layer))
                            .append(name);
                    if (value != null) {
                        writer.append(" {\n\n");
                        write(writer, value, layer + 1);
                        writer.append("}\n\n");
                    } else
                        writer.append("null");
                }
            });
        }
    }
    
    private void write(final OutputStreamWriter writer, final Field field, final int layer) throws IOException {
        final @Nullable Comment comment = field.getAnnotation(Comment.class);
        if (comment != null)
            writer.append("\t".repeat(layer)).append("# ").append(comment.value()).append("\n");
    }
    
}
