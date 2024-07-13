package amadeus.maho.lang.javac.incremental;

import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.ToString;

public sealed interface DependencyItem permits DependencyItem.Module, DependencyItem.Class, DependencyItem.Member {
    
    @ToString
    @EqualsAndHashCode
    record Module(String name) implements DependencyItem { }
    
    @ToString
    @EqualsAndHashCode
    record Class(String module, String name) implements DependencyItem { }
    
    sealed interface Member extends DependencyItem permits Field, Method {
        
        Class owner();
        
        String name();
        
        String signature();
        
    }
    
    @ToString
    @EqualsAndHashCode
    record Field(Class owner, String name, String signature) implements Member { }
    
    @ToString
    @EqualsAndHashCode
    record Method(Class owner, String name, String signature) implements Member { }
    
}
