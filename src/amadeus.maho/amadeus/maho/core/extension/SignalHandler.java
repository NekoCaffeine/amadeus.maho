package amadeus.maho.core.extension;

import jdk.internal.misc.Signal;

import amadeus.maho.lang.Getter;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.Init;
import amadeus.maho.transform.mark.base.TransformProvider;
import amadeus.maho.util.control.FunctionChain;

@Init(initialized = true)
@TransformProvider
public interface SignalHandler {
    
    @Getter
    FunctionChain<Integer, Boolean> signalHandlers = { };
    
    @Hook(value = Signal.class, isStatic = true)
    static Hook.Result dispatch(final int number) = Hook.Result.falseToVoid(signalHandlers().apply(number).orElse(Boolean.FALSE));
    
}
