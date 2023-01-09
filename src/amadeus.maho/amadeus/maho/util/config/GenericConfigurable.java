package amadeus.maho.util.config;

import amadeus.maho.lang.Getter;
import amadeus.maho.lang.Setter;

public interface GenericConfigurable<C> extends LoadableConfigurable<C> {
    
    interface Named<C> extends GenericConfigurable<C> {
        
        String configName();
        
        default void loadConfig(final Config config) = config.load(config(), configName());
        
        default void saveConfig(final Config config) = config.save(config(), configName());
        
    }
    
    @Getter
    @Setter
    C config();
    
    default void loadConfig(final Config config) = config.load(config());
    
    default void saveConfig(final Config config) = config.save(config());
    
}
