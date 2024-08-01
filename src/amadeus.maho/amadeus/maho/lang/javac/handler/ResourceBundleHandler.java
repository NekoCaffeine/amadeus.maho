package amadeus.maho.lang.javac.handler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
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
import amadeus.maho.lang.javac.handler.base.DelayedContext;
import amadeus.maho.lang.javac.handler.base.Handler;
import amadeus.maho.util.misc.Environment;
import amadeus.maho.util.runtime.ArrayHelper;
import amadeus.maho.util.runtime.StreamHelper;

import static amadeus.maho.lang.javac.handler.ResourceBundleHandler.PRIORITY;

@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Handler(value = ResourceBundle.class, priority = PRIORITY)
public class ResourceBundleHandler extends BaseHandler<ResourceBundle> {
    
    public static final int PRIORITY = -1 << 12;
    
    public record AgentMethod(Symbol.MethodSymbol methodSymbol, Pattern pattern, ResourceAgent agent) { }
    
    @Override
    public boolean shouldProcess(final boolean advance) = advance;
    
    @Override
    public void processClass(final Env<AttrContext> env, final JCTree.JCClassDecl tree, final JCTree owner, final ResourceBundle annotation, final JCTree.JCAnnotation annotationTree, final boolean advance)
            = instance(DelayedContext.class).todos() += context -> instance(context, ResourceBundleHandler.class).process(env, tree, annotation, annotationTree, advance);
    
    private void process(final Env<AttrContext> env, final JCTree.JCClassDecl tree, final ResourceBundle annotation, final JCTree.JCAnnotation annotationTree, final boolean advance) {
        final Path location = location(annotation);
        if (Files.isDirectory(location)) {
            final FieldDefaultsHandler fieldDefaultsHandler = instance(FieldDefaultsHandler.class);
            maker.at(annotationTree.pos);
            final JCTree.JCAnnotation fieldDefaultAnnotationTree = maker.Annotation(IdentQualifiedName(FieldDefaults.class), List.nil());
            final HashMap<String, AgentMethod> agents = { };
            final boolean itf = anyMatch(tree.mods.flags, Flags.INTERFACE);
            final ResourceAgentHandler resourceAgentHandler = instance(ResourceAgentHandler.class);
            allSupers(tree.sym)
                    .map(Symbol.ClassSymbol::members)
                    .map(Scope::getSymbols)
                    .flatMap(StreamHelper::fromIterable)
                    .forEach(member -> {
                        if (!itf || anyMatch(member.flags_field, Flags.STATIC))
                            if (member instanceof Symbol.MethodSymbol methodSymbol && resourceAgentHandler.valid(methodSymbol)) {
                                final @Nullable ResourceAgent agentAnnotation = methodSymbol.getAnnotation(ResourceAgent.class);
                                if (agentAnnotation != null) {
                                    try {
                                        final Pattern pattern = Pattern.compile(agentAnnotation.value());
                                        final Map<String, Integer> namedGroupsIndex = pattern.namedGroups();
                                        final java.util.List<String> missingKey = methodSymbol.params().stream().map(symbol -> symbol.name.toString()).filterNot(namedGroupsIndex.keySet()::contains).toList();
                                        if (missingKey.isEmpty())
                                            agents.compute(agentAnnotation.value(), (regex, agentMethod) -> addAgent(regex, annotationTree, agentMethod, methodSymbol, pattern, agentAnnotation));
                                    } catch (final PatternSyntaxException ignored) { }
                                }
                            }
                    });
            try {
                Files.walk(location, annotation.visitOptions()).forEach(path -> {
                    final String arg = location % path | "/";
                    final Map<String, java.util.List<Symbol.MethodSymbol>> record = new HashMap<>();
                    final boolean p_repeatedly[] = { false };
                    agents.values().stream()
                            .filter(agentMethod -> shouldHandle(path, agentMethod.agent()))
                            .forEach(agentMethod -> {
                                final Matcher matcher = agentMethod.pattern().matcher(arg);
                                if (matcher.find()) {
                                    final Symbol.MethodSymbol methodSymbol = agentMethod.methodSymbol;
                                    final boolean instance = itf || noneMatch(methodSymbol.flags_field, Flags.STATIC);
                                    final Name name = name(agentMethod.agent.format(), matcher, location, path);
                                    final String nameString = name.toString();
                                    final java.util.List<Symbol.MethodSymbol> symbols = record.computeIfAbsent(nameString, _ -> new LinkedList<>());
                                    symbols += methodSymbol;
                                    if (symbols.size() > 1) {
                                        p_repeatedly[0] = true;
                                        return;
                                    }
                                    if (shouldInjectVariable(env, name)) {
                                        final List<JCTree.JCExpression> args = methodSymbol.params().stream()
                                                .map(parameter -> matcher.group(parameter.name.toString()))
                                                .map(value -> maker.Literal(value))
                                                .collect(List.collector());
                                        final JCTree.JCVariableDecl decl = maker.VarDef(maker.Modifiers(instance ? 0L : Flags.STATIC), name, resourceType(path, methodSymbol),
                                                maker.Apply(List.nil(), instance ? maker.Ident(methodSymbol) : maker.QualIdent(methodSymbol), args));
                                        fieldDefaultsHandler.processVariable(env, decl, tree, annotation.fieldDefaults(), fieldDefaultAnnotationTree, advance);
                                        injectMember(env, decl);
                                    }
                                }
                            });
                    if (p_repeatedly[0])
                        log.error(JCDiagnostic.DiagnosticFlag.RESOLVE_ERROR, annotationTree, new JCDiagnostic.Error(MahoJavac.KEY, "resource.bundle.repeatedly.matched", arg, record));
                });
            } catch (final IOException e) {
                e.printStackTrace();
                log.error(JCDiagnostic.DiagnosticFlag.RESOLVE_ERROR, annotationTree, new JCDiagnostic.Error(MahoJavac.KEY, "resource.bundle.ioe", e.getMessage()));
            }
        }
    }
    
