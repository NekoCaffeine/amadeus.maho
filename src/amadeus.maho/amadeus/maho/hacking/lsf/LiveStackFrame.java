package amadeus.maho.hacking.lsf;

import java.lang.invoke.MethodType;
import java.lang.reflect.Executable;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;

import static amadeus.maho.util.bytecode.ASMHelper.*;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PUBLIC)
class LiveStackFrame {
    
    final Class<?> declaringClass;
    final String name;
    final MethodType methodType;
    final Object locals[], stack[];
    
    @SneakyThrows
    @Getter(lazy = true)
    private @Nullable Executable method = name.equals(_CLINIT_) ? null : name.equals(_INIT_) ? declaringClass.getConstructor(methodType.parameterArray()) : declaringClass.getDeclaredMethod(name, methodType.parameterArray());
    
}
