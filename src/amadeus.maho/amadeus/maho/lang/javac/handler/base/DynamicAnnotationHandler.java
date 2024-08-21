package amadeus.maho.lang.javac.handler.base;

import java.lang.annotation.Annotation;
import java.util.Collection;

import com.sun.tools.javac.code.Symbol;

public interface DynamicAnnotationHandler {
    
    Class<? extends Annotation> providerType();
    
    Class<? extends Annotation> annotationType();
    
    void addSymbol(Symbol.ClassSymbol symbol);
    
    Collection<Symbol.ClassSymbol> allSymbols(Symbol.ModuleSymbol module);
    
}
