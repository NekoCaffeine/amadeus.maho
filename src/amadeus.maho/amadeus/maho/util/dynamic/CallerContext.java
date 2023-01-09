package amadeus.maho.util.dynamic;

import java.lang.reflect.Executable;

import amadeus.maho.lang.Getter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.util.annotation.mark.IndirectCaller;
import amadeus.maho.vm.transform.mark.HotSpotJIT;

import static amadeus.maho.util.bytecode.ASMHelper.*;

@HotSpotJIT
public interface CallerContext {
    
    abstract class Stack {
        
        @Getter
        private static final StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        
    }
    
    static StackWalker.StackFrame selfFrame() = callerFrame(1);
    
    static StackWalker.StackFrame callerFrame() = callerFrame(2);
    
    static StackWalker.StackFrame callerFrame(final int depth) = Stack.walker().walk(frames -> frames.skip(1 + depth).dropWhile(CallerContext::invisibleFrame).findFirst()).orElseThrow(IllegalCallerException::new);
    
    static boolean invisibleFrame(final StackWalker.StackFrame frame) = frame.getDeclaringClass().isAnnotationPresent(IndirectCaller.class) || executable(frame)?.isAnnotationPresent(IndirectCaller.class) ?? false;
    
    static Class<?> self() = callerFrame(1).getDeclaringClass();
    
    static Class<?> caller() = callerFrame(2).getDeclaringClass();
    
    static Class<?> caller(final int depth) = callerFrame(1 + depth).getDeclaringClass();
    
    static Executable selfExecutable() = executable(callerFrame(1));
    
    static Executable callerExecutable() = executable(callerFrame(2));
    
    static Executable callerExecutable(final int depth) = executable(callerFrame(1 + depth));
    
    @SneakyThrows
    static Executable executable(final StackWalker.StackFrame frame)
            = frame.getMethodName().equals(_CLINIT_) ? null : frame.getMethodName().equals(_INIT_) ?
            frame.getDeclaringClass().getConstructor(frame.getMethodType().parameterArray()) :
            frame.getDeclaringClass().getDeclaredMethod(frame.getMethodName(), frame.getMethodType().parameterArray());
    
}
