package amadeus.maho.lang.javac.handler;

import java.io.IOException;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.jvm.Code;
import com.sun.tools.javac.jvm.Gen;
import com.sun.tools.javac.jvm.Items;
import com.sun.tools.javac.jvm.PoolWriter;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeCopier;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.BinaryMapping;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.javac.handler.base.BaseHandler;
import amadeus.maho.lang.javac.handler.base.Handler;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.dynamic.LookupHelper;
import amadeus.maho.util.serialization.BinaryMapper;
import amadeus.maho.util.serialization.Deserializable;
import amadeus.maho.util.serialization.Serializable;
import amadeus.maho.util.tuple.Tuple2;

import static amadeus.maho.lang.javac.handler.BinaryMappingHandler.PRIORITY;
import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.jvm.ByteCodes.*;

@TransformProvider
@NoArgsConstructor
@Handler(value = BinaryMapping.class, priority = PRIORITY)
public class BinaryMappingHandler extends BaseHandler<BinaryMapping> {
    
    public static final int PRIORITY = ReferenceHandler.PRIORITY >> 2;
    
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PUBLIC)
    public static class AfterReadMethodInvocation extends JCTree.JCMethodInvocation {
        
        EOFContext.Type type;
        
    }
    
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PUBLIC)
    public static class EOFContext {
        
        public enum Type {RETURN, MARK_RETURN, THROW}
        
        Type type;
        
        public Type type() {
            final Type type = this.type;
            if (type != Type.THROW)
                this.type = Type.THROW;
            return type;
        }
        
    }
    
    @Hook
    public static <P> Hook.Result visitMethodInvocation(final TreeCopier<P> $this, final MethodInvocationTree node, final P p) {
        if (node instanceof AfterReadMethodInvocation invocation) {
            final AfterReadMethodInvocation result = { $this.copy(invocation.typeargs, p), $this.copy(invocation.meth, p), $this.copy(invocation.args, p), invocation.type };
            result.pos = invocation.pos;
            return { result };
        }
        return Hook.Result.VOID;
    }
    
    @Privilege
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)))
    private static void visitApply(final Gen $this, final JCTree.JCMethodInvocation invocation) {
        if (invocation instanceof AfterReadMethodInvocation afterReadMethodInvocation) {
            final Items.Item result = $this.result;
            final Code code = $this.code;
            code.emitop0(dup);
            code.emitop0(iconst_m1);
            final Code.Chain branch = code.branch(if_icmpne);
            switch (afterReadMethodInvocation.type) {
                case THROW       -> {
                    final BinaryMappingHandler instance = instance(BinaryMappingHandler.class);
                    final Symbol eof = instance.finder.loadClass($this.env.toplevel.modle, instance.name(BinaryMapper.class.getCanonicalName())).members_field
                            .findFirst($this.names.fromString(BinaryMappingHandler.eof), symbol -> symbol instanceof Symbol.MethodSymbol methodSymbol && anyMatch(symbol.flags_field, STATIC) && methodSymbol.params.isEmpty());
                    code.emitInvokestatic(eof, eof.type);
                }
                case RETURN      -> code.emitop0(return_);
                case MARK_RETURN -> {
                    code.emitop0(aload_0);
                    code.emitop0(iconst_1);
                    final Symbol eofMark = $this.env.enclClass.sym.members_field.findFirst($this.names.fromString(BinaryMappingHandler.eofMark), symbol -> symbol.kind == Kinds.Kind.VAR && noneMatch(symbol.flags_field, STATIC));
                    code.emitop2(putfield, eofMark, PoolWriter::putMember);
                    code.emitop0(return_);
                }
            }
            code.resolve(branch);
            $this.result = result;
        }
    }
    
    public static final String serialization = "serialization", deserialization = "deserialization", read = "read", write = "write", eof = "eof", eofMark = "eofMark", offsetMark = "offsetMark";
    
    public Stream<JCTree.JCVariableDecl> fields(final JCTree.JCClassDecl tree) = tree.defs.stream()
            .filter(JCTree.JCVariableDecl.class::isInstance)
            .map(JCTree.JCVariableDecl.class::cast)
            .filter(field -> noneMatch(field.mods.flags, STATIC | TRANSIENT))
            .filter(this::nonGenerating);
    
    @Override
    public boolean shouldProcess(final boolean advance) = true;
    
    @Override
    public void processClass(final Env<AttrContext> env, final JCTree.JCClassDecl tree, final JCTree owner, final BinaryMapping annotation, final JCTree.JCAnnotation annotationTree, final boolean advance) {
        if (advance) {
            final Name eofMark = name(BinaryMappingHandler.eofMark), offsetMark = name(BinaryMappingHandler.offsetMark);
            if (annotation.eofMark()) {
                if (shouldInjectVariable(env, eofMark))
                    injectMember(env, maker.VarDef(maker.Modifiers(PUBLIC | TRANSIENT, List.of(maker.Annotation(IdentQualifiedName(Getter.class), List.nil()))), eofMark, maker.TypeIdent(TypeTag.BOOLEAN), null), advance);
                injectInterface(env, tree, BinaryMapper.EOFMark.class);
            }
            if (annotation.offsetMark()) {
                if (shouldInjectVariable(env, offsetMark))
                    injectMember(env, maker.VarDef(maker.Modifiers(PUBLIC | TRANSIENT, List.of(maker.Annotation(IdentQualifiedName(Getter.class), List.nil()))), offsetMark, maker.TypeIdent(TypeTag.LONG), null), advance);
                injectInterface(env, tree, BinaryMapper.OffsetMark.class);
            }
            injectInterface(env, tree, annotation.metadata() ? BinaryMapper.Metadata.class : BinaryMapper.class);
        } else {
            final boolean callSuper = annotation.callSuper() && types.isSubtype(env.enclClass.sym.getSuperclass(), symtab.enterClass(mahoModule, name(BinaryMapper.class)).type);
            final BinaryMapping.Endian endian = annotation.value();
            final boolean unsigned = annotation.unsigned();
            if (!annotation.metadata()) {
                final Name write = name(BinaryMappingHandler.write), outputStream = name(Serializable.Output.class), output = name("$output");
                if (shouldInjectMethod(env, write, outputStream))
                    injectMember(env, maker.MethodDef(maker.Modifiers(PUBLIC), write, maker.TypeIdent(TypeTag.VOID), List.nil(),
                            List.of(maker.VarDef(maker.Modifiers(FINAL | PARAMETER), output, IdentQualifiedName(Serializable.Output.class), null)), List.of(IdentQualifiedName(IOException.class)), maker.Block(0L, fields(tree)
                                    .filter(field -> !skipWrite(field, env))
                                    .map(field -> {
                                        List<JCTree.JCStatement> statements = write(maker.at(field.pos).Ident(output), maker.Ident(field.name), field.sym.type, bigEndian(endian, field, env), unsigned || unsigned(field, env), 0).collect(List.collector());
                                        if (field.init != null && !constant(field, env) && forWrite(field, env)) {
                                            statements = statements.prepend(maker.at(field.init.pos).Exec(maker.Assign(maker.Ident(field.name), field.init)));
                                            // noinspection DataFlowIssue
                                            field.init = null;
                                        }
                                        return maker.at(field.pos).Block(0L, statements);
                                    })
                                    .collect(List.<JCTree.JCStatement>collector())
                                    .prependList(callSuper ? List.of(maker.Block(0L, List.of(maker.Exec(maker.Apply(List.nil(), maker.Select(maker.Ident(names._super), write), List.of(maker.Ident(output))))))) : List.nil())), null));
            }
            maker.at(annotationTree.pos);
            {
                final Name read = name(BinaryMappingHandler.read), inputStream = name(Deserializable.Input.class), input = name("$input");
                if (shouldInjectMethod(env, read, inputStream)) {
                    final int p_index[] = { -1 };
                    injectMember(env, maker.MethodDef(maker.Modifiers(PUBLIC), read, maker.TypeIdent(TypeTag.VOID), List.nil(),
                            List.of(maker.VarDef(maker.Modifiers(FINAL | PARAMETER), input, IdentQualifiedName(Deserializable.Input.class), null)), List.of(IdentQualifiedName(IOException.class)), maker.Block(0L, fields(tree)
                                    .filter(field -> !skipRead(field, env))
                                    .map(field -> {
                                        final boolean constant = constant(field, env);
                                        final List<JCTree.JCStatement> assign;
                                        if (constant || field.init == null || forWrite(field, env))
                                            assign = List.nil();
                                        else {
                                            assign = List.of(maker.at(field.init.pos).Exec(maker.Assign(maker.Ident(field.name), field.init)));
                                            // noinspection DataFlowIssue
                                            field.init = null;
                                        }
                                        final EOFContext context = { annotation.eofMark() && ++p_index[0] == 0 ? EOFContext.Type.MARK_RETURN : optional(field, env) ? EOFContext.Type.RETURN : EOFContext.Type.THROW };
                                        return maker.at(field.pos).Block(0L, assign.appendList(read(maker.Ident(input), maker.Ident(field.name).let(it -> it.type = field.sym.type),
                                                field.sym.type, bigEndian(endian, field, env), unsigned || unsigned(field, env), constant, 0, context).collect(List.collector())));
                                    })
                                    .collect(List.<JCTree.JCStatement>collector())
                                    .prependList(callSuper ? List.of(maker.Block(0L, List.of(maker.Exec(maker.Apply(List.nil(), maker.Select(maker.Ident(names._super), read), List.of(maker.Ident(input))))))) : List.nil())), null));
                }
            }
        }
    }
    
    @SneakyThrows
    protected static final String
            floatToIntBits   = LookupHelper.method1(Float::floatToIntBits).getName(),
            doubleToLongBits = LookupHelper.method1(Double::doubleToLongBits).getName(),
            intBitsToFloat   = LookupHelper.method1(Float::intBitsToFloat).getName(),
            longBitsToDouble = LookupHelper.method1(Double::longBitsToDouble).getName(),
            getBytes         = LookupHelper.<String, String, byte[]>method2(String::getBytes).getName();
    
    protected boolean bigEndian(final BinaryMapping.Endian endian, final JCTree.JCVariableDecl field, final Env<AttrContext> env)
            = marker.getAnnotationsByType(field.mods, env, BinaryMapping.Mark.class).stream().map(Tuple2::v1).findFirst().map(BinaryMapping.Mark::value).orElse(endian) == BinaryMapping.Endian.BIG;
    
    protected boolean unsigned(final JCTree.JCVariableDecl field, final Env<AttrContext> env) = hasAnnotation(field.mods, env, BinaryMapping.Unsigned.class);
    
    protected boolean constant(final JCTree.JCVariableDecl field, final Env<AttrContext> env) = hasAnnotation(field.mods, env, BinaryMapping.Constant.class);
    
    protected boolean forWrite(final JCTree.JCVariableDecl field, final Env<AttrContext> env) = hasAnnotation(field.mods, env, BinaryMapping.ForWrite.class);
    
    protected boolean skipRead(final JCTree.JCVariableDecl field, final Env<AttrContext> env) = hasAnnotation(field.mods, env, BinaryMapping.SkipRead.class);
    
    protected boolean skipWrite(final JCTree.JCVariableDecl field, final Env<AttrContext> env) = hasAnnotation(field.mods, env, BinaryMapping.SkipWrite.class);
    
    protected boolean optional(final JCTree.JCVariableDecl field, final Env<AttrContext> env) = hasAnnotation(field.mods, env, BinaryMapping.Optional.class);
    
    protected int size(final int size, final boolean unsigned) = unsigned ? size >> 1 : size;
    
    protected UnaryOperator<JCTree.JCExpression> method(final JCTree.JCExpression method) = it -> maker.Apply(List.nil(), method, List.of(it));
    
    protected UnaryOperator<JCTree.JCExpression> binary(final JCTree.Tag tag, final int n) = it -> maker.Binary(tag, it, maker.Literal(TypeTag.INT, n));
    
    protected JCTree.JCStatement write(final JCTree.JCExpression output, final JCTree.JCExpression field, final Function<JCTree.JCExpression, JCTree.JCExpression> transformer)
            = maker.Exec(maker.Apply(List.nil(), maker.Select(output, name("write")), List.of(transformer.apply(field))));
    
    
    protected Stream<JCTree.JCStatement> writeNByte(final JCTree.JCExpression output, final JCTree.JCExpression field, final int n, final boolean bigEndian,
            final UnaryOperator<JCTree.JCExpression> transformer = UnaryOperator.identity())
            = switch (n) {
        case 1  -> Stream.of(write(output, field, Function.identity()));
        default -> {
            final JCTree.JCIdent local = maker.Ident(name("$local"));
            yield Stream.concat(Stream.of(maker.VarDef(maker.Modifiers(FINAL), local.name, maker.Type(field.type), transformer.apply(field))), IntStream.range(0, n)
                    .mapToObj(i -> write(output, local, binary(JCTree.Tag.USR, (bigEndian ? n - 1 - i : i) << 3).andThen(it -> maker.TypeCast(symtab.intType, it)))));
        }
    };
    
    protected Stream<JCTree.JCStatement> writeArray(final JCTree.JCExpression output, final JCTree.JCExpression field, final Type type, final boolean bigEndian, final boolean unsigned, final int layer) = switch (type.getTag()) {
        case BYTE -> Stream.of(write(output, field, Function.identity()));
        default   -> {
            final JCTree.JCIdent element = maker.Ident(name(STR."$element_\{layer}"));
            yield Stream.of(maker.ForeachLoop(maker.VarDef(maker.Modifiers(FINAL), element.name, maker.Type(element.type = type), null),
                    field, maker.Block(0L, write(output, element, type, bigEndian, unsigned, layer + 1).collect(List.collector()))));
        }
    };
    
    protected Stream<JCTree.JCStatement> write(final JCTree.JCExpression output, final JCTree.JCExpression field, final Type type, final boolean bigEndian, final boolean unsigned, final int layer) = switch (type.getTag()) {
        case BYTE    -> writeNByte(output, field, 1, bigEndian);
        case SHORT   -> writeNByte(output, field, size(2, unsigned), bigEndian);
        case INT     -> writeNByte(output, field, size(4, unsigned), bigEndian);
        case LONG    -> writeNByte(output, field, size(8, unsigned), bigEndian);
        case FLOAT   -> writeNByte(output, field, 4, bigEndian, method(maker.Select(maker.Ident(types.boxedClass(symtab.floatType)), name(floatToIntBits))));
        case DOUBLE  -> writeNByte(output, field, 8, bigEndian, method(maker.Select(maker.Ident(types.boxedClass(symtab.doubleType)), name(doubleToLongBits))));
        case BOOLEAN -> writeNByte(output, field, 1, bigEndian, it -> maker.Conditional(it, maker.Literal(TypeTag.BYTE, 1), maker.Literal(TypeTag.BYTE, 0)));
        case CHAR    -> writeNByte(output, field, 1, bigEndian, it -> maker.TypeCast(symtab.intType, it));
        case ARRAY   -> writeArray(output, field, ((Type.ArrayType) type).elemtype, bigEndian, unsigned, layer);
        case CLASS   -> Stream.of(maker.Exec(maker.Apply(List.nil(), maker.Select(maker.Parens(maker.TypeCast(IdentQualifiedName(BinaryMapper.class), field)), name(serialization)), List.of(output))));
        default      -> throw new IllegalArgumentException(STR."type: \{type}");
    };
    
    protected JCTree.JCExpression read(final JCTree.JCExpression input, final Function<JCTree.JCExpression, JCTree.JCExpression> transformer, final EOFContext.Type type)
            = transformer.apply(new AfterReadMethodInvocation(List.nil(), maker.Select(input, name("read")), List.nil(), type).let(it -> it.pos = maker.pos));
    
    protected Stream<JCTree.JCStatement> readNBytes(final JCTree.JCExpression input, final int n, final boolean bigEndian,
            final Function<JCTree.JCExpression, JCTree.JCStatement> transformer, final EOFContext context, final boolean castLong = false)
            = Stream.of(transformer.apply(switch (n) {
        case 1  -> read(input, Function.identity(), context.type());
        default -> IntStream.range(0, n)
                .mapToObj(i -> read(input, (castLong ? (UnaryOperator<JCTree.JCExpression>) it -> maker.TypeCast(maker.TypeIdent(TypeTag.LONG), it) : UnaryOperator.<JCTree.JCExpression>identity())
                        .andThen(binary(JCTree.Tag.SL, (bigEndian ? n - 1 - i : i) << 3)), context.type()))
                .reduce((a, b) -> maker.Binary(JCTree.Tag.BITOR, a, b))
                .orElseThrow();
    }));
    
    protected Stream<JCTree.JCStatement> readArray(final JCTree.JCExpression input, final JCTree.JCExpression field, final Type type,
            final boolean bigEndian, final boolean unsigned, final boolean constant, final int layer, final EOFContext context) {
        final EOFContext.Type firstType = context.type;
        if (firstType != EOFContext.Type.THROW)
            maker.If(maker.Binary(JCTree.Tag.LT, maker.Literal(0), maker.Select(field, names.length)),
                    maker.Block(0L, read(input, maker.Indexed(field, maker.Literal(0)).let(it -> it.type = type), type, bigEndian, unsigned, constant, layer + 1, context).collect(List.collector())), null);
        final Name index = name(STR."$index_\{layer}");
        return Stream.of(maker.ForLoop(List.of(maker.VarDef(maker.Modifiers(0L), index, maker.TypeIdent(TypeTag.INT), maker.Literal(TypeTag.INT, firstType != EOFContext.Type.THROW ? 1 : 0))),
                maker.Binary(JCTree.Tag.LT, maker.Ident(index), maker.Select(field, names.length)), List.of(maker.Exec(maker.Unary(JCTree.Tag.POSTINC, maker.Ident(index)))),
                maker.Block(0L, read(input, maker.Indexed(field, maker.Ident(index)).let(it -> it.type = type), type, bigEndian, unsigned, constant, layer + 1, context).collect(List.collector()))));
    }
    
    protected Function<JCTree.JCExpression, JCTree.JCStatement> afterRead(final JCTree.JCExpression target, final boolean constant, final Function<JCTree.JCExpression, JCTree.JCExpression> transformer = Function.identity()) = constant ?
            it -> maker.If(maker.Binary(JCTree.Tag.NE, target, maker.TypeCast(target.type, transformer.apply(it))), maker.Throw(maker.NewClass(null, List.nil(), maker.Type(symtab.illegalArgumentExceptionType), List.nil(), null)), null) :
            it -> maker.Exec(maker.Assign(target, maker.TypeCast(target.type, transformer.apply(it))));
    
    protected Stream<JCTree.JCStatement> read(final JCTree.JCExpression input, final JCTree.JCExpression field, final Type type,
            final boolean bigEndian, final boolean unsigned, final boolean constant, final int layer, final EOFContext context) = switch (type.getTag()) {
        case BYTE    -> readNBytes(input, 1, bigEndian, afterRead(field, constant), context);
        case SHORT   -> readNBytes(input, size(2, unsigned), bigEndian, afterRead(field, constant), context);
        case INT     -> readNBytes(input, size(4, unsigned), bigEndian, afterRead(field, constant), context);
        case LONG    -> readNBytes(input, size(8, unsigned), bigEndian, afterRead(field, constant), context, true);
        case FLOAT   -> readNBytes(input, 4, bigEndian, afterRead(field, constant, method(maker.Select(maker.Ident(types.boxedClass(symtab.floatType)), name(intBitsToFloat)))), context);
        case DOUBLE  -> readNBytes(input, 8, bigEndian, afterRead(field, constant, method(maker.Select(maker.Ident(types.boxedClass(symtab.doubleType)), name(longBitsToDouble)))), context, true);
        case BOOLEAN -> readNBytes(input, 1, bigEndian, afterRead(field, constant, it -> maker.Conditional(it, maker.Literal(TypeTag.BYTE, 1), maker.Literal(TypeTag.BYTE, 0))), context);
        case CHAR    -> readNBytes(input, 1, bigEndian, afterRead(field, constant), context);
        case ARRAY   -> readArray(input, field, ((Type.ArrayType) type).elemtype, bigEndian, unsigned, constant, layer, context);
        case CLASS   -> Stream.of(maker.Exec(maker.Apply(List.nil(), maker.Select(maker.Parens(maker.TypeCast(IdentQualifiedName(BinaryMapper.class), field)), name(deserialization)), List.of(input))));
        default      -> throw new IllegalArgumentException(STR."type: \{type}");
    };
    
}
