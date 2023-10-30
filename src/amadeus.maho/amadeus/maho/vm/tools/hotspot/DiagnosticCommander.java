package amadeus.maho.vm.tools.hotspot;

import com.sun.management.internal.DiagnosticCommandImpl;

import amadeus.maho.lang.Privilege;

@SuppressWarnings("SpellCheckingInspection")
public interface DiagnosticCommander {
    
    DiagnosticCommandImpl impl = (DiagnosticCommandImpl) (Privilege) DiagnosticCommandImpl.getDiagnosticCommandMBean();
    
    static String execute(final String command) = (Privilege) impl.executeDiagnosticCommand(command);
    
    interface Compiler {
        
        static String CodeHeap_Analytics() = execute("Compiler.CodeHeap_Analytics");
        
        static String codecache() = execute("Compiler.codecache");
        
        static String codelist() = execute("Compiler.codelist");
        
        static String queue() = execute("Compiler.queue");
        
    }
    
    interface GC {
        
        static String class_histogram() = execute("GC.class_histogram");
        
        static String heap_info() = execute("GC.heap_info");
        
    }
    
    interface Thread {
        
        static String print() = execute("Thread.print");
        
    }
    
    interface VM {
        
        interface Log {
            
            static String output() = execute("VM.log output");
            
            static String output(final String value) = execute("VM.log output=" + value);
            
            static String output_options() = execute("VM.log output_options");
            
            static String output_options(final String value) = execute("VM.log output_options=" + value);
            
            static String what() = execute("VM.log what");
            
            static String what(final String value) = execute("VM.log what=" + value);
            
            static String decorators() = execute("VM.log decorators");
            
            static String decorators(final String value) = execute("VM.log decorators=" + value);
            
            static String disable() = execute("VM.log disable");
            
            static String list() = execute("VM.log list");
            
            static String rotate() = execute("VM.log rotate");
            
        }
        
        static String class_hierarchy() = execute("VM.class_hierarchy");
        
        static String classloader_stats() = execute("VM.classloader_stats");
        
        static String classloaders() = execute("VM.classloaders");
        
        static String command_line() = execute("VM.command_line");
        
        static String events() = execute("VM.events");
        
        static String flags() = execute("VM.flags");
        
        static String info() = execute("VM.info");
        
        static String metaspace() = execute("VM.metaspace");
        
        static String native_memory() = execute("VM.native_memory");
        
        static String print_touched_methods() = execute("VM.print_touched_methods");
        
        static String stringtable() = execute("VM.stringtable");
        
        static String symboltable() = execute("VM.symboltable");
        
        static String system_properties() = execute("VM.system_properties");
        
        static String systemdictionary() = execute("VM.systemdictionary");
        
        static String uptime() = execute("VM.uptime");
        
        static String version() = execute("VM.version");
        
        static String vitals() = execute("VM.vitals");
        
    }
    
}
