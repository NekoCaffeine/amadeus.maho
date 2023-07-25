package amadeus.maho.util.runtime;

import java.lang.constant.ClassDesc;
import java.lang.constant.Constable;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.invoke.MethodHandles;
import java.util.Optional;
import java.util.stream.Stream;

import amadeus.maho.lang.Extension;
import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.util.dynamic.CallerContext;

import static java.lang.constant.ConstantDescs.*;

@Extension
@SuppressWarnings("OptionalGetWithoutIsPresent")
public interface ConstantHelper {
    
    ClassDesc CD_ConstantHelper = ConstantHelper.class.describeConstable().get();
    
    DirectMethodHandleDesc BSM_RESOLVE_ARRAY = ofConstantBootstrap(CD_ConstantHelper, "resolveArray", CD_Object.arrayType(), CD_ConstantDesc.arrayType());
    
    static <T> DynamicConstantDesc<T> invoke(final MethodHandleDesc handle, final ConstantDesc... args)
            = DynamicConstantDesc.ofNamed(BSM_INVOKE, "_", handle.invocationType().returnType(), ArrayHelper.insert(args, handle));
    
    @SneakyThrows
    static Object[] resolveArray(final MethodHandles.Lookup lookup, final String name, final Class<?> type, final ConstantDesc... components)
            = Stream.of(components).map(desc -> desc.resolveConstantDesc(lookup)).toArray(TypeHelper.arrayConstructor(type.componentType()));
    
    static <T> DynamicConstantDesc<T> array(final ClassDesc arrayType, final ConstantDesc... components)
            = DynamicConstantDesc.ofNamed(BSM_RESOLVE_ARRAY, "_", arrayType.isArray() ? arrayType : arrayType.arrayType(), components);
    
    static <D extends ConstantDesc> D describe(final Constable constable) = (D) constable.describeConstable().get();
    
    static ConstantDesc[] describes(final Constable... constables) = Stream.of(constables).map(Constable::describeConstable).map(Optional::get).toArray(ConstantDesc[]::new);
    
    @SneakyThrows
    static <T> T resolveConstantDesc(final ConstantDesc desc, final Class<?> caller = CallerContext.caller()) = (T) desc.resolveConstantDesc(MethodHandleHelper.lookup().in(caller));
    
}
