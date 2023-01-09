package amadeus.maho.lang.javac.handler.base;

import java.lang.annotation.Annotation;
import java.util.Set;

import com.sun.tools.javac.code.Symbol;

public interface DynamicAnnotationHandler {
    
    void initModules(Set<Symbol.ModuleSymbol> modules);
    
    Class<? extends Annotation> providerType();
    
    Class<? extends Annotation> annotationType();
    
    void addSymbol(Symbol.ClassSymbol symbol);
    
}
