package amadeus.maho.core.extension;

import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.Set;

import jdk.internal.loader.BootLoader;
import jdk.internal.module.ModuleBootstrap;
import jdk.internal.module.Modules;

import amadeus.maho.lang.SneakyThrows;
import amadeus.maho.lang.inspection.Nullable;
import amadeus.maho.util.annotation.mark.IndirectCaller;
import amadeus.maho.util.bytecode.ASMHelper;
import amadeus.maho.util.dynamic.CallerContext;
import amadeus.maho.util.resource.ResourcePath;
import amadeus.maho.util.runtime.MethodHandleHelper;

@SneakyThrows
public interface ModuleAdder {
    
    Class<?> holder = Class.forName("jdk.internal.module.ModuleLoaderMap$Modules", true, null);
    
    Set<String>
            bootModules     = (Set<String>) MethodHandleHelper.lookup().findStaticVarHandle(holder, "bootModules", Set.class).get(),
            platformModules = (Set<String>) MethodHandleHelper.lookup().findStaticVarHandle(holder, "platformModules", Set.class).get();
    
    static Module addSystemModule(final String name) = Modules.loadModule(name);
    
    static @Nullable Module addModule(final ModuleFinder finder = ModuleBootstrap.unlimitedFinder(), final String name, final @Nullable ClassLoader loader = null)
            = finder.find(name).flatMap(reference -> reference.location().map(uri -> Modules.defineModule(null, reference.let(BootLoader::loadModule).descriptor(), uri))).orElse(null);
    
    @IndirectCaller
    static void injectMissingSystemModules(final Class<?> caller = CallerContext.caller()) = ResourcePath.of(caller).traverse(Path.of("module-info.class")).forEach(ModuleAdder::injectMissingSystemModules);
    
    static void injectMissingSystemModules(final Path moduleInfo) = ASMHelper.newClassNode(ASMHelper.newClassReader(moduleInfo)).module?.requires?.stream()
            .map(require -> require.module)
            .filter(name -> bootModules.contains(name) || platformModules.contains(name))
            .forEach(ModuleAdder::addSystemModule);
    
}
