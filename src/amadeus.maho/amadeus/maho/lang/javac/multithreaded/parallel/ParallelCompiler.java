package amadeus.maho.lang.javac.multithreaded.parallel;

import java.util.Queue;
import javax.tools.JavaFileObject;

import com.sun.source.util.TaskEvent;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.CompileStates;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Pair;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Privilege;
import amadeus.maho.lang.javac.multithreaded.dispatch.DispatchCompiler;

@FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
public class ParallelCompiler extends JavaCompiler {
    
    ParallelContext context;
    
    DispatchCompiler parent;
    
    public static ParallelCompiler instance(final ParallelContext context) = (ParallelCompiler) context.get(compilerKey) ?? new ParallelCompiler(context);
    
    public ParallelCompiler(final ParallelContext context) {
        super(context);
        parent = (this.context = context).get(DispatchCompiler.dispatchCompilerKey);
    }
    
    @Override
    protected boolean shouldStop(final CompileStates.CompileState cs) = false;
    
    @Override
    public Env<AttrContext> attribute(final Env<AttrContext> env) {
        if (compileStates.isDone(env, CompileStates.CompileState.ATTR))
            return env;
        if (!taskListener.isEmpty())
            taskListener.started((Privilege) ((JavaCompiler) this).newAnalyzeTaskEvent(env));
        final JavaFileObject prev = log.useSource(env.enclClass.sym.sourcefile != null ? env.enclClass.sym.sourcefile : env.toplevel.sourcefile);
        try {
            attr.attrib(env);
            compileStates.put(env, CompileStates.CompileState.ATTR);
        } finally { log.useSource(prev); }
        return env;
    }
    
    @Override
    public void flow(final Env<AttrContext> env, final Queue<Env<AttrContext>> results) = super.flow(env, results);
    
    @Override
    public void desugar(final Env<AttrContext> env, final Queue<Pair<Env<AttrContext>, JCTree.JCClassDecl>> results) = super.desugar(env, results);
    
    @Override
    public void generate(final Queue<Pair<Env<AttrContext>, JCTree.JCClassDecl>> queue, final Queue<JavaFileObject> results) = super.generate(queue, results);
    
    @Override
    public int errorCount() = log.nerrors;
    
    public void unblockAnnotationsNoFlush() = annotate.unblockAnnotationsNoFlush();
    
}
