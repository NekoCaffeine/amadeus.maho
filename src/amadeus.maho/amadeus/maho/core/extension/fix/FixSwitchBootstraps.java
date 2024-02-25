package amadeus.maho.core.extension.fix;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.runtime.SwitchBootstraps;

import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.dynamic.LookupHelper;

import static java.lang.invoke.MethodHandles.*;

@TransformProvider public
interface FixSwitchBootstraps {
    
    @FunctionalInterface
    interface Checker {
        
        int target();
        
        default boolean check(final @Nullable Object instance, final int value) = value == target();
        
    }
    
    MethodHandle check = LookupHelper.methodHandle3(Checker::check);
    
    @Hook(value = SwitchBootstraps.class, isStatic = true, at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.RETURN)), capture = true)
    private static MethodHandle createMethodHandleSwitch(final MethodHandle capture, final MethodHandles.Lookup lookup, final Object labels[])
            = guardWithTest(check.bindTo((Checker) () -> labels.length), dropArguments(constant(int.class, labels.length), 0, Object.class, int.class), capture);
    
}
