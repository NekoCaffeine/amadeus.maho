package amadeus.maho.lang.javac.handler.base;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Supplier;

import org.objectweb.asm.Type;

import amadeus.maho.lang.Getter;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.transform.handler.base.marker.BaseMarker;
import amadeus.maho.transform.mark.base.TransformMark;
import amadeus.maho.util.bytecode.ASMHelper;

@TransformMark(Syntax.SyntaxMarker.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Syntax {
    
    @NoArgsConstructor
    class SyntaxMarker extends BaseMarker<Syntax> {
        
        @Getter
        private static final Map<Integer, Supplier<Class<? extends BaseSyntaxHandler>>> syntaxTypes = new ConcurrentSkipListMap<>();
        
        @Override
        public synchronized void onMark() = syntaxTypes()[annotation.priority()] = () -> (Class<? extends BaseSyntaxHandler>) ASMHelper.loadType(Type.getObjectType(sourceClass.name), true, contextClassLoader());
        
        @Override
        public boolean advance() = false;
        
    }
    
    int priority();
    
}
