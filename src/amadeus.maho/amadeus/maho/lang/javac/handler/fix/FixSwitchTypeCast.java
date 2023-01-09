package amadeus.maho.lang.javac.handler.fix;

import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.jvm.Code;

import amadeus.maho.lang.inspection.Fixed;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;

@TransformProvider
public interface FixSwitchTypeCast {
    
    /*
        see: https://github.com/SAP/SapMachine/issues/1213
        
        public class SwitchBug {
    
            interface A { }
            
            interface B { }
            
            public static void main(final String... args) {
                f((B) switch (new Object()) {
                    case Object obj -> new A() {};
                });
            }
            
            static void f(final B b) { }
            
        }
     */
    @Fixed(domain = "openjdk", shortName = "JDK-8291657", url = "https://bugs.openjdk.org/browse/JDK-8291657")
    @Hook(at = @At(method = @At.MethodInsn(name = "check")), capture = true)
    private static boolean forceStackTop(final boolean capture, final Code.State $this, final Type t) = true;
    
}
