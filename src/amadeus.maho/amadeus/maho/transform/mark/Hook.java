package amadeus.maho.transform.mark;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import amadeus.maho.core.MahoBridge;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.transform.handler.HookTransformer;
import amadeus.maho.transform.mark.base.At;
import amadeus.maho.transform.mark.base.TransformMark;
import amadeus.maho.transform.mark.base.TransformMetadata;
import amadeus.maho.util.annotation.mark.DefaultClass;
import amadeus.maho.util.annotation.mark.DisallowLoading;
import amadeus.maho.util.annotation.mark.Freeze;
import amadeus.maho.util.annotation.mark.IgnoredDefaultValue;
import amadeus.maho.util.annotation.mark.paradigm.AOP;
import amadeus.maho.util.bytecode.Bytecodes;

@AOP
@Share(erase = @Erase(method = true))
@TransformMark(HookTransformer.class)
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Hook {
    
    @Share(erase = @Erase, required = "amadeus.maho.transform.mark.Hook")
    class Result {
        
        private static final Object VOID_MARK;
        
        static {
            if (Result.class.getClassLoader() != null) {
                @Nullable Class<?> bootTarget = null;
                try {
                    bootTarget = Class.forName(Result.class.getName(), false, null);
                } catch (final ClassNotFoundException _) { }
                if (bootTarget == null)
                    VOID_MARK = { };
                else
                    try {
                        final Field field = bootTarget.getDeclaredField("VOID_MARK");
                        field.setAccessible(true);
                        VOID_MARK = field.get(null);
                    } catch (final Throwable throwable) {
                        throwable.printStackTrace();
                        throw new RuntimeException(throwable);
                    }
            } else if (MahoBridge.bridgeClassLoader() == null)
                VOID_MARK = { };
            else
                try {
                    final Class<?> bridgeTarget = Class.forName(Result.class.getName(), true, MahoBridge.bridgeClassLoader()); // fallback
                    if (Result.class != bridgeTarget) {
                        final Field field = bridgeTarget.getDeclaredField("VOID_MARK");
                        field.setAccessible(true);
                        VOID_MARK = field.get(null);
                    } else
                        VOID_MARK = { };
                } catch (final Throwable throwable) {
                    throwable.printStackTrace();
                    throw new RuntimeException(throwable);
                }
        }
        
        // VOID => continue
        // The rest are all returning the corresponding results.
        // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        // If you need to operate the stack, please use Result#new
        // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        // See Result#new
        @Freeze
        public static final Result
                VOID  = { },
                NULL  = { null },
                ZERO  = { 0 },
                TRUE  = { Boolean.TRUE },
                FALSE = { Boolean.FALSE };
        
        public final @Nullable Object result;
        
        public int jumpIndex = -1;
        
        public Map<Integer, Object> stackContext;
        
        protected transient int index = -1;
        
        public self jump(final int index = 0) = jumpIndex = index;
        
        public Map<Integer, Object> stackContext() = stackContext == null ? stackContext = new HashMap<>() : stackContext;
        
        public <T> self operationStack(final int offset = 1, final T obj) = stackContext().put(index += offset, obj);
        
        @Override
        public int hashCode() = result == null ? 0 : result.hashCode();
        
        @Override
        public boolean equals(final Object obj) = obj != null && obj.getClass() == getClass() && Objects.equals(result, ((Result) obj).result);
        
        @Override
        public Result clone() {
            final Result result = { this.result };
            result.stackContext = stackContext;
            result.jumpIndex = jumpIndex;
            return result;
        }
        
        public Result() = this(VOID_MARK);
        
        public <T> Result(final @Nullable T result) = this.result = result;
        
        public static Result trueToVoid(final boolean flag) = flag ? VOID : FALSE;
        
        public static Result falseToVoid(final boolean flag) = flag ? TRUE : VOID;
        
        public static Result trueToVoidReverse(final boolean flag) = flag ? VOID : TRUE;
        
        public static Result falseToVoidReverse(final boolean flag) = flag ? FALSE : VOID;
        
        public static Result trueToVoid(final boolean flag, final @Nullable Object result) = flag ? VOID : new Hook.Result(result);
        
        public static Result falseToVoid(final boolean flag, final @Nullable Object result) = !flag ? VOID : new Hook.Result(result);
        
        public static Result nullToVoid(final @Nullable Object flag) = nullToVoid(flag, flag);
        
        public static Result nullToVoid(final @Nullable Object flag, final @Nullable Object result) = flag == null ? VOID : new Result(result);
        
        public static Result nonnullToVoid(final @Nullable Object flag) = nonnullToVoid(flag, NULL);
        
        public static Result nonnullToVoid(final @Nullable Object flag, final @Nullable Object result) = flag != null ? VOID : new Result(result);
        
        public static Result nullOrVoid(final @Nullable Object flag) = flag == null ? NULL : VOID;
        
    }
    
    @Share(required = "amadeus.maho.transform.mark.Hook")
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME) @interface Reference { }
    
    @Share(required = "amadeus.maho.transform.mark.Hook")
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME) @interface LocalVar {
        
        int index();
        
    }
    
    boolean isStatic() default false;
    
    boolean direct() default false;
    
    boolean capture() default false;
    
    boolean before() default true;
    
    boolean branchReversal() default false;
    
    boolean broadCast() default true;
    
    boolean forceReturn() default false;
    
    boolean exactMatch() default true;
    
    boolean avoidRecursion() default false;
    
    int store() default -1;
    
    At at() default @At;
    
    At[] jump() default { };
    
    At[] lambdaRedirect() default { };
    
    @Remap.Class
    @IgnoredDefaultValue("target")
    String target() default "";
    
    @DisallowLoading
    @Remap.Class
    @IgnoredDefaultValue("target")
    Class<?> value() default DefaultClass.class;
    
    @Remap.Method
    String selector() default "";
    
    @IgnoredDefaultValue
    TransformMetadata metadata() default @TransformMetadata;
    
}