    protected boolean shouldHandle(final Path path, final ResourceAgent agentAnnotation) = ArrayHelper.contains(agentAnnotation.types(), Files.isDirectory(path) ? ResourceAgent.Type.DIRECTORY : ResourceAgent.Type.FILE);
    
    protected Name name(final String format, final Matcher matcher, final Path location, final Path path) {
        final Map<String, Integer> map = matcher.pattern().namedGroups();
        final @Nullable String group = map.containsKey("name") ? matcher.group("name") : null, name = format.formatted(group ?? defaultName(location, path));
        final int p_index[] = { -1 };
        return name(name.codePoints().map(c -> (++p_index[0] == 0 ? Character.isJavaIdentifierStart(c) : Character.isJavaIdentifierPart(c)) ? c : '_').collectCodepoints());
    }
    
    protected String defaultName(final Path location, final Path path) {
        final String parent = (location % path.getParent()).toString();
        return parent.isEmpty() ? path.fileName() : parent + path.getFileSystem().getSeparator() + path.fileName();
    }
    
    protected JCTree.JCExpression resourceType(final Path path, final Symbol.MethodSymbol methodSymbol) = maker.Type(methodSymbol.getReturnType());
    
    protected AgentMethod addAgent(final String regex, final JCTree.JCAnnotation annotationTree, final @Nullable AgentMethod agentMethod,
            final Symbol.MethodSymbol agent, final Pattern pattern, final ResourceAgent agentAnnotation) {
        if (agentMethod == null)
            return { agent, pattern, agentAnnotation };
        log.error(JCDiagnostic.DiagnosticFlag.RESOLVE_ERROR, annotationTree, new JCDiagnostic.Error(MahoJavac.KEY, "resource.bundle.agent.repeat", agentMethod.methodSymbol, agent));
        return agentMethod;
    }
    
    protected Path location(final ResourceBundle annotation) {
        final String value = annotation.value();
        return !value.isEmpty() && value.charAt(0) == '!' ? Path.of(value.substring(1)) : Path.of(Environment.local().lookup("amadeus.maho.build.project.root", "")) / value;
    }
    
}
