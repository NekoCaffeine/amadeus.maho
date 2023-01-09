package amadeus.maho.lang.javac.handler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.ResourceAgent;
import amadeus.maho.lang.ResourceBundle;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.lang.javac.MahoJavac;
import amadeus.maho.lang.javac.handler.base.BaseHandler;
import amadeus.maho.lang.javac.handler.base.Handler;
import amadeus.maho.util.misc.Environment;
import amadeus.maho.util.runtime.ArrayHelper;
import amadeus.maho.util.runtime.StreamHelper;
import amadeus.maho.util.tuple.Tuple;
import amadeus.maho.util.tuple.Tuple2;
import amadeus.maho.util.tuple.Tuple3;

import static amadeus.maho.lang.javac.handler.ResourceBundleHandler.PRIORITY;

@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Handler(value = ResourceBundle.class, priority = PRIORITY)
public class ResourceBundleHandler extends BaseHandler<ResourceBundle> {
    
    public static final int PRIORITY = -1 << 12;
    
    @Override
    public boolean shouldProcess(final boolean advance) = advance;
    
    @Override
    public void processClass(final Env<AttrContext> env, final JCTree.JCClassDecl tree, final JCTree owner, final ResourceBundle annotation, final JCTree.JCAnnotation annotationTree, final boolean advance)
            = delayedContext.todos() += () -> {
        final Path location = location(annotation);
        if (Files.isDirectory(location)) {
            final FieldDefaultsHandler fieldDefaultsHandler = instance(FieldDefaultsHandler.class);
            maker.at(annotationTree.pos);
            final JCTree.JCAnnotation fieldDefaultAnnotationTree = maker.Annotation(IdentQualifiedName(FieldDefaults.class), List.nil());
            final HashMap<String, Tuple2<Symbol.MethodSymbol, ResourceAgent>> agents = { };
            final boolean itf = anyMatch(tree.mods.flags, Flags.INTERFACE);
            allSupers(tree.sym)
                    .map(Symbol.ClassSymbol::members)
                    .map(Scope::getSymbols)
                    .flatMap(StreamHelper::fromIterable)
                    .forEach(member -> {
                        if (!itf || anyMatch(member.flags_field, Flags.STATIC))
                            if (member instanceof Symbol.MethodSymbol methodSymbol && methodSymbol.params().size() == 1 && methodSymbol.params().head.type.tsym == symtab.stringType.tsym) {
                                final @Nullable ResourceAgent agentAnnotation = methodSymbol.getAnnotation(ResourceAgent.class);
                                if (agentAnnotation != null)
                                    agents.compute(agentAnnotation.value(), (regex, tuple) -> addAgent(regex, annotationTree, tuple, methodSymbol, agentAnnotation));
                            }
                    });
            final List<Tuple3<Pattern, Symbol.MethodSymbol, ResourceAgent>> patterns = agents.entrySet().stream()
                    .map(entry -> {
                        try {
                            return Tuple.tuple(Pattern.compile(entry.getKey()), entry.getValue().v1, entry.getValue().v2);
                        } catch (final PatternSyntaxException e) { return null; }
                    })
                    .nonnull()
                    .collect(List.collector());
            try {
                Files.walk(location, annotation.visitOptions()).forEach(path -> {
                    final String arg = location % path | "/";
                    final Set<Symbol.MethodSymbol> symbols = new HashSet<>();
                    patterns.stream()
                            .filter(tuple -> shouldHandle(path, tuple.v3))
                            .map(tuple -> Tuple.tuple(tuple.v1.matcher(arg), tuple.v2))
                            .filter(tuple -> tuple.v1.find())
                            .forEach(tuple -> {
                                if (symbols.isEmpty()) {
                                    final boolean instance = itf || noneMatch(tuple.v2.flags_field, Flags.STATIC);
                                    final JCTree.JCVariableDecl decl = maker.VarDef(maker.Modifiers(instance ? 0L : Flags.STATIC), name(tuple.v1, location, path), resourceType(path, tuple.v2),
                                            maker.Apply(List.nil(), instance ? maker.Ident(tuple.v2) : maker.QualIdent(tuple.v2), List.of(maker.Literal(arg))));
                                    fieldDefaultsHandler.processVariable(env, decl, tree, annotation.fieldDefaults(), fieldDefaultAnnotationTree, advance);
                                    injectMember(env, decl);
                                }
                                symbols += tuple.v2;
                            });
                    if (symbols.size() > 1)
                        log.error(JCDiagnostic.DiagnosticFlag.RESOLVE_ERROR, annotationTree, new JCDiagnostic.Error(MahoJavac.KEY, "resource.bundle.repeatedly.matched", arg, symbols));
                });
            } catch (final IOException e) {
                e.printStackTrace();
                log.error(JCDiagnostic.DiagnosticFlag.RESOLVE_ERROR, annotationTree, new JCDiagnostic.Error(MahoJavac.KEY, "resource.bundle.ioe", e.getMessage()));
            }
        }
    };
    
    protected boolean shouldHandle(final Path path, final ResourceAgent agentAnnotation) = ArrayHelper.contains(agentAnnotation.types(), Files.isDirectory(path) ? ResourceAgent.Type.DIRECTORY : ResourceAgent.Type.FILE);
    
    protected Name name(final Matcher matcher, final Path location, final Path path) {
        final Map<String, Integer> map = matcher.pattern().namedGroupsIndex();
        final @Nullable String group = map.containsKey("name") ? matcher.group("name") : null, name = group ?? defaultName(location, path);
        final int p_index[] = { -1 };
        return name(name.codePoints().map(c -> (++p_index[0] == 0 ? Character.isJavaIdentifierStart(c) : Character.isJavaIdentifierPart(c)) ? c : '_').collectCodepoints());
    }
    
    protected String defaultName(final Path location, final Path path) {
        final String parent = (location % path.getParent()).toString();
        return parent.isEmpty() ? path.fileName() : parent + path.getFileSystem().getSeparator() + path.fileName();
    }
    
    protected JCTree.JCExpression resourceType(final Path path, final Symbol.MethodSymbol methodSymbol) = maker.Type(methodSymbol.getReturnType());
    
    protected Tuple2<Symbol.MethodSymbol, ResourceAgent> addAgent(final String regex, final JCTree.JCAnnotation annotationTree, final @Nullable Tuple2<Symbol.MethodSymbol, ResourceAgent> tuple,
            final Symbol.MethodSymbol agent, final ResourceAgent agentAnnotation) {
        if (tuple == null)
            return { agent, agentAnnotation };
        log.error(JCDiagnostic.DiagnosticFlag.RESOLVE_ERROR, annotationTree, new JCDiagnostic.Error(MahoJavac.KEY, "resource.bundle.agent.repeat", tuple.v1, agent));
        return tuple;
    }
    
    protected Path location(final ResourceBundle annotation) {
        final String value = annotation.value();
        return value.length() > 0 && value.charAt(0) == '!' ? Path.of(value.substring(1)) : Path.of(Environment.local().lookup("maho.build.project.root", "")) / value;
    }
    
}
