package amadeus.maho.util.shell;

import java.util.Map;

import jdk.jshell.execution.DirectExecutionControl;
import jdk.jshell.spi.ExecutionControlProvider;
import jdk.jshell.spi.ExecutionEnv;

import amadeus.maho.lang.EqualsAndHashCode;
import amadeus.maho.lang.ToString;

@ToString
@EqualsAndHashCode
public record DirectExecutionControlProvider(ClassLoaderDelegate delegate = { }) implements ExecutionControlProvider {
    
    @Override
    public String name() = "direct";
    
    @Override
    public DirectExecutionControl generate(final ExecutionEnv env, final Map<String, String> parameters) throws Throwable = { delegate() };
    
}
