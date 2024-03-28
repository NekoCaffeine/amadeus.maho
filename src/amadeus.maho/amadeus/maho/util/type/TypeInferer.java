package amadeus.maho.util.type;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.dynamic.ClassLocal;
import amadeus.maho.util.dynamic.LambdaHelper;
import amadeus.maho.util.runtime.MethodHandleHelper;

public interface TypeInferer {
    
    ClassLocal.Recursive<Map<TypeVariable<?>, Type>> typeVariableMapLocal = { clazz -> computeType(clazz, true) };
    
    @SneakyThrows
    private static Map<TypeVariable<?>, Type> computeType(final Type context, final boolean root = true, final Map<TypeVariable<?>, Type> typeVariableMap = new HashMap<>()) {
        if ((Type) switch (context) {
            case ParameterizedType parameterizedType when parameterizedType.getRawType() instanceof Class<?> clazz -> {
                computeParameterizedType(parameterizedType, typeVariableMap);
                yield clazz;
            }
            case Class<?> clazz when clazz != Object.class                                                         -> clazz;
            default                                                                                                -> null;
        } instanceof Class<?> raw) {
            if (root) {
                final @Nullable Type superclass = raw.getGenericSuperclass(); // interface => null
                if (superclass != null)
                    computeType(superclass, false, typeVariableMap);
                for (final Type genericInterface : raw.getGenericInterfaces())
                    computeType(genericInterface, false, typeVariableMap);
                if (LambdaHelper.isLambdaClass(raw))
                    computeLambdaType(raw, typeVariableMap);
            } else
                typeVariableMap.putAll(typeVariableMapLocal[raw]);
        }
        return typeVariableMap;
    }
    
    private static void computeParameterizedType(final ParameterizedType parameterizedType, final Map<TypeVariable<?>, Type> typeVariableMap) {
        final TypeVariable<?> formalParameters[] = ((Class<?>) parameterizedType.getRawType()).getTypeParameters();
        if (formalParameters.length > 0) {
            final Type actualTypes[] = parameterizedType.getActualTypeArguments();
            for (int i = 0; i < formalParameters.length; i++)
                typeVariableMap[formalParameters[i]] = actualTypes[i];
        }
    }
    
    private static void computeLambdaType(final Class<?> lambdaType, final Map<TypeVariable<?>, Type> typeVariableMap) {
        final Method functionalMethod = LambdaHelper.lookupFunctionalMethodWithInterface(LambdaHelper.lookupFunctionalInterface(lambdaType));
        final @Nullable Executable executable = executableReference(lambdaType); // `generated lambda method` | `method reference`
        if (executable != null) {
            final Type returnTypeVar = functionalMethod.getGenericReturnType(), parameterTypeVars[] = functionalMethod.getGenericParameterTypes();
            if (returnTypeVar instanceof TypeVariable<?> variable)
                typeVariableMap[variable] = switch (executable) {
                    case Method method              -> method.getGenericReturnType();
                    case Constructor<?> constructor -> constructor.getDeclaringClass();
                };
            final Type arguments[] = executable.getGenericParameterTypes();
            int parameterOffset = 0;
            if (parameterTypeVars.length > 0 && parameterTypeVars[0] instanceof TypeVariable variable && parameterTypeVars.length == arguments.length + 1) { // instance method reference
                typeVariableMap[variable] = executable.getDeclaringClass(); // this reference
                parameterOffset = 1;
            }
            final int argumentOffset = arguments.length - parameterTypeVars.length; // closures
            for (int i = parameterOffset; i < parameterTypeVars.length; i++)
                if (parameterTypeVars[i] instanceof TypeVariable<?> variable)
                    typeVariableMap[variable] = arguments[i + argumentOffset];
        }
    }
    
    @SneakyThrows
    static Map<TypeVariable<?>, Type> typeVariableMap(final Type type) = switch (type) {
        case InferredParameterizedType.Cached cached -> cached.typeVariableMap();
        case Class<?> clazz                          -> typeVariableMapLocal[clazz];
        default                                      -> computeType(type, false);
    };
    
    @SneakyThrows
    private static @Nullable Executable executableReference(final Class<?> lambdaType) {
        try {
            final MethodHandles.Lookup lookup = MethodHandleHelper.lookup();
            final MethodHandle protectedImplMethod = (MethodHandle) lookup.findStaticGetter(lambdaType, "protectedImplMethod", MethodHandle.class).invokeExact();
            return lookup.revealDirect(protectedImplMethod).reflectAs(Method.class, lookup);
        } catch (final NoSuchFieldException ignored) { }
        return lambdaType.constantPool().lastExecutableWithoutBoxed();
    }
    
    static RuntimeParameterizedType parameterization(final Class<?> classType) = { classType.getTypeParameters(), classType, classType.getDeclaringClass() };
    
    static <T> InferredGenericType<? extends T> infer(final TypeToken<T> token, final Type context) = (InferredGenericType<? extends T>) infer(token.genericType(), context);
    
    static InferredGenericType<?> infer(final Type genericType, final Type context) = infer(genericType, typeVariableMap(context));
    
    static InferredGenericType<?> infer(final Type genericType, final Map<TypeVariable<?>, Type> typeVariableMap, final Map<Type, Type> context = new HashMap<>()) = switch (genericType) {
        case Class<?> clazz                          -> new InferredClassType(clazz);
        case InferredGenericType inferredGenericType -> inferredGenericType;
        default                                      -> {
            Type type = genericType;
            while (context.containsKey(type))
                type = context[type];
            yield switch (type) {
                case TypeVariable<?> typeVariable        -> {
                    final @Nullable Type result = typeVariableMap[typeVariable];
                    if (result != null && result != typeVariable) {
                        context[typeVariable] = result;
                        yield infer(result, typeVariableMap, context);
                    } else {
                        final InferredTypeVariable<?> inferredTypeVariable = { typeVariable, infer(typeVariable.getBounds(), typeVariableMap, context) };
                        context[typeVariable] = inferredTypeVariable;
                        yield inferredTypeVariable;
                    }
                }
                case ParameterizedType parameterizedType -> {
                    final InferredParameterizedType inferredParameterizedType = { parameterizedType, infer(parameterizedType.getActualTypeArguments(), typeVariableMap, context) };
                    final InferredParameterizedType.Cached cached = inferredParameterizedType.cache();
                    context[parameterizedType] = cached;
                    yield cached;
                }
                case GenericArrayType genericArrayType   -> {
                    final InferredGenericArrayType inferredGenericArrayType = { genericArrayType, infer(genericArrayType.getGenericComponentType(), typeVariableMap, context) };
                    context[genericArrayType] = inferredGenericArrayType;
                    yield inferredGenericArrayType;
                }
                case WildcardType wildcardType           -> {
                    final InferredWildcardType inferredWildcardType = { wildcardType, infer(wildcardType.getUpperBounds(), typeVariableMap, context), infer(wildcardType.getLowerBounds(), typeVariableMap, context) };
                    context[wildcardType] = inferredWildcardType;
                    yield inferredWildcardType;
                }
                default                                  -> throw new IllegalStateException(STR."Unexpected value: \{type}");
            };
        }
    };
    
    static InferredGenericType[] infer(final Type genericTypes[], final Map<TypeVariable<?>, Type> typeVariableMap, final Map<Type, Type> context = new HashMap<>())
            = Stream.of(genericTypes).map(genericType -> infer(genericType, typeVariableMap, context)).toArray(InferredGenericType[]::new);
    
}
