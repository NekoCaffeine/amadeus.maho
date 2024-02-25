package amadeus.maho.lang.javac.handler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

import com.sun.tools.javac.code.Directive;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.Modules;
import com.sun.tools.javac.comp.Resolve;
import com.sun.tools.javac.jvm.ClassReader;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;

import amadeus.maho.lang.javac.JavacContext;
import amadeus.maho.lang.javac.handler.base.HandlerSupport;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;

import static amadeus.maho.lang.javac.JavacContext.*;
import static com.sun.tools.javac.code.Flags.*;

@TransformProvider
public class AccessibleHandler {
    
    public static final int ACCESS_MARKS = PUBLIC | PROTECTED | PRIVATE;
    
    @Hook(forceReturn = true)
    private static boolean isAccessible(final Resolve $this, final Env<AttrContext> env, final Symbol.TypeSymbol symbol, final boolean checkInner) = true;
    
    @Hook
    private static Hook.Result isAccessible(final Resolve $this, final Env<AttrContext> env, final Type site, final Symbol sym, final boolean checkInner)
            = Hook.Result.falseToVoid(sym instanceof Symbol.ClassSymbol || PrivilegeHandler.inPrivilegeContext(new ArrayList<>(HandlerSupport.attrContext()), env));
    
    @Hook(forceReturn = true)
    private static void addVisiblePackages(final Modules $this, final Symbol.ModuleSymbol symbol, final Map<Name, Symbol.ModuleSymbol> seenPackages,
            final Symbol.ModuleSymbol exportsFrom, final Collection<Directive.ExportsDirective> exports) = exports.stream()
            .filter(directive -> directive.modules == null || directive.modules.contains(symbol))
            .forEach(directive -> {
                seenPackages[directive.packge.fullname] = exportsFrom;
                symbol.visiblePackages[directive.packge.fullname] = directive.packge;
            });
    
    @Hook(at = @At(method = @At.MethodInsn(name = "initAddReads")), before = false)
    private static void completeModule(final Modules $this, final Symbol.ModuleSymbol moduleSymbol) {
        if (moduleSymbol.classLocation != null)
            try {
                final JavacContext context = instance(JavacContext.class);
                final ListBuffer<Directive.ExportsDirective> exports = { };
                final Set<String> seenPackages = new HashSet<>();
                final JavaFileManager manager = instance(JavaFileManager.class);
                for (final JavaFileObject clazz : manager.list(moduleSymbol.classLocation, "", EnumSet.of(JavaFileObject.Kind.CLASS), true)) {
                    final String binName = manager.inferBinaryName(moduleSymbol.classLocation, clazz);
                    final String pack = binName.lastIndexOf('.') != -1 ? binName.substring(0, binName.lastIndexOf('.')) : "";
                    if (seenPackages.add(pack))
                        exports.add(new Directive.ExportsDirective(context.symtab.enterPackage(moduleSymbol, context.names.fromString(pack)), null));
                }
                moduleSymbol.exports = moduleSymbol.exports.prependList(exports.toList());
            } catch (final IOException e) { throw new IllegalStateException(e); }
    }
    
    public static long transformPackageLocalToProtected(final long flags) = noneMatch(flags, ACCESS_MARKS) ? flags | PROTECTED : flags;
    
    public static void transformPackageLocalToProtected(final JCTree tree, final JCTree owner, final JCTree.JCModifiers modifiers) {
        if (owner instanceof JCTree.JCClassDecl classDecl && noneMatch(classDecl.mods.flags, INTERFACE))
            if (tree instanceof JCTree.JCVariableDecl || tree instanceof JCTree.JCMethodDecl methodDecl && (methodDecl.name != methodDecl.name.table.names.init || noneMatch(classDecl.mods.flags, ENUM)))
                modifiers.flags = transformPackageLocalToProtected(modifiers.flags);
    }
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static long adjustMethodFlags(final long capture, final ClassReader $this, final long flags) = transformPackageLocalToProtected(capture);
    
    @Hook(at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static long adjustFieldFlags(final long capture, final ClassReader $this, final long flags) = transformPackageLocalToProtected(capture);
    
}
