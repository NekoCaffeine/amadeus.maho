package amadeus.maho.util.runtime;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.stream.Stream;

import jdk.internal.loader.BootLoader;

import amadeus.maho.lang.SneakyThrows;

@SneakyThrows
public interface ModuleHelper {
    
    // JVM_AddModuleExportsToAll
    MethodHandle implAddOpens = MethodHandleHelper.lookup().findVirtual(Module.class, "implAddOpens", MethodType.methodType(void.class, String.class));
    
    // JVM_AddReadsModule
    MethodHandle implAddReads = MethodHandleHelper.lookup().findVirtual(Module.class, "implAddReads", MethodType.methodType(void.class, Module.class));
    
    MethodHandle layers = MethodHandleHelper.lookup().findStatic(ModuleLayer.class, "layers", MethodType.methodType(Stream.class, ClassLoader.class));
    
    static void openAllPackages(final Module module) = module.getPackages().forEach(packageName -> implAddOpens.invoke(module, packageName));
    
    static void readAllBootModules(final Module module) = Stream.concat(Stream.of(BootLoader.getUnnamedModule()), ModuleLayer.boot().modules().stream()).forEach(target -> implAddReads.invoke(module, target));
    
    static void openAllBootModule() = ModuleLayer.boot().modules().forEach(ModuleHelper::openAllPackages);
    
    static Stream<ModuleLayer> layers(final ClassLoader loader) = (Stream<ModuleLayer>) layers.invoke(loader);
    
}
