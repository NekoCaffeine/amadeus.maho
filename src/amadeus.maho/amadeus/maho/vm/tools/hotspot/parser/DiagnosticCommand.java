package amadeus.maho.vm.tools.hotspot.parser;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.transform.mark.Patch;
import amadeus.maho.transform.mark.Share;
import amadeus.maho.vm.tools.hotspot.WhiteBox;

@Share(target = WhiteBox.Names.DiagnosticCommand, required = WhiteBox.Names.DiagnosticArgumentType)
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PUBLIC)
public class DiagnosticCommand {
    
    @Patch(target = WhiteBox.Names.DiagnosticCommand)
    private static class Patcher extends DiagnosticCommand {
        
        @Patch.Exception
        public Patcher(final String name, final String desc, final DiagnosticArgumentType type, final boolean mandatory, final String defaultValue, final boolean argument) = super(name, desc, type, mandatory, defaultValue, argument);
        
        public String getName() = name;
        
        public String getDesc() = desc;
        
        public DiagnosticArgumentType getType() = type;
        
        public boolean isMandatory() = mandatory;
        
        public boolean isArgument() = argument;
        
        public String getDefaultValue() = defaultValue;
        
    }
    
    @Share(target = WhiteBox.Names.DiagnosticArgumentType)
    public enum DiagnosticArgumentType {
        JLONG, BOOLEAN, STRING, NANOTIME, STRINGARRAY, MEMORYSIZE
    }
    
    String                 name;
    String                 desc;
    DiagnosticArgumentType type;
    boolean                mandatory;
    String                 defaultValue;
    boolean                argument;
    
}
