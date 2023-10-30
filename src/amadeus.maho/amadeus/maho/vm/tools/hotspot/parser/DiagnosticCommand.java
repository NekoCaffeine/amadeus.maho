package amadeus.maho.vm.tools.hotspot.parser;

import amadeus.maho.lang.AccessLevel;
import amadeus.maho.lang.AllArgsConstructor;
import amadeus.maho.lang.FieldDefaults;
import amadeus.maho.lang.NoArgsConstructor;
import amadeus.maho.transform.mark.Patch;
import amadeus.maho.transform.mark.Remap;
import amadeus.maho.transform.mark.Share;
import amadeus.maho.vm.tools.hotspot.WhiteBox;

@Share(target = WhiteBox.Names.DiagnosticCommand, required = WhiteBox.Names.DiagnosticArgumentType)
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PUBLIC)
public class DiagnosticCommand {
    
    @NoArgsConstructor(on = @Patch.Exception)
    @Patch(target = WhiteBox.Names.DiagnosticCommand)
    private static class Patcher extends DiagnosticCommand {
        
        public String getName() = name;
        
        public String getDesc() = desc;
        
        public DiagnosticArgumentType getType() = type;
        
        public boolean isMandatory() = mandatory;
        
        public boolean isArgument() = argument;
        
        public String getDefaultValue() = defaultValue;
        
    }
    
    @Share(target = WhiteBox.Names.DiagnosticArgumentType, remap = @Remap(mapping = {
            WhiteBox.Names.DiagnosticCommand_Shadow,
            WhiteBox.Names.DiagnosticCommand
    }))
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
