package amadeus.maho.core.extension;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Objects;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.AOTTransformer;
import amadeus.maho.transform.mark.Erase;
import amadeus.maho.transform.mark.Hook;
import amadeus.maho.transform.mark.Init;
import amadeus.maho.transform.mark.Share;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformMetadata;
import amadeus.maho.transform.mark.base.TransformProvider;

@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PUBLIC, makeFinal = true)
@Share(erase = @Erase, init = @Init(initialized = true), required = {
        "amadeus.maho.core.extension.DynamicLinkingContext$CallSiteContextThreadLocal",
        "amadeus.maho.core.extension.DynamicLinkingContext$LinkingType"
})
@TransformProvider
public class DynamicLinkingContext {
    
    @Share
    private static final class CallSiteContextThreadLocal extends ThreadLocal<LinkedList<DynamicLinkingContext>> {
        
        @Override
        protected LinkedList<DynamicLinkingContext> initialValue() = { };
        
    }
    
    @Share
    public enum LinkingType {
        
        LINK_CALL_SITE,
        LINK_DYNAMIC_CONSTANT,
        LINK_METHOD,
        LINK_METHOD_HANDLE_CONSTANT
        
    }
    
    private static final String MethodHandleNatives = "java.lang.invoke.MethodHandleNatives";
    
    // Unable to use ThreadLocal.withInitial(Stack::new) here
    // DynamicLinkingContext#<clinit> =>
    // ThreadLocal#withInitial =>
    // Stack::new =>
    // MethodHandleNatives#link? =>
    // DynamicLinkingContext#link? =>
    // contextStack.get() =>
    // NullPointerException( contextStack )
    private static final ThreadLocal<LinkedList<DynamicLinkingContext>> contextStack = new CallSiteContextThreadLocal();
    
    public static LinkedList<DynamicLinkingContext> contextStack() = contextStack.get();
    
    public static boolean shouldAvoidRecursion() {
        final LinkedList<DynamicLinkingContext> contextStack = contextStack();
        if (contextStack.size() < 2)
            return false;
        final DynamicLinkingContext top = contextStack.peek();
        for (int i = contextStack.size() - 2; i > -1; i--)
            if (top.equals(contextStack.get(i)))
                return true;
        return false;
    }
    
    LinkingType linkingType;
    
    Class<?> caller;
    String   name;
    Object   type;
    
    // LINK_CALL_SITE
    @Nullable MethodHandle bootstrapMethod;
    @Nullable Object       staticArguments;
    
    // LINK_METHOD, LINK_METHOD_HANDLE_CONSTANT
    int refKind;
    @Nullable Class<?> defc;
    
    public boolean isMethodType() = type instanceof MethodType;
    
    public boolean isFieldType() = type instanceof Class;
    
    @Override
    public int hashCode() = Objects.hash(linkingType, caller, name, type, bootstrapMethod, staticArguments, refKind, defc);
    
