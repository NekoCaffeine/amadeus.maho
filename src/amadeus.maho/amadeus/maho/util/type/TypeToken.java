package amadeus.maho.util.type;

import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.Getter;
import amadeus.maho.lang.RequiredArgsConstructor;
import amadeus.maho.transform.GhostContext;
import amadeus.maho.util.annotation.mark.Ghost;
import amadeus.maho.util.annotation.mark.IndirectCaller;
import amadeus.maho.util.dynamic.CallerContext;
import amadeus.maho.util.runtime.TypeHelper;

import sun.reflect.generics.factory.CoreReflectionFactory;
import sun.reflect.generics.parser.SignatureParser;
import sun.reflect.generics.scope.ClassScope;
import sun.reflect.generics.scope.MethodScope;
import sun.reflect.generics.visitor.Reifier;

@Getter
@RequiredArgsConstructor(AccessLevel.PROTECTED)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TypeToken<T> {
    
    Type genericType;
    
    Class<? super T> erasedType = (Class<? super T>) TypeHelper.erase(genericType);
    
    int hashCode = genericType.hashCode();
    
    @Override
    public boolean equals(final Object obj) = obj == this || obj instanceof TypeToken<?> token && token.genericType.equals(genericType);
    
    @Override
    public String toString() = genericType.getTypeName();
    
    public static <T> TypeToken<T> capture(final Type genericType = compileTimeGenericType()) = { genericType };
    
    @SuppressWarnings("unused")
    public static <T, P> TypeToken<T> locate(final Type genericType = compileTimeGenericType()) = { genericType };
    
    @Ghost
    private static Type compileTimeGenericType();
    
    @IndirectCaller
    public static Type runtimeType(final String signature) {
        final StackWalker.StackFrame frame = CallerContext.callerFrame();
        final GenericDeclaration declaration = CallerContext.executable(frame) ?? (GenericDeclaration) frame.getDeclaringClass();
        return Reifier.make(CoreReflectionFactory.make(declaration, switch (declaration) {
            case Method method  -> MethodScope.make(method);
            case Class<?> clazz -> ClassScope.make(clazz);
            default             -> throw new IllegalStateException(STR."Unexpected value: \{declaration}");
        })).let(SignatureParser.make().parseTypeSig(signature)::accept).getResult();
    }
    
    @IndirectCaller
    public static TypeVariable<?> runtimeTypeVariable(final String signature, final int index) {
        if (runtimeType(signature) instanceof ParameterizedType parameterizedType && parameterizedType.getRawType() instanceof Class<?> clazz)
            return clazz.getTypeParameters()[index];
        else
            throw new IncompatibleClassChangeError();
    }
    
}
