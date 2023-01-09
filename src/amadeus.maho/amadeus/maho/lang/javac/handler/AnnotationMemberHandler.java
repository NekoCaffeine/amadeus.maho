package amadeus.maho.lang.javac.handler;

import java.lang.annotation.Annotation;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.Check;
import com.sun.tools.javac.util.JCDiagnostic;

import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public class AnnotationMemberHandler {
    
    @Hook
    private static Hook.Result isAnnotationType(final Symbol.ClassSymbol symbol) = Hook.Result.falseToVoid(symbol.getQualifiedName().toString().equals(Annotation.class.getCanonicalName()));
    
    @Hook
    private static Hook.Result validateAnnotationType(final Check $this, final JCDiagnostic.DiagnosticPosition pos, final Type type) = Hook.Result.falseToVoid(type.tsym.getQualifiedName().toString().equals(Annotation.class.getCanonicalName()));
    
}
