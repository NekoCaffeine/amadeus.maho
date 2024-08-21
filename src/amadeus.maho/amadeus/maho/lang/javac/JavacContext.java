package amadeus.maho.lang.javac;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.internal.misc.Unsafe;
import jdk.jshell.CompletenessAnalyzer;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;

import com.sun.tools.javac.code.AnnoConstruct;
import com.sun.tools.javac.code.ClassFinder;
import com.sun.tools.javac.code.Lint;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import com.sun.tools.javac.comp.Annotate;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Check;
import com.sun.tools.javac.comp.ConstFold;
import com.sun.tools.javac.comp.DeferredAttr;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.InferenceContext;
import com.sun.tools.javac.comp.MemberEnter;
import com.sun.tools.javac.comp.Modules;
import com.sun.tools.javac.comp.Operators;
import com.sun.tools.javac.comp.Resolve;
import com.sun.tools.javac.comp.TypeEnter;
import com.sun.tools.javac.comp.TypeEnvs;
import com.sun.tools.javac.jvm.ByteCodes;
import com.sun.tools.javac.jvm.Code;
import com.sun.tools.javac.jvm.Gen;
import com.sun.tools.javac.jvm.Items;
import com.sun.tools.javac.jvm.PoolConstant;
import com.sun.tools.javac.parser.JavaTokenizer;
import com.sun.tools.javac.parser.JavacParser;
import com.sun.tools.javac.parser.Tokens;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.Pretty;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.util.Warner;

import amadeus.maho.core.MahoExport;
import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.Default;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Callback;
import amadeus.maho.lang.inspection.ConstructorContract;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.inspection.TestOnly;
import amadeus.maho.lang.javac.handler.base.AnnotationProxyMaker;
import amadeus.maho.lang.javac.handler.base.HandlerSupport;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.Proxy;
import amadeus.maho.transform.mark.Redirect;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.InvisibleType;
import amadeus.maho.transform.mark.base.Slice;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.control.LinkedIterator;
import amadeus.maho.util.dynamic.EnumHelper;
import amadeus.maho.util.function.FunctionHelper;
import amadeus.maho.util.runtime.ArrayHelper;
import amadeus.maho.util.runtime.DebugHelper;
import amadeus.maho.util.runtime.MethodHandleHelper;
import amadeus.maho.util.throwable.BreakException;
import amadeus.maho.util.tuple.Tuple;
import amadeus.maho.util.tuple.Tuple2;
import amadeus.maho.util.tuple.Tuple3;

import static amadeus.maho.util.bytecode.Bytecodes.NEW;
import static amadeus.maho.util.runtime.ObjectHelper.requireNonNull;
import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.tree.JCTree.Tag.*;
import static org.objectweb.asm.Opcodes.*;

@ConstructorContract(@ConstructorContract.Parameters(Context.class))
@TransformProvider
@FieldDefaults(level = AccessLevel.PUBLIC)
public class JavacContext {
    
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class SignatureGenerator extends Types.SignatureGenerator {
        
        public static final Context.Key<SignatureGenerator> signatureGeneratorKey = { };
        
        public static SignatureGenerator instance(final Context context) = context.get(signatureGeneratorKey) ?? new SignatureGenerator(context);
        
        public SignatureGenerator(final Context context) {
            super(Types.instance(context));
            context.put(signatureGeneratorKey, this);
        }
        
        StringBuilder builder = { 1 << 6 };
        
        @Override
        protected void append(final char c) = builder.append(c);
        
        @Override
        protected void append(final byte bytes[]) = builder.append(new String(bytes));
        
        @Override
        protected void append(final Name name) = builder.append(name);
        
        @Override
        public String toString() = builder.toString();
        
        public self reset() = builder.setLength(0);
        
        public String signature(final Type type) {
            reset().assembleSig(type);
            return toString();
        }
        
    }
    
    @NoArgsConstructor
    public static class DynamicMethodSymbol extends Symbol.DynamicMethodSymbol {
        
        @Override
        public Tuple3<Object, Object, Object> poolKey(final Types types) = { bsmKey(types), name.toString(), dynamicType().poolKey(types) };
        
    }
    
    @TransformProvider
    @NoArgsConstructor
    public static class DynamicVarSymbol extends Symbol.DynamicVarSymbol {
        
        @Hook
        private static Hook.Result visitIdent(final Attr $this, final JCTree.JCIdent ident) {
            if (ident.sym instanceof JavacContext.DynamicVarSymbol) {
                (Privilege) ($this.result = ident.sym.type);
                return Hook.Result.NULL;
            }
            return Hook.Result.VOID;
        }
        
        @Redirect(targetClass = Items.DynamicItem.class, slice = @Slice(@At(method = @At.MethodInsn(name = "emitLdc"))))
        private static void load(final Code code, final DynamicVarSymbol member) {
            switch (member.type.getTag()) {
                case LONG,
                     DOUBLE -> code.emitop2(ByteCodes.ldc2w, member, (writer, constant) -> (Privilege) writer.putConstant(constant));
                default     -> code.emitLdc(member);
            }
        }
        
        @Override
        public Tuple3<Object, Object, Object> poolKey(final Types types) = { bsmKey(types), name.toString(), dynamicType().poolKey(types) };
        
        public JCTree.JCIdent ident(final TreeMaker maker) = maker.Ident(this).let(it -> it.sym = this);
        
        public JCTree.JCFieldAccess select(final TreeMaker maker, final String name) = maker.Select(ident(maker), this.name.table.fromString(name));
        
        public JCTree.JCMethodInvocation invocation(final TreeMaker maker, final String name, final JCTree.JCExpression... expressions) = maker.Apply(List.nil(), select(maker, name), List.from(expressions));
        
    }
    
