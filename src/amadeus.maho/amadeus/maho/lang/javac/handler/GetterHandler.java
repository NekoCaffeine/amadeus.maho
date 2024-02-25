package amadeus.maho.lang.javac.handler;

import java.util.concurrent.Callable;

import com.sun.source.tree.CaseTree;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.javac.MahoJavac;
import amadeus.maho.lang.javac.handler.base.BaseHandler;
import amadeus.maho.lang.javac.handler.base.Handler;

import static amadeus.maho.lang.javac.handler.GetterHandler.PRIORITY;
import static com.sun.tools.javac.code.Flags.*;

@NoArgsConstructor
@Handler(value = Getter.class, ranges = Handler.Range.FIELD, priority = PRIORITY)
public class GetterHandler extends BaseHandler<Getter> {
    
    public static final int PRIORITY = 1 << 2;
    
    public static final String REFERENCE_GETTER = "Reference", ON_REFERENCE_GETTER = "on" + REFERENCE_GETTER;
    
    @Override
    public void processVariable(final Env<AttrContext> env, final JCTree.JCVariableDecl tree, final JCTree owner, final Getter annotation, final JCTree.JCAnnotation annotationTree, final boolean advance) {
        if (owner instanceof JCTree.JCClassDecl clazz)
            if (shouldInjectMethod(env, tree.sym.name)) {
                if (annotation.lazy()) {
                    boolean error = false;
                    if (anyMatch(clazz.mods.flags, INTERFACE)) {
                        log.error(JCDiagnostic.DiagnosticFlag.RESOLVE_ERROR, tree, new JCDiagnostic.Error(MahoJavac.KEY, "lazy.getter.must.not.interface"));
                        error = true;
                    }
                    if (tree.init == null) {
                        log.error(JCDiagnostic.DiagnosticFlag.RESOLVE_ERROR, tree, new JCDiagnostic.Error(MahoJavac.KEY, "lazy.getter.must.has.init"));
                        error = true;
                    }
                    if (error)
                        return;
                }
                final JCTree.JCExpression unpackedType = ReferenceHandler.unpackedType(tree.vartype, this);
                final List<JCTree.JCStatement> body = generateBody(env, tree, unpackedType, annotation, annotationTree);
                final long flags = accessLevel(annotation.value()) | (annotation.nonStatic() ? 0 : tree.mods.flags & STATIC) | (anyMatch(env.enclClass.mods.flags, INTERFACE) ? STATIC : 0);
                injectMember(env, maker.MethodDef(maker.Modifiers(flags).let(modifiers -> followAnnotation(env, tree.mods, modifiers)), tree.name, unpackedType, List.nil(), List.nil(), List.nil(),
                        maker.Block(0L, body), null).let(it -> followAnnotation(annotationTree, "on", it.mods)));
            }
    }
    
