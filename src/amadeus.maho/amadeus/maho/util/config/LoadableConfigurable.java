package amadeus.maho.util.config;

import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.util.runtime.TypeHelper;
import amadeus.maho.util.type.TypeInferer;
import amadeus.maho.util.type.TypeToken;

public interface LoadableConfigurable<C> {
    
    @SneakyThrows
    default C newConfig() = (C) TypeHelper.noArgConstructorHandle(configType()).invoke();
    
    default Class<? extends C> configType() = TypeInferer.infer(TypeToken.<C>capture(), getClass()).erasedType();
    
    default void load(final Config config) = config.load(newConfig());
    
}