    @Getter
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class NestedCheckContext implements Check.CheckContext {
        
        Check.CheckContext context;
        
        public boolean compatible(final Type found, final Type req, final Warner warn) = context.compatible(found, req, warn);
        
        public void report(final JCDiagnostic.DiagnosticPosition pos, final JCDiagnostic details) = context.report(pos, details);
        
        public Warner checkWarner(final JCDiagnostic.DiagnosticPosition pos, final Type found, final Type req) = context.checkWarner(pos, found, req);
        
        public InferenceContext inferenceContext() = context.inferenceContext();
        
        public DeferredAttr.DeferredAttrContext deferredAttrContext() = context.deferredAttrContext();
        
    }
    
    @TransformProvider
    @NoArgsConstructor
    public static class MarkAnnotation extends JCTree.JCAnnotation {
        
        @Hook
        private static Hook.Result annotateNow(final Annotate $this, final Symbol toAnnotate, @Hook.Reference List<JCAnnotation> withAnnotations, final Env<AttrContext> env,
                final boolean typeAnnotations, final boolean isTypeParam) {
            if (typeAnnotations)
                withAnnotations = withAnnotations.stream()
                        .filter(it -> {
                            if (it instanceof MarkAnnotation) {
                                $this.attributeTypeAnnotation(it, ((Privilege) $this.syms).annotationType, env);
                                return false;
                            }
                            return true;
                        })
                        .collect(List.collector());
            return { };
        }
        
    }
    
    @RequiredArgsConstructor(AccessLevel.PROTECTED)
    @FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
    public static class TreeTranslator extends com.sun.tools.javac.tree.TreeTranslator {
        
        Map<JCTree, JCTree> mapping;
        
        @Getter
        int debugCount[] = { 0 };
        
        @Override
        public <T extends JCTree> @Nullable T translate(final @Nullable T tree) {
            if (tree == null)
                return null;
            final T value = (T) mapping.getOrDefault(tree, tree);
            if (value != tree)
                debugCount[0]++;
            return value != tree ? value : super.translate(tree);
        }
        
        @Override
        public void visitErroneous(final JCTree.JCErroneous tree) {
            tree.errs = translate(tree.errs);
            result = tree;
        }
        
        public static void translate(final Map<? extends JCTree, ? extends JCTree> mapping, final boolean debug = false, final JCTree... trees) {
            final TreeTranslator translator = { new IdentityHashMap<>(mapping) };
            Stream.of(trees).forEach(translator::translate);
            if (debug && mapping.entrySet().stream().anyMatch(entry -> entry.getKey() != entry.getValue()) && translator.debugCount[0] == 0)
                DebugHelper.breakpoint();
        }
        
        public static JCTree upper(final Env<?> env, final JCTree tree) {
            Env<?> next = env.next;
            while (next.tree == tree)
                next = next.next;
            return next.tree;
        }
        
    }
    
    @RequiredArgsConstructor
    public static class TypeMapping extends Type.StructuralTypeMapping<Void> {
        
        public final Map<Type, Type> mapping;
        
        @Override
        public Type visitType(final Type type, final Void aVoid) = mapping.getOrDefault(type, type);
        
    }
    
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    public static class ClassAnnotationFinder extends ClassVisitor {
        
        String annotationTypes[];
        
        @Nullable
        Function<Class<? extends Annotation>, AnnotationVisitor> mapper;
        
        Map<String, Class<? extends Annotation>> cache = new HashMap<>();
        
        @SafeVarargs
        public ClassAnnotationFinder(final @Nullable Function<Class<? extends Annotation>, AnnotationVisitor> mapper = null, final Class<? extends Annotation>... annotationTypes) {
            super(MahoExport.asmAPIVersion());
            this.mapper = mapper;
            this.annotationTypes = Stream.of(annotationTypes).map(clazz -> {
                final String desc = ASMHelper.classDesc(clazz);
                cache[desc] = clazz;
                return desc;
            }).toArray(String[]::new);
        }
        
        @Override
        public @Nullable AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
            for (final String annotationType : annotationTypes)
                if (descriptor.equals(annotationType))
                    if (mapper != null)
                        return mapper.apply(cache[descriptor]);
                    else
                        throw BreakException.BREAK;
            return null;
        }
        
        @SafeVarargs
        public static boolean hasAnnotations(final ClassReader reader, final Class<? extends Annotation>... annotationTypes) {
            try {
                reader.accept(new ClassAnnotationFinder(annotationTypes), ClassReader.SKIP_CODE);
                return false;
            } catch (final BreakException ignored) { return true; }
        }
        
        @SafeVarargs
        public static void process(final ClassReader reader, final Function<Class<? extends Annotation>, AnnotationVisitor> mapper, final Class<? extends Annotation>... annotationTypes)
            = reader.accept(new ClassAnnotationFinder(mapper, annotationTypes), ClassReader.SKIP_CODE);
        