    protected List<JCTree.JCStatement> generateBody(final Env<AttrContext> env, final JCTree.JCVariableDecl tree, final JCTree.JCExpression unpackedType, final Getter getter, final JCTree.JCAnnotation annotationTree) {
        if (getter.lazy()) {
            if (tree.sym != null && (Privilege) tree.sym.data instanceof Callable<?>)
                (Privilege) (tree.sym.data = null);
            tree.mods.flags &= ~FINAL;
            tree.sym.flags_field &= ~FINAL;
            final String type = switch (attr.attribType(unpackedType, env)) {
                case Type.JCPrimitiveType primitiveType -> switch (primitiveType.getTag()) {
                    case BYTE    -> "Byte";
                    case SHORT   -> "Short";
                    case INT     -> "Int";
                    case LONG    -> "Long";
                    case FLOAT   -> "Float";
                    case DOUBLE  -> "Double";
                    case CHAR    -> "Char";
                    case BOOLEAN -> "Boolean";
                    default      -> "Reference";
                };
                default                                 -> "Reference";
            };
            final JCTree.JCVariableDecl mark = maker.VarDef(maker.Modifiers(PRIVATE | tree.mods.flags & STATIC), name(STR."$\{tree.name.toString()}$mark"), maker.TypeIdent(TypeTag.INT), null);
            if (shouldInjectVariable(env, mark.name))
                injectMember(env, mark);
            final Name $value = name("$value");
            final List<JCTree.JCStatement> statements = List.of(
                    maker.Return(maker.Conditional(maker.Binary(JCTree.Tag.EQ, maker.Ident(mark.name), maker.Literal(2)),
                            maker.Ident(tree.name),
                            maker.SwitchExpression(unsafeFieldBaseAccess(maker, tree, env, mark.sym, "compareAndExchangeInt", maker.Literal(0), maker.Literal(1)), List.of(
                                    maker.Case(CaseTree.CaseKind.RULE, List.of(maker.ConstantCaseLabel(maker.Literal(0))), null, List.of(
                                            maker.VarDef(maker.Modifiers(FINAL), $value, unpackedType, tree.init),
                                            maker.Exec(unsafeFieldBaseAccess(maker, tree, env, tree.sym, STR."put\{type}Opaque", maker.Ident($value))),
                                            maker.Exec(unsafeFieldBaseAccess(maker, tree, env, mark.sym, "putIntRelease", maker.Literal(2))),
                                            maker.Yield(maker.Ident($value))
                                    ), null),
                                    maker.Case(CaseTree.CaseKind.RULE, List.of(maker.ConstantCaseLabel(maker.Literal(1))), null, List.of(
                                            maker.WhileLoop(
                                                    maker.Binary(JCTree.Tag.NE, unsafeFieldBaseAccess(maker, tree, env, mark.sym, "getIntOpaque"), maker.Literal(2)),
                                                    maker.Exec(maker.Apply(List.nil(), maker.Select(IdentQualifiedName(Thread.class), name("onSpinWait")), List.nil()))
                                            ),
                                            maker.Exec(unsafe(tree, env).invocation(maker, "loadLoadFence")),
                                            maker.Yield(maker.TypeCast(unpackedType, unsafeFieldBaseAccess(maker, tree, env, tree.sym, STR."get\{type}Opaque")))
                                    ), null),
                                    maker.Case(CaseTree.CaseKind.RULE, List.of(maker.DefaultCaseLabel()), null, List.of(
                                            maker.Yield(maker.TypeCast(unpackedType, unsafeFieldBaseAccess(maker, tree, env, tree.sym, STR."get\{type}Opaque")))
                                    ), null)
                            ))))
            );
            tree.init = null;
            if (tree.vartype != unpackedType) {
                final Name referenceGetterName = tree.sym.name.append(name(REFERENCE_GETTER));
                if (shouldInjectMethod(env, referenceGetterName))
                    injectMember(env, maker.MethodDef(maker.Modifiers(accessLevel(getter.value()) | (getter.nonStatic() ? 0 : tree.mods.flags & STATIC) | (anyMatch(env.enclClass.mods.flags, INTERFACE) ? STATIC : 0)),
                            referenceGetterName, tree.vartype, List.nil(), List.nil(), List.nil(), maker.Block(0L, statements), null).let(it -> followAnnotation(annotationTree, ON_REFERENCE_GETTER, it.mods)));
                return List.of(maker.Return(maker.Apply(List.nil(), maker.Select(maker.Apply(List.nil(), maker.Ident(referenceGetterName), List.nil()), name("get")), List.nil())));
            }
            return statements;
        }
        if (tree.vartype != unpackedType) {
            final Name referenceGetterName = tree.sym.name.append(name(REFERENCE_GETTER));
            if (shouldInjectMethod(env, referenceGetterName))
                injectMember(env, maker.MethodDef(maker.Modifiers(accessLevel(getter.value()) | (getter.nonStatic() ? 0 : tree.mods.flags & STATIC) | (anyMatch(env.enclClass.mods.flags, INTERFACE) ? STATIC : 0)),
                        referenceGetterName, tree.vartype, List.nil(), List.nil(), List.nil(), maker.Block(0L, List.of(maker.Return(maker.Ident(tree.name)))), null));
        }
        return List.of(maker.Return(tree.vartype != unpackedType ? maker.Apply(List.nil(), maker.Select(maker.Ident(tree.name), name("get")), List.nil()) : maker.Ident(tree.name)));
    }
    
    @Override
    public void processMethod(final Env<AttrContext> env, final JCTree.JCMethodDecl tree, final JCTree owner, final Getter annotation, final JCTree.JCAnnotation annotationTree, final boolean advance) {
        if (annotation.lazy())
            log.error(JCDiagnostic.DiagnosticFlag.RESOLVE_ERROR, annotationTree, new JCDiagnostic.Error(MahoJavac.KEY, "getter.method.lazy"));
        if (!tree.params.isEmpty())
            log.error(JCDiagnostic.DiagnosticFlag.RESOLVE_ERROR, tree, new JCDiagnostic.Error(MahoJavac.KEY, "accessor.method.has-parameter", "@Getter"));
        if (owner instanceof JCTree.JCClassDecl && noneMatch(((JCTree.JCClassDecl) owner).mods.flags, INTERFACE))
            log.error(JCDiagnostic.DiagnosticFlag.RESOLVE_ERROR, tree, new JCDiagnostic.Error(MahoJavac.KEY, "accessor.method.non-interface", "@Getter"));
        if (anyMatch(tree.mods.flags, STATIC))
            log.error(JCDiagnostic.DiagnosticFlag.RESOLVE_ERROR, tree, new JCDiagnostic.Error(MahoJavac.KEY, "accessor.method.static", "@Getter"));
    }
    
}
