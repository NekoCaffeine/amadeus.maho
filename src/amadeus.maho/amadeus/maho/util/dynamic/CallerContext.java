package amadeus.maho.util.dynamic;

import java.lang.reflect.Executable;

import amadeus.maho.lang.Getter;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.annotation.mark.IndirectCaller;

import static amadeus.maho.util.bytecode.ASMHelper.*;

public interface CallerContext {
    
    abstract class Stack {
        
        @Getter
        private static final StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        
    }
    
    static StackWalker.StackFrame selfFrame() = callerFrame(1);
    
    static StackWalker.StackFrame callerFrame() = callerFrame(2);
    
    static StackWalker.StackFrame callerFrame(final int depth) = Stack.walker().walk(frames -> frames.skip(1 + depth).dropWhile(CallerContext::invisibleFrame).findFirst()).orElseThrow(IllegalCallerException::new);
    
    static boolean invisibleFrame(final StackWalker.StackFrame frame) = frame.getDeclaringClass().isAnnotationPresent(IndirectCaller.class) || executable(frame)?.isAnnotationPresent(IndirectCaller.class) ?? false;
    
    static <T> Class<T> self() = (Class<T>) callerFrame(1).getDeclaringClass();
    
    static <T> Class<T> caller() = (Class<T>) callerFrame(2).getDeclaringClass();
    
    static <T> Class<T> caller(final int depth) = (Class<T>) callerFrame(1 + depth).getDeclaringClass();
    
    static @Nullable Executable selfExecutable() = executable(callerFrame(1));
    
    static @Nullable Executable callerExecutable() = executable(callerFrame(2));
    
    static @Nullable Executable callerExecutable(final int depth) = executable(callerFrame(1 + depth));
    
    @SneakyThrows
    static @Nullable Executable executable(final StackWalker.StackFrame frame)
            = frame.getMethodName().equals(_CLINIT_) ? null : frame.getMethodName().equals(_INIT_) ?
            frame.getDeclaringClass().getDeclaredConstructor(frame.getMethodType().parameterArray()) :
            frame.getDeclaringClass().getDeclaredMethod(frame.getMethodName(), frame.getMethodType().parameterArray());
    
}