        @SafeVarargs
        public static void processVoid(final ClassReader reader, final Consumer<Class<? extends Annotation>> consumer, final Class<? extends Annotation>... annotationTypes) = process(reader, annotationType -> {
            consumer.accept(annotationType);
            return null;
        }, annotationTypes);
        
    }
    
    @FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
    public static class MembersScope extends Scope {
        
        Scope scope;
        
        Predicate<Symbol> filter;
        
        public MembersScope(final Scope scope, final Predicate<Symbol> filter) {
            super(scope.owner);
            this.scope = scope;
            this.filter = filter;
        }
        
        protected Predicate<Symbol> combine(final Predicate<Symbol> filter) = symbol -> this.filter.test(symbol) && (filter == null || filter.test(symbol));
        
        @Override
        public Iterable<Symbol> getSymbols(final Predicate<Symbol> filter, final LookupKind lookupKind) = scope.getSymbols(combine(filter), lookupKind);
        
        @Override
        public Iterable<Symbol> getSymbolsByName(final Name name, final Predicate<Symbol> filter, final LookupKind lookupKind) = scope.getSymbolsByName(name, combine(filter), lookupKind);
        
        @Override
        public Scope getOrigin(final Symbol symbol) = scope.getOrigin(symbol);
        
        @Override
        public boolean isStaticallyImported(final Symbol symbol) = scope.isStaticallyImported(symbol);
        
    }
    
    public static class StaticMembersScope extends MembersScope {
        
        public StaticMembersScope(final Scope scope) = super(scope, symbol -> JavacContext.anyMatch(symbol.flags(), STATIC));
        
    }
    
    public static class FlowControlException extends RuntimeException {
        
        @Override
        public Throwable fillInStackTrace() = this;
        
    }
    
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PUBLIC)
    public static class ReAttrException extends FlowControlException {
        
        @Default
        Runnable runnable = FunctionHelper.nothing();
        
        @Default
        boolean needAttr = true;
        
        @Default
        Consumer<JCTree> consumer = FunctionHelper.abandon();
        
        JCTree tree, breakTree;
        
    }
    
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PUBLIC)
    public static class ReLowException extends FlowControlException {
        
        JCTree tree, breakTree;
        
    }
    
    // Used to add new operators
    @SuppressWarnings("SpellCheckingInspection")
    @TransformProvider
    public static class AdditionalOperators {
        
        // # hack lexical analysis
        
        @Proxy(NEW)
        public static native Tokens.TokenKind newTokenKind(String name, int id, String string);
        
        public static final int nextTokenKindId[] = { Tokens.TokenKind.values().length };
        
        public static final Tokens.TokenKind
                KIND_NULL_OR       = newTokenKind("NULL_OR", nextTokenKindId[0]++, "??"),
                KIND_SAFE_ACCESS   = newTokenKind("SAFE_ACCESS", nextTokenKindId[0]++, "?."),
                KIND_ASSERT_ACCESS = newTokenKind("ASSERT_ACCESS", nextTokenKindId[0]++, "!.");
        
        public static final ArrayList<Tokens.TokenKind> KINDS = { };
        
        public static void addTokenKinds(final Tokens.TokenKind... kinds) {
            EnumHelper.addEnum(kinds);
            KINDS.addAll(List.from(kinds));
        }
        
        @Hook
        private static Hook.Result lookupKind(final Tokens $this, final Name name) = lookupKind($this, name.toString());
        
        @Hook
        private static Hook.Result lookupKind(final Tokens $this, final String name) = Hook.Result.nullToVoid(KINDS.stream().filter(it -> it.name.equals(name)).findFirst().orElse(null));
        
        @Hook
        private static Hook.Result isSpecial(final JavaTokenizer $this, final char ch) = Hook.Result.falseToVoid(ch == '.');
        
        // # hack jshell
        
        public static final String TK = "jdk.jshell.CompletenessAnalyzer$TK", TK_ARRAY = "[L" + TK + ";";
        
        public static int XEXPR = (Privilege) CompletenessAnalyzer.XEXPR, XTERM = (Privilege) CompletenessAnalyzer.XTERM;
        
        @Proxy(value = INVOKESTATIC, target = TK)
        private static native Enum @InvisibleType(TK_ARRAY) [] values();
        
        @Proxy(NEW)
        public static native @InvisibleType(TK) Enum newTK(String name, int id, Tokens.TokenKind kind, int b);
        
        public static final int nextTKId[] = { values().length };
        
        public static final @InvisibleType(TK) Enum
                TK_NULL_OR            = newTK("NULL_OR", nextTKId[0]++, KIND_NULL_OR, XEXPR),
                TK_SAFE_ACCESS        = newTK("SAFE_ACCESS", nextTKId[0]++, KIND_SAFE_ACCESS, XEXPR),
                TK_ASSERT_ACCESS      = newTK("ASSERT_ACCESS", nextTKId[0]++, KIND_ASSERT_ACCESS, XEXPR),
                TK_POST_ASSERT_ACCESS = newTK("POST_ASSERT_ACCESS", nextTKId[0]++, Tokens.TokenKind.BANG, XEXPR | XTERM);
        
        public static final Map<Tokens.TokenKind, @InvisibleType(TK) Enum> TK_MAP = new HashMap<>();
        
        public static void addTKs(final Map<Tokens.TokenKind, @InvisibleType(TK) Enum> tkMapping) {
            EnumHelper.addEnum(tkMapping.values().toArray(Enum[]::new));
            TK_MAP.putAll(tkMapping);
        }
        
        @Redirect(slice = @Slice(@At(method = @At.MethodInsn(name = "get"))), target = TK)
        private static @InvisibleType(TK) Object tokenKindToTK(final EnumMap<Tokens.TokenKind, Object> map, final Tokens.TokenKind kind) {
            // noinspection ConstantValue
            if (TK_MAP == null) // jdk.jshell.CompletenessAnalyzer$TK#<clinit>
                return map.get(kind);
            final @Nullable @InvisibleType(TK) Enum result = TK_MAP[kind];
            return result == null ? requireNonNull(map[kind]) : result;
        }
        
        // # hack ast tag
        
        @Proxy(NEW)
        public static native JCTree.Tag newTag(String name, int id);
        
        public static final int nextTagId[] = { JCTree.Tag.values().length };
        
        public static final JCTree.Tag
                TAG_NULL_OR            = newTag("NULL_OR", nextTagId[0]++),
                TAG_SAFE_ACCESS        = newTag("SAFE_ACCESS", nextTagId[0]++),
                TAG_ASSERT_ACCESS      = newTag("ASSERT_ACCESS", nextTagId[0]++),
                TAG_POST_ASSERT_ACCESS = newTag("POST_ASSERT_ACCESS", nextTagId[0]++);
        
        public static final Map<Tokens.TokenKind, JCTree.Tag> KIND_TO_TAG_MAP = new HashMap<>();
        public static final Map<JCTree.Tag, Tokens.TokenKind> TAG_TO_KIND_MAP = new HashMap<>();
        public static final Map<JCTree.Tag, Integer>          TAG_PREC_MAP    = new HashMap<>();
        
        public static void addOperatorTags(final Map<JCTree.Tag, Tuple2<Tokens.TokenKind, Integer>> tagMapping) = tagMapping.forEach((tag, tuple) -> {
            KIND_TO_TAG_MAP.put(tuple.v1, tag);
            TAG_TO_KIND_MAP.put(tag, tuple.v1);
            TAG_PREC_MAP.put(tag, tuple.v2);
        });
        
        @Hook(value = TreeInfo.class, isStatic = true)
        private static Hook.Result opPrec(final JCTree.Tag op) = Hook.Result.nullToVoid(TAG_PREC_MAP[op]);
        
        @Hook(value = JavacParser.class, isStatic = true)
        private static Hook.Result optag(final Tokens.TokenKind kind) = Hook.Result.nullToVoid(KIND_TO_TAG_MAP[kind]);
        
        @Hook
        private static Hook.Result operatorName(final Operators $this, final JCTree.Tag tag) = Hook.Result.nullToVoid(Optional.ofNullable(TAG_TO_KIND_MAP.get(tag)).map(kind -> instance().name(kind.name)).orElse(null));
        
        @Hook
        private static Hook.Result operatorName(final Pretty $this, final JCTree.Tag tag) = Hook.Result.nullToVoid(Optional.ofNullable(TAG_TO_KIND_MAP.get(tag)).map(kind -> kind.name).orElse(null));
        
        // # add new operator
        
        static {
            addTokenKinds(KIND_NULL_OR, KIND_SAFE_ACCESS, KIND_ASSERT_ACCESS);
            addTKs(Map.of(KIND_NULL_OR, TK_NULL_OR, KIND_SAFE_ACCESS, TK_SAFE_ACCESS, KIND_ASSERT_ACCESS, TK_ASSERT_ACCESS, Tokens.TokenKind.BANG, TK_POST_ASSERT_ACCESS));
            addOperatorTags(Map.of(TAG_NULL_OR, Tuple.tuple(KIND_NULL_OR, TreeInfo.prefixPrec), TAG_POST_ASSERT_ACCESS, Tuple.tuple(Tokens.TokenKind.BANG, TreeInfo.postfixPrec)));
            EnumHelper.addEnum(TAG_NULL_OR, TAG_SAFE_ACCESS, TAG_ASSERT_ACCESS, TAG_POST_ASSERT_ACCESS);
        }
        
    }
    
    public static class OperatorData {
        
        public static final HashMap<String, String> operatorSymbol2operatorName = { };
        
        public static final HashMap<JCTree.Tag, String> operatorType2operatorName = { };
        
        static {
            // @formatter:off
            add("PLUS"    , POS       , "+"   );
            add("MINUS"   , NEG       , "-"   );
            add("NOT"     , NOT       , "!"   );
            add("TILDE"   , COMPL     , "~"   );
            add("PREINC"  , PREINC    , "++_" );
            add("PREDEC"  , PREDEC    , "--_" );
            add("POSTINC" , POSTINC   , "_++" );
            add("POSTDEC" , POSTDEC   , "_--" );
            add("OROR"    , OR        , "||"  );
            add("ANDAND"  , AND       , "&&"  );
            add("OR"      , BITOR     , "|"   );
            add("XOR"     , BITXOR    , "^"   );
            add("AND"     , BITAND    , "&"   );
            add("EQ"      , EQ        , "=="  );
            add("NE"      , NE        , "!="  );
            add("LT"      , LT        , "<"   );
            add("GT"      , GT        , ">"   );
            add("LE"      , LE        , "<="  );
            add("GE"      , GE        , ">="  );
            add("LTLT"    , SL        , "<<"  );
            add("GTGT"    , SR        , ">>"  );
            add("GTGTGT"  , USR       , ">>>" );
            add("PLUS"    , PLUS      , "+"   );
            add("MINUS"   , MINUS     , "-"   );
            add("MUL"     , MUL       , "*"   );
            add("DIV"     , DIV       , "/"   );
            add("MOD"     , MOD       , "%"   );
            add("OREQ"    , BITOR_ASG , "|="  );
            add("XOREQ"   , BITXOR_ASG, "^="  );
            add("ANDEQ"   , BITAND_ASG, "&="  );
            add("LTLTEQ"  , SL_ASG    , "<<=" );
            add("GTGTEQ"  , SR_ASG    , ">>=" );
            add("GTGTGTEQ", USR_ASG   , ">>>=");
            add("PLUSEQ"  , PLUS_ASG  , "+="  );
            add("MINUSEQ" , MINUS_ASG , "-="  );
            add("MULEQ"   , MUL_ASG   , "*="  );
            add("DIVEQ"   , DIV_ASG   , "/="  );
            add("MODEQ"   , MOD_ASG   , "%="  );
            // @formatter:on
        }
        
        public static void add(final String name, final JCTree.Tag type, final String symbol) {
            operatorSymbol2operatorName[symbol] = name;
            operatorType2operatorName[type] = name;
        }
        
    }
    
    private static final ThreadLocal<JavacContext> contextLocal = { };
    
    public static @Nullable JavacContext instanceMayNull() = contextLocal.get();
    
    public static JavacContext instance() = requireNonNull(contextLocal.get());
    
    public static void drop() = contextLocal.remove();
    
    public static <T> T instance(final Class<T> clazz, @Nullable final Function<Context, T> orElseGet = null) = instance(contextLocal.get().context, clazz, orElseGet);
    
    @SneakyThrows
    public static <T> T instance(final Context context, final Class<T> clazz, @Nullable final Function<Context, T> orElseGet = null) {
        @Nullable T result = context.get(clazz);
        if (result == null) {
            final Context.Key<T> key = (Privilege) context.key(clazz);
            if ((result = context.get(key)) == null)
                try {
                    context.put(key, result = (orElseGet != null ? orElseGet :
                            (Function<Context, T>) it -> (T) MethodHandleHelper.lookup().findConstructor(clazz, MethodType.methodType(void.class, Context.class)).invoke(context)).apply(context));
                } catch (final NoSuchMethodException e) { throw DebugHelper.breakpointBeforeThrow(e); }
        }
        return result;
    }
    
    Context      context;
    Annotate     annotate;
    TreeMaker    maker;
    Operators    operators;
    Names        names;
    Types        types;
    Resolve      resolve;
    ClassFinder  finder;
    Attr         attr;
    DeferredAttr deferredAttr;
    Symtab       symtab;
    Modules      modules;
    Gen          gen;
    Lint         lint;
    Log          log;
    Enter        enter;
    TypeEnter    typeEnter;
    MemberEnter  memberEnter;
    TypeEnvs     typeEnvs;
    ConstFold    constFold;
    
    SignatureGenerator signatureGenerator;
    
    HandlerSupport marker;
    
    Symbol.ModuleSymbol transientModule, mahoModule;
    
    public JavacContext(final Context context) = init(context);
    
    public void init(final Context context) {
        this.context = context;
        if (getClass() == JavacContext.class)
            mark();
        annotate = Annotate.instance(context);
        maker = TreeMaker.instance(context);
        operators = Operators.instance(context);
        names = Names.instance(context);
        types = Types.instance(context);
        resolve = Resolve.instance(context);
        finder = ClassFinder.instance(context);
        attr = Attr.instance(context);
        deferredAttr = DeferredAttr.instance(context);
        symtab = Symtab.instance(context);
        modules = Modules.instance(context);
        gen = Gen.instance(context);
        lint = Lint.instance(context);
        log = Log.instance(context);
        enter = Enter.instance(context);
        typeEnter = TypeEnter.instance(context);
        memberEnter = MemberEnter.instance(context);
        typeEnvs = TypeEnvs.instance(context);
        constFold = ConstFold.instance(context);
        signatureGenerator = SignatureGenerator.instance(context);
        context.put((Class<? super JavacContext>) getClass(), this);
        marker = this instanceof HandlerSupport handlerMarker ? handlerMarker : context.get(HandlerSupport.class);
        if (marker == null)
            context.put(HandlerSupport.class, marker = { context });
        assert symtab != null;
        transientModule = symtab.enterModule(name("amadeus.maho.lang.javac.runtime"));
        mahoModule = symtab.enterModule(name("amadeus.maho"));
    }
    
    public synchronized void mark() = contextLocal.set(this);
    
    public static boolean anyMatch(final long flags, final long mask) = (flags & mask) != 0;
    
    public static boolean allMatch(final long flags, final long mask) = (flags & mask) == mask;
    
    public static boolean noneMatch(final long flags, final long mask) = (flags & mask) == 0;
    
    public static @Nullable Symbol symbol(final JCTree tree) = switch (TreeInfo.skipParens(tree)) {
        case JCTree.JCIdent it           -> it.sym;
        case JCTree.JCFieldAccess it     -> it.sym;
        case JCTree.JCMemberReference it -> it.sym;
        case JCTree.JCNewClass it        -> it.constructor;
        case JCTree.JCClassDecl it       -> it.sym;
        case JCTree.JCVariableDecl it    -> it.sym;
        case JCTree.JCMethodDecl it      -> it.sym;
        case JCTree.JCPackageDecl it     -> it.packge;
        case JCTree.JCModuleDecl it      -> it.sym;
        default                          -> null;
    };
    
    public static @Nullable JCTree.JCModifiers modifiers(final JCTree tree) = switch (tree) {
        case JCTree.JCVariableDecl it -> it.mods;
        case JCTree.JCMethodDecl it   -> it.mods;
        case JCTree.JCClassDecl it    -> it.mods;
        case JCTree.JCModuleDecl it   -> it.mods;
        default                       -> null;
    };
    
    public static @Nullable Name name(final JCTree tree) = switch (tree) {
        case JCTree.JCIdent it        -> it.name;
        case JCTree.JCFieldAccess it  -> it.name;
        case JCTree.JCVariableDecl it -> it.name;
        case JCTree.JCMethodDecl it   -> it.name;
        case JCTree.JCClassDecl it    -> it.name;
        case JCTree.JCModuleDecl it   -> TreeInfo.fullName(it.qualId);
        default                       -> null;
    };
    
    @Proxy(INVOKEVIRTUAL)
    public static native <T extends JCTree> T toP(JavacParser $this, T t);
    
    @Proxy(GETFIELD)
    public static native TreeMaker F(JavacParser $this);
    
    @Proxy(GETFIELD)
    public static native Env<AttrContext> env(Attr $this);
    
    public JCTree.JCExpression IdentQualifiedName(final Class<?> type) = IdentQualifiedName(type.getCanonicalName());
    
    public JCTree.JCExpression IdentQualifiedName(final String fullyQualifiedName) = IdentQualifiedName(fullyQualifiedName.split("\\."));
    
    public JCTree.JCExpression IdentQualifiedName(final String... fullyQualifiedName) {
        if (fullyQualifiedName.length == 0)
            return maker.Ident(names._this);
        JCTree.JCExpression result = maker.Ident(name(fullyQualifiedName[0]));
        for (int i = 1; i < fullyQualifiedName.length; i++)
            result = maker.Select(result, name(fullyQualifiedName[i]));
        return result;
    }
    
    public <A extends Annotation> java.util.List<JCTree.JCAnnotation> getAnnotationTreesByType(final JCTree.JCModifiers modifiers, final Env<AttrContext> env, final Class<A> annotationType) {
        if (modifiers.annotations != null)
            return modifiers.annotations.stream()
                    .filter(annotation -> attr.attribType(annotation.annotationType, env).tsym.getQualifiedName().toString().equals(annotationType.getCanonicalName()))
                    .collect(Collectors.toCollection(ArrayList::new));
        return java.util.List.of();
    }
    
    public <A extends Annotation> java.util.List<Tuple2<A, JCTree.JCAnnotation>> getAnnotationsByType(final JCTree.JCModifiers modifiers, final Env<AttrContext> env, final Class<A> annotationType) {
        if (modifiers.annotations != null) {
            final AnnotationProxyMaker.Evaluator evaluator = { this, env };
            return modifiers.annotations.stream()
                    .peek(annotation -> annotation.type = annotation.type ?? attr.attribType(annotation.annotationType, env))
                    .filter(annotation -> annotation.type.tsym.getQualifiedName().toString().equals(annotationType.getCanonicalName()))
                    .peek(annotation -> annotation.type.tsym.complete())
                    .map(annotation -> Tuple.tuple(AnnotationProxyMaker.make(annotation, annotationType, evaluator), annotation))
                    .filter(tuple -> tuple.v1 != null)
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        return java.util.List.of();
    }
    
    public boolean hasAnnotation(final JCTree.JCModifiers modifiers, final Env<AttrContext> env, final Class<? extends Annotation> annotationType) = !getAnnotationsByType(modifiers, env, annotationType).isEmpty();
    
    public static boolean hasAnnotation(final AnnoConstruct construct, final Class<? extends Annotation> annotationType) = (Privilege) construct.getAttribute(annotationType) != null;
    
    public static String methodIdentity(final Symbol.MethodSymbol symbol) = symbol.name + symbol.params.stream().map(var -> var.type.tsym.getQualifiedName()).collect(Collectors.joining(",", "(", ")"));
    
    public Name name(final JCTree.Tag tag) = name(OperatorData.operatorType2operatorName.getOrDefault(tag, tag.name()));
    
    public Name name(final String name) = names.fromString(name);
    
    public Name name(final Class<?> clazz) = name(clazz.getCanonicalName());
    
    public Name[] names(final Class<?>... classes) = Stream.of(classes).map(this::name).toArray(Name[]::new);
    
    public Name[] names(final List<JCTree.JCVariableDecl> variableDecls, final Env<AttrContext> env) = variableDecls.stream()
            .peek(variableDecl -> {
                if (variableDecl.type == null)
                    variableDecl.type = attr.attribType(variableDecl.vartype, env);
            })
            .map(variableDecl -> variableDecl.type.tsym.getQualifiedName())
            .toArray(Name[]::new);
    
    public boolean nonGenerating(final JCTree tree) = !requireNonNull(name(tree)).toString().startsWith("$");
    
    public boolean requiresTypeDeclaration(final JCTree tree) = tree instanceof JCTree.JCLambda || tree instanceof JCTree.JCMemberReference;
    
    public void injectInterface(final Env<AttrContext> env, final JCTree.JCClassDecl tree, final Class<?> itf) {
        final Type type = finder.loadClass(env.toplevel.modle, name(itf.getName())).type;
        if (tree.implementing.stream().noneMatch(expression -> expression.type.equalsIgnoreMetadata(type)))
            tree.implementing = tree.implementing.append(maker.Type(type));
        if (tree.sym.type instanceof Type.ClassType classType) {
            if (classType.interfaces_field.stream().noneMatch(it -> it.equalsIgnoreMetadata(type)))
                classType.interfaces_field = classType.interfaces_field.append(type);
            if (classType.all_interfaces_field.stream().noneMatch(it -> it.equalsIgnoreMetadata(type)))
                classType.all_interfaces_field = classType.interfaces_field.append(type);
        }
    }
    
    public <T extends JCTree> List<T> collectMember(final Env<AttrContext> env, final Class<T> type, final Class<? extends Annotation> mark, final boolean reverse, final Predicate<T> checker) = env.enclClass.defs.stream()
            .filter(type::isInstance)
            .map(type::cast)
            .filter(checker)
            .filter(member -> hasAnnotation(requireNonNull(modifiers(member)), env, mark) == reverse)
            .collect(List.collector());
    
    public List<JCTree> mapGetter(final Env<AttrContext> env, final List<? extends JCTree> list) = list.map(tree -> tree instanceof JCTree.JCVariableDecl variable ?
            lookupMethod(env, variable.name, variable.sym.type.tsym.getQualifiedName(), new Name[0]) ?? tree : tree);
    
    public boolean shouldInjectVariable(final Env<AttrContext> env, final Name name) = lookupVariable(env, name) == null;
    
    public @Nullable JCTree.JCVariableDecl lookupVariable(final Env<AttrContext> env, final Name name, final @Nullable Name type = null) {
        final Stream<JCTree.JCVariableDecl> stream = env.enclClass.defs.stream()
                .filter(JCTree.JCVariableDecl.class::isInstance)
                .map(JCTree.JCVariableDecl.class::cast)
                .filter(variableDecl -> variableDecl.name.equals(name));
        return (type == null ? stream : stream.filter(variableDecl -> variableDecl.sym.type.tsym.getQualifiedName().equals(type)))
                .findFirst()
                .orElse(null);
    }
    
    public boolean shouldInjectMethod(final Env<AttrContext> env, final Name name, final Name... argsTypes) = lookupMethod(env, name, argsTypes) == null;
    
    public @Nullable JCTree.JCMethodDecl lookupMethod(final Env<AttrContext> env, final Name name, final @Nullable Name returnType = null, final Name... argsTypes) {
        final int p_index[] = { -1 };
        final Supplier<Name> nextArgType = () -> argsTypes[++p_index[0]];
        final Stream<JCTree.JCMethodDecl> stream = env.enclClass.defs.stream()
                .filter(JCTree.JCMethodDecl.class::isInstance)
                .map(JCTree.JCMethodDecl.class::cast)
                .filter(method -> method.name.equals(name));
        return (returnType == null ? stream : stream.filter(method -> method.sym.getReturnType().tsym.getQualifiedName().equals(returnType)))
                .filter(method -> method.sym.getParameters().size() == argsTypes.length)
                .filter(method -> {
                    p_index[0] = -1;
                    return method.sym.getParameters().stream().allMatch(symbol -> symbol.type.tsym.getQualifiedName().equals(nextArgType.get()));
                })
                .findFirst()
                .orElse(null);
    }
    
    public boolean shouldInjectInnerClass(final Env<AttrContext> env, final Name name) = lookupInnerClassDecl(env, name) == null;
    
    public static Stream<Symbol.ClassSymbol> supers(final Symbol.ClassSymbol symbol) = LinkedIterator.of(it -> {
        final Type superType = it.getSuperclass();
        return superType == Type.noType ? null : (Symbol.ClassSymbol) superType.tsym;
    }, symbol).stream(true);
    
    public static Stream<Symbol.ClassSymbol> allSupers(final Symbol.ClassSymbol symbol) = supers(symbol).flatMap(it -> Stream.concat(Stream.of(it), it.getInterfaces().stream().map(type -> (Symbol.ClassSymbol) type.tsym))).distinct();
    
    public static Stream<Symbol.ClassSymbol> allSupers(final Symbol.TypeSymbol symbol) = switch (symbol) {
        case Symbol.TypeVariableSymbol variableSymbol -> variableSymbol.getBounds().stream().map(it -> it.tsym).flatMap(JavacContext::allSupers).distinct();
        case Symbol.ClassSymbol classSymbol           -> allSupers(classSymbol);
        default                                       -> throw new IllegalStateException(STR."Unexpected value: \{symbol}");
    };
    
    public @Nullable JCTree.JCClassDecl lookupInnerClassDecl(final Env<AttrContext> env, final Name name) = env.enclClass.defs.stream()
            .filter(JCTree.JCClassDecl.class::isInstance)
            .map(JCTree.JCClassDecl.class::cast)
            .filter(classDecl -> classDecl.name.equals(name))
            .findFirst()
            .orElse(null);
    
    public void followAnnotation(final JCTree.JCAnnotation target, final String name, final JCTree.JCModifiers follower) = target.args.stream()
            .cast(JCTree.JCAssign.class)
            .filter(assign -> assign.lhs instanceof JCTree.JCIdent ident && ident.name.toString().equals(name))
            .map(assign -> assign.rhs)
            .forEach(value -> {
                if (value instanceof JCTree.JCAnnotation annotation)
                    follower.annotations = follower.annotations.append(annotation);
                else if (value instanceof JCTree.JCNewArray newArray)
                    follower.annotations = follower.annotations.appendList(newArray.elems.stream()
                            .cast(JCTree.JCAnnotation.class)
                            .collect(List.collector()));
            });
    
    public void followAnnotation(final Env<AttrContext> env, final JCTree.JCModifiers target, final JCTree.JCModifiers follower) = target.annotations.stream()
            .filter(annotation -> shouldFollowAnnotation(attr.attribType(annotation.annotationType, env).tsym.getQualifiedName().toString()))
            .forEach(annotation -> follower.annotations = follower.annotations.append(annotation));
    
    public void followAnnotationWithoutNullable(final Env<AttrContext> env, final JCTree.JCModifiers target, final JCTree.JCModifiers follower) = target.annotations.stream()
            .filter(annotation -> {
                final String name = attr.attribType(annotation.annotationType, env).tsym.getQualifiedName().toString();
                return !Nullable.class.getCanonicalName().equals(name) && shouldFollowAnnotation(name);
            })
            .forEach(annotation -> follower.annotations = follower.annotations.append(annotation));
    
    public void followNullable(final Env<AttrContext> env, final JCTree.JCModifiers target, final JCTree.JCModifiers follower) = target.annotations.stream()
            .filter(annotation -> Nullable.class.getCanonicalName().equals(attr.attribType(annotation.annotationType, env).tsym.getQualifiedName().toString()))
            .forEach(annotation -> follower.annotations = follower.annotations.append(annotation));
    
    public void followAnnotation(final Symbol target, final JCTree.JCModifiers follower) = target.getAnnotationMirrors().stream()
            .filter(compound -> shouldFollowAnnotation(compound.type.tsym.getQualifiedName().toString()))
            .map(maker::Annotation)
            .forEach(annotation -> follower.annotations = follower.annotations.append(annotation));
    
    public static final Set<Class<? extends Annotation>> followableAnnotationTypes = new HashSet<>(List.of(Deprecated.class, Nullable.class, Callback.class, TestOnly.class));
    
    public boolean shouldFollowAnnotation(final String name) = followableAnnotationTypes.stream().map(Class::getName).anyMatch(name::equals);
    
    public static Function<String, String> simplify(final String context) {
        final Set<String> mark = new HashSet<>();
        return name -> mark.add(name) ? name : STR."\{context}$\{name}";
    }
    
    public JCTree.JCMethodInvocation unsafeFieldBaseAccess(final TreeMaker maker, final JCDiagnostic.DiagnosticPosition position, final Env<AttrContext> env, final Symbol.VarSymbol field, final String name,
            final JCTree.JCExpression... args) {
        final List<JCTree.JCExpression> baseAndOffset = anyMatch(field.flags_field, STATIC) ?
                List.of(
                        maker.Ident(unsafeConstant(position, env, "staticFieldBase", field, symtab.objectType, fieldConstant(position, env, field))),
                        maker.Ident(unsafeConstant(position, env, "staticFieldOffset", field, symtab.longType, fieldConstant(position, env, field)))) :
                List.of(
                        maker.Ident(names._this),
                        maker.Ident(unsafeConstant(position, env, "objectFieldOffset", field, symtab.longType, fieldConstant(position, env, field))));
        return maker.Apply(List.nil(), maker.Select(maker.Ident(unsafe(position, env)), name(name)), baseAndOffset.appendList(List.from(args)));
    }
    
    public DynamicVarSymbol unsafe(final JCDiagnostic.DiagnosticPosition position, final Env<AttrContext> env) {
        final Symbol.MethodSymbol accessor = resolve.resolveInternalMethod(position, env, symtab.enterClass(symtab.java_base, name(Unsafe.class)).type, name("getUnsafe"), List.nil(), List.nil());
        final PoolConstant.LoadableConstant constants[] = { accessor.asHandle() };
        return { name("$unsafe"), symtab.noSymbol, constantInvokeBSM(position, env).asHandle(), symtab.enterClass(symtab.java_base, name(Unsafe.class)).type, constants };
    }
    
    public DynamicVarSymbol unsafeConstant(final JCDiagnostic.DiagnosticPosition position, final Env<AttrContext> env, final String name, final Symbol.VarSymbol field, final Type constType,
            final PoolConstant.LoadableConstant... args) {
        final Symbol.MethodSymbol accessor = resolve.resolveInternalMethod(position, env, symtab.enterClass(symtab.java_base, name(Unsafe.class)).type, name(name), List.from(args).map(types::constantType), List.nil());
        final PoolConstant.LoadableConstant constants[] = { accessor.asHandle(), unsafe(position, env) };
        return { name(STR."$\{field.name}$\{name}"), symtab.noSymbol, constantInvokeBSM(position, env).asHandle(), constType, ArrayHelper.addAll(constants, args) };
    }
    
    public DynamicVarSymbol fieldConstant(final JCDiagnostic.DiagnosticPosition position, final Env<AttrContext> env, final Symbol.VarSymbol field) {
        final Symbol.MethodSymbol accessor = resolve.resolveInternalMethod(position, env, symtab.classType, name("getDeclaredField"), List.of(symtab.stringType), List.nil());
        final PoolConstant.LoadableConstant constants[] = { accessor.asHandle(), (Type.ClassType) field.owner.type, PoolConstant.LoadableConstant.String(field.name.toString()) };
        return { name(STR."$\{field.name}$field"), symtab.noSymbol, constantInvokeBSM(position, env).asHandle(), symtab.enterClass(symtab.java_base, name(Field.class)).type, constants };
    }
    
    public Symbol.MethodSymbol constantInvokeBSM(final JCDiagnostic.DiagnosticPosition position, final Env<AttrContext> env) = resolve.resolveInternalMethod(position, env,
            symtab.enterClass(symtab.java_base, name("java.lang.invoke.ConstantBootstraps")).type, name("invoke"), List.of(
                    symtab.methodHandleLookupType, symtab.stringType, symtab.classType, symtab.methodHandleType, types.makeArrayType(symtab.objectType)
            ), List.nil());
    
    public Symbol.MethodSymbol lookupDynamicLookupMethod(final Name name) {
        final Symbol.ClassSymbol dynamicLookupClass = symtab.enterClass(mahoModule, name("amadeus.maho.core.extension.DynamicLookup"));
        final Symbol.MethodSymbol bsm = (Symbol.MethodSymbol) dynamicLookupClass.members().getSymbolsByName(name, Scope.LookupKind.NON_RECURSIVE).iterator().next();
        final Symbol.PackageSymbol owner = symtab.enterPackage(mahoModule, name("amadeus.maho.share"));
        final Symbol.ClassSymbol dynamicLookupClassShare = { dynamicLookupClass.flags_field | PUBLIC, dynamicLookupClass.name, owner };
        dynamicLookupClassShare.members_field = dynamicLookupClass.members_field;
        dynamicLookupClassShare.trans_local = List.nil();
        return { bsm.flags_field, bsm.name, bsm.type, dynamicLookupClassShare };
    }
    
    public void discardDiagnostic(final Runnable runnable) {
        final Log.DiscardDiagnosticHandler discardHandler = { log };
        try { runnable.run(); } finally { log.popDiagnosticHandler(discardHandler); }
    }
    
    public <T> @Nullable T discardDiagnostic(final Supplier<T> supplier) {
        final Log.DiscardDiagnosticHandler discardHandler = { log };
        try { return supplier.get(); } finally { log.popDiagnosticHandler(discardHandler); }
    }
    
}