    @Override
    public boolean equals(final @Nullable Object obj)
            = obj != null && obj.getClass() == DynamicLinkingContext.class
            && linkingType == ((DynamicLinkingContext) obj).linkingType
            && caller == ((DynamicLinkingContext) obj).caller
            && name.equals(((DynamicLinkingContext) obj).name)
            && type == ((DynamicLinkingContext) obj).type
            && bootstrapMethod == ((DynamicLinkingContext) obj).bootstrapMethod
            && (staticArguments instanceof Object[] ?
            Arrays.deepEquals((Object[]) staticArguments, (Object[]) ((DynamicLinkingContext) obj).staticArguments) :
            Objects.equals(staticArguments, ((DynamicLinkingContext) obj).staticArguments))
            && refKind == ((DynamicLinkingContext) obj).refKind
            && defc == ((DynamicLinkingContext) obj).defc;
    
    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder(super.toString()).append('[')
                .append("linkingType").append(" = ").append(linkingType).append(", ")
                .append("caller").append(" = ").append(caller).append('/').append(caller.getClassLoader()).append(", ")
                .append("name").append(" = ").append(name).append(", ")
                .append("target").append(" = ").append(type).append(", ");
        switch (linkingType) {
            case LINK_CALL_SITE                           -> builder.append("bootstrapMethod").append(" = ").append(bootstrapMethod).append(", ")
                    .append("staticArguments").append(" = ").append(staticArguments instanceof Object[] ?
                            Arrays.toString((Object[]) staticArguments) : staticArguments);
            case LINK_METHOD, LINK_METHOD_HANDLE_CONSTANT -> builder.append("refKind").append(" = ").append(refKind).append(", ")
                    .append("defc").append(" = ").append(defc).append('/').append(defc != null ? defc.getClassLoader() : null);
        }
        return builder.append(']').toString();
    }
    
    
    public static DynamicLinkingContext withLinkCallSite(final Class<?> caller, final String name, final Object type, final MethodHandle bootstrapMethod, final Object staticArguments)
            = { LinkingType.LINK_CALL_SITE, caller, name, type, bootstrapMethod, staticArguments, 0, null };
    
    public static DynamicLinkingContext withLinkDynamicConstant(final Class<?> caller, final String name, final Object type, final MethodHandle bootstrapMethod, final Object staticArguments)
            = { LinkingType.LINK_DYNAMIC_CONSTANT, caller, name, type, bootstrapMethod, staticArguments, 0, null };
    
    public static DynamicLinkingContext withLinkMethod(final Class<?> caller, final String name, final Object type, final int refKind, final Class<?> defc)
            = { LinkingType.LINK_METHOD, caller, name, type, null, null, refKind, defc };
    
    public static DynamicLinkingContext withLinkMethodHandleConstant(final Class<?> caller, final String name, final Object type, final int refKind, final Class<?> defc)
            = { LinkingType.LINK_METHOD_HANDLE_CONSTANT, caller, name, type, null, null, refKind, defc };
    
    // runtime version > 17
    @Hook(target = MethodHandleNatives, isStatic = true, direct = true, metadata = @TransformMetadata(aotLevel = AOTTransformer.Level.RUNTIME))
    public static void linkCallSite_$Enter(final Object callerObj, final Object bootstrapMethodObj, final Object nameObj, final Object typeObj, final Object staticArguments, final Object appendixResult[])
    = contextStack().push(withLinkCallSite((Class<?>) callerObj, nameObj.toString(), typeObj, (MethodHandle) bootstrapMethodObj, staticArguments));
    
    @Hook(target = MethodHandleNatives, isStatic = true, direct = true, metadata = @TransformMetadata(aotLevel = AOTTransformer.Level.RUNTIME), at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.FINALLY)))
    public static void linkCallSite_$Exit(final Object callerObj, final Object bootstrapMethodObj, final Object nameObj, final Object typeObj, final Object staticArguments, final Object appendixResult[])
            = contextStack().pop();
    
    @Hook(target = MethodHandleNatives, isStatic = true, direct = true, metadata = @TransformMetadata(aotLevel = AOTTransformer.Level.RUNTIME))
    public static void linkDynamicConstant_$Enter(final Object callerObj, final Object bootstrapMethodObj, final Object nameObj, final Object typeObj, final Object staticArguments)
            = contextStack().push(withLinkDynamicConstant((Class<?>) callerObj, nameObj.toString(), typeObj, (MethodHandle) bootstrapMethodObj, staticArguments));
    
    @Hook(target = MethodHandleNatives, isStatic = true, direct = true, metadata = @TransformMetadata(aotLevel = AOTTransformer.Level.RUNTIME), at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.FINALLY)))
    public static void linkDynamicConstant_$Exit(final Object callerObj, final Object bootstrapMethodObj, final Object nameObj, final Object typeObj, final Object staticArguments)
            = contextStack().pop();
    // end
    
    @Hook(target = MethodHandleNatives, isStatic = true, direct = true, metadata = @TransformMetadata(aotLevel = AOTTransformer.Level.RUNTIME))
    public static void linkMethod_$Enter(final Class<?> callerClass, final int refKind, final Class<?> defc, final String name, final Object type, final Object appendixResult[])
            = contextStack().push(withLinkMethod(callerClass, name, type, refKind, defc));
    
    @Hook(target = MethodHandleNatives, isStatic = true, direct = true, metadata = @TransformMetadata(aotLevel = AOTTransformer.Level.RUNTIME), at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.FINALLY)))
    public static void linkMethod_$Exit(final Class<?> callerClass, final int refKind, final Class<?> defc, final String name, final Object type, final Object appendixResult[])
            = contextStack().pop();
    
    @Hook(target = MethodHandleNatives, isStatic = true, direct = true, metadata = @TransformMetadata(aotLevel = AOTTransformer.Level.RUNTIME))
    public static void linkMethodHandleConstant_$Enter(final Class<?> callerClass, final int refKind, final Class<?> defc, final String name, final Object type)
            = contextStack().push(withLinkMethodHandleConstant(callerClass, name, type, refKind, defc));
    
    @Hook(target = MethodHandleNatives, isStatic = true, direct = true, metadata = @TransformMetadata(aotLevel = AOTTransformer.Level.RUNTIME), at = @At(endpoint = @At.Endpoint(At.Endpoint.Type.FINALLY)))
    public static void linkMethodHandleConstant_$Exit(final Class<?> callerClass, final int refKind, final Class<?> defc, final String name, final Object type)
            = contextStack().pop();
    
}
